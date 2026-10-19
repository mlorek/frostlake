/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Cortex Search Service statements answer where no service is there to act on, which every account
 * answers alike: each form ALTER takes is read, and refused for the missing service and not for its syntax;
 * what a SET may not set is refused before the service is looked for; UNSET takes PRIMARY KEY and ATTRIBUTES
 * alone; IF EXISTS forgives the missing service in a sentence of its own; a quoted name is spelled as written.
 * Every statement is live-verified.
 */
public class CortexSearchServiceStatementsTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String status(final String sql) {
        final ResultSet answered = engine.executeQuery(sql);
        assertEquals(1, answered.getRowCount(), sql);
        return String.valueOf(answered.getRows().get(0).getValue(0));
    }

    /** The first sentence of the refusal of a service that is not there. */
    private static String missing(final String spelled) {
        return "Cortex Search Service 'TEST_DB.TEST_SCHEMA." + spelled + "' does not exist or not authorized.";
    }

    @Test
    public void everyFormOfAlterIsReadAndRefusedForTheMissingService() {
        engine.execute("CREATE TAG css_tag");
        final String[] actions = {
            "SET TARGET_LAG = '2 days'", "SET WAREHOUSE = nosuch_wh", "SET COMMENT = 'c1'",
            "SET PRIMARY KEY = (id)", "SET FULL_INDEX_BUILD_INTERVAL_DAYS = 7", "SET REQUEST_LOGGING = TRUE",
            "SET AUTO_SUSPEND = 1800", "SET AUTO_SUSPEND = 60", "SET AUTO_SUSPEND = NULL",
            "SET TARGET_LAG = '3 days' COMMENT = 'c2'", "UNSET PRIMARY KEY", "UNSET ATTRIBUTES",
            "SET ATTRIBUTES (cat)", "SET TAG css_tag = 'v'", "UNSET TAG css_tag", "REFRESH",
            "SUSPEND", "SUSPEND SERVING", "SUSPEND INDEXING", "RESUME", "RESUME SERVING", "RESUME INDEXING"};
        for (final String action : actions) {
            final String statement = "ALTER CORTEX SEARCH SERVICE css1 " + action;
            assertTrue(refusal(statement).contains(missing("CSS1")), statement);
        }
        assertTrue(refusal("DESCRIBE CORTEX SEARCH SERVICE css1").contains(missing("CSS1")));
        assertTrue(refusal("DROP CORTEX SEARCH SERVICE css1").contains(missing("CSS1")));
    }

    @Test
    public void whatASetMayNotSetIsRefusedBeforeTheServiceIsLookedFor() {
        assertEquals("SQL compilation error:\ninvalid value 'soon' for property 'TARGET_LAG'",
            refusal("ALTER CORTEX SEARCH SERVICE css1 SET TARGET_LAG = 'soon'"));
        assertEquals("SQL compilation error:\ninvalid property 'EMBEDDING_MODEL' for 'CORTEX_SEARCH_SERVICE'",
            refusal("ALTER CORTEX SEARCH SERVICE css1 SET EMBEDDING_MODEL = 'snowflake-arctic-embed-m-v1.5'"));
    }

    @Test
    public void unsetTakesThePrimaryKeyAndTheAttributesAlone() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected 'COMMENT'.",
            refusal("ALTER CORTEX SEARCH SERVICE css1 UNSET COMMENT"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected 'TARGET_LAG'.",
            refusal("ALTER CORTEX SEARCH SERVICE css1 UNSET TARGET_LAG"));
    }

    @Test
    public void ifExistsForgivesTheMissingServiceInASentenceOfItsOwn() {
        assertEquals("Cortex Search Service NOSUCH does not exist.",
            status("ALTER CORTEX SEARCH SERVICE IF EXISTS nosuch REFRESH"));
        assertEquals("Drop statement executed successfully (CSS1 already dropped).",
            status("DROP CORTEX SEARCH SERVICE IF EXISTS css1"));
        assertTrue(refusal("ALTER CORTEX SEARCH SERVICE nosuch REFRESH").contains(missing("NOSUCH")));
    }

    @Test
    public void aQuotedNameIsSpelledAsItWasWritten() {
        assertTrue(refusal("DESCRIBE CORTEX SEARCH SERVICE \"css q\"").contains(missing("\"css q\"")));
        assertTrue(refusal("DESCRIBE CORTEX SEARCH SERVICE \"CSS Q\"").contains(missing("\"CSS Q\"")));
        assertTrue(refusal("ALTER CORTEX SEARCH SERVICE \"css q\" SET COMMENT = 'quoted'")
            .contains(missing("\"css q\"")));
        assertTrue(refusal("DROP CORTEX SEARCH SERVICE \"css q\"").contains(missing("\"css q\"")));
    }

    @Test
    public void theListingHasItsTwentyColumns() {
        final ResultSet listed = engine.executeQuery("SHOW CORTEX SEARCH SERVICES LIKE 'CSS1'");
        assertEquals(0, listed.getRowCount());
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < listed.getColumnCount(); i++) {
            names.add(listed.getColumns().get(i).getName());
        }
        assertEquals(Arrays.asList("created_on", "name", "database_name", "schema_name", "target_lag", "warehouse",
            "search_column", "attribute_columns", "columns", "definition", "comment", "embedding_model",
            "indexing_state", "serving_state", "source_data_num_rows", "primary_key_columns",
            "scoring_profile_count", "auto_suspend", "refresh_mode", "vector_indexes"), names);
    }
}
