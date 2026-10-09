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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stage's DIRECTORY property list, at CREATE STAGE and at ALTER STAGE … SET. The engine read ENABLE alone, took
 * any other word and any value, and told nothing of AUTO_REFRESH or of the last refresh.
 *
 * <p>On the account the list is judged option by option — a value that is no bare TRUE or FALSE is refused as
 * written, an unknown option by its name, and REFRESH_ON_CREATE in an ALTER — an empty list or a value that is no
 * list is refused whole, and a list without ENABLE leaves the table as it is. DESCRIBE STAGE tells the last
 * refresh of a table that is on, before ENABLE and AUTO_REFRESH: the moment of the CREATE that turned it on, unless
 * REFRESH_ON_CREATE was written off, and of each REFRESH since; empty for a table an ALTER turned on.
 * INFORMATION_SCHEMA.STAGES tells NULL for a stage never given a list, and TRUE or FALSE once it was. Every cell is
 * live-verified.
 */
public class StageDirectoryPropertyTest extends BaseDatabaseTest {

    private static final String NEEDS_A_LIST = "Parameter for directory requires property list."
        + " Example: DIRECTORY=(ENABLE=TRUE AUTO_REFRESH=FALSE).";
    private static final String MOMENT = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} [+-]\\d{4}";

    /** The statement's refusal on one line, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalidValue(final String written, final String parameter) {
        return "SQL compilation error:|invalid value [" + written + "] for parameter '" + parameter + "'";
    }

    /** DESCRIBE STAGE's DIRECTORY rows as {@code property=value}, in the order given, a moment told as a mark. */
    private List<String> described(final String stage) {
        final ResultSet rs = engine.executeQuery("DESCRIBE STAGE " + stage);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            if (!"DIRECTORY".equals(cell(rs, row, "parent_property"))) {
                continue;
            }
            final String value = cell(rs, row, "property_value");
            assertEquals("LAST_REFRESHED_ON".equals(cell(rs, row, "property")) ? "Timestamp" : "Boolean",
                cell(rs, row, "property_type"));
            assertEquals("LAST_REFRESHED_ON".equals(cell(rs, row, "property")) ? "" : "false",
                cell(rs, row, "property_default"));
            out.add(cell(rs, row, "property") + "=" + (value.matches(MOMENT) ? "<moment>" : value));
        }
        return out;
    }

    private static List<String> rows(final String... rows) {
        final List<String> out = new ArrayList<>();
        for (final String row : rows) {
            out.add(row);
        }
        return out;
    }

    @Test
    public void aValueIsJudgedAsWritten() {
        engine.execute("CREATE STAGE dp");
        for (final String statement : new String[] {"CREATE OR REPLACE STAGE dp2 DIRECTORY = ", "ALTER STAGE dp SET DIRECTORY = "}) {
            assertEquals(invalidValue("MAYBE", "ENABLE"), outcome(statement + "(ENABLE = MAYBE)"), statement);
            assertEquals(invalidValue("MAYBE", "AUTO_REFRESH"), outcome(statement + "(AUTO_REFRESH = MAYBE)"), statement);
            assertEquals(invalidValue("MAYBE", "REFRESH_ON_CREATE"),
                outcome(statement + "(ENABLE = TRUE REFRESH_ON_CREATE = MAYBE)"), statement);
            assertEquals("SQL compilation error:|invalid parameter 'FOO'",
                outcome(statement + "(ENABLE = TRUE FOO = 1)"), statement);
            assertEquals(NEEDS_A_LIST, outcome(statement + "()"), statement);
        }
        assertEquals(invalidValue("'TRUE'", "ENABLE"), outcome("ALTER STAGE dp SET DIRECTORY = (ENABLE = 'TRUE')"));
        assertEquals(invalidValue("1", "ENABLE"), outcome("ALTER STAGE dp SET DIRECTORY = (ENABLE = 1)"));
        assertEquals(NEEDS_A_LIST, outcome("ALTER STAGE dp SET DIRECTORY = 'x'"));
        assertEquals(NEEDS_A_LIST, outcome("ALTER STAGE dp SET DIRECTORY = TRUE"));
        assertEquals(rows("ENABLE=false", "AUTO_REFRESH=false"), described("dp"), "a refused list changes nothing");
    }

    @Test
    public void refreshOnCreateBelongsToACreate() {
        engine.execute("CREATE STAGE dp");
        assertEquals("SQL compilation error:|invalid property 'true' for 'REFRESH_ON_CREATE'",
            outcome("ALTER STAGE dp SET DIRECTORY = (ENABLE = TRUE REFRESH_ON_CREATE = TRUE)"));
        assertEquals("SQL compilation error:|invalid property 'false' for 'REFRESH_ON_CREATE'",
            outcome("ALTER STAGE dp SET DIRECTORY = (REFRESH_ON_CREATE = FALSE)"));
        assertEquals("ok", outcome("CREATE OR REPLACE STAGE dp DIRECTORY = (ENABLE = TRUE REFRESH_ON_CREATE = FALSE)"));
        assertEquals(rows("LAST_REFRESHED_ON=", "ENABLE=true", "AUTO_REFRESH=false"), described("dp"));
    }

    @Test
    public void theLastRefreshIsToldForATableThatIsOn() {
        engine.execute("CREATE STAGE off_stage");
        assertEquals(rows("ENABLE=false", "AUTO_REFRESH=false"), described("off_stage"));
        engine.execute("CREATE STAGE made_on DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows("LAST_REFRESHED_ON=<moment>", "ENABLE=true", "AUTO_REFRESH=false"), described("made_on"));
        engine.execute("ALTER STAGE off_stage SET DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows("LAST_REFRESHED_ON=", "ENABLE=true", "AUTO_REFRESH=false"), described("off_stage"),
            "an ALTER turns the table on without a refresh");
        engine.execute("ALTER STAGE off_stage REFRESH");
        assertEquals(rows("LAST_REFRESHED_ON=<moment>", "ENABLE=true", "AUTO_REFRESH=false"), described("off_stage"));
        engine.execute("ALTER STAGE off_stage SET DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows("LAST_REFRESHED_ON=<moment>", "ENABLE=true", "AUTO_REFRESH=false"), described("off_stage"),
            "turning on a table that is on keeps its last refresh");
        engine.execute("ALTER STAGE off_stage SET DIRECTORY = (ENABLE = FALSE)");
        assertEquals(rows("ENABLE=false", "AUTO_REFRESH=false"), described("off_stage"));
        engine.execute("ALTER STAGE off_stage SET DIRECTORY = (ENABLE = TRUE)");
        assertEquals(rows("LAST_REFRESHED_ON=", "ENABLE=true", "AUTO_REFRESH=false"), described("off_stage"));
    }

    @Test
    public void autoRefreshIsKeptAsWritten() {
        engine.execute("CREATE STAGE ar DIRECTORY = (ENABLE = TRUE AUTO_REFRESH = TRUE)");
        assertEquals(rows("LAST_REFRESHED_ON=<moment>", "ENABLE=true", "AUTO_REFRESH=true"), described("ar"));
        engine.execute("ALTER STAGE ar SET DIRECTORY = (AUTO_REFRESH = FALSE)");
        assertEquals(rows("LAST_REFRESHED_ON=<moment>", "ENABLE=true", "AUTO_REFRESH=false"), described("ar"),
            "a list without ENABLE leaves the table on");
        engine.execute("ALTER STAGE ar SET DIRECTORY = (ENABLE = TRUE, AUTO_REFRESH = TRUE)");
        assertEquals(rows("LAST_REFRESHED_ON=<moment>", "ENABLE=true", "AUTO_REFRESH=true"), described("ar"));
        engine.execute("CREATE STAGE ar_alone DIRECTORY = (AUTO_REFRESH = FALSE)");
        assertEquals(rows("ENABLE=false", "AUTO_REFRESH=false"), described("ar_alone"));
    }

    @Test
    public void theFlagHasThreeStates() {
        engine.execute("CREATE STAGE fl_none");
        engine.execute("CREATE STAGE fl_true DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STAGE fl_false DIRECTORY = (ENABLE = FALSE)");
        engine.execute("CREATE STAGE fl_alt");
        engine.execute("ALTER STAGE fl_alt SET DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STAGE fl_alt2 DIRECTORY = (ENABLE = TRUE)");
        engine.execute("ALTER STAGE fl_alt2 SET DIRECTORY = (ENABLE = FALSE)");
        engine.execute("CREATE STAGE fl_alt3");
        engine.execute("ALTER STAGE fl_alt3 SET DIRECTORY = (ENABLE = FALSE)");
        engine.execute("CREATE STAGE fl_alt4");
        engine.execute("ALTER STAGE fl_alt4 SET DIRECTORY = (AUTO_REFRESH = FALSE)");
        final List<String> told = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT stage_name, directory_enabled FROM information_schema.stages"
                + " WHERE stage_schema = 'TEST_SCHEMA' ORDER BY 1").getRows()) {
            told.add(row.getValue(0) + "=" + row.getValue(1));
        }
        assertEquals(rows("FL_ALT=true", "FL_ALT2=false", "FL_ALT3=false", "FL_ALT4=false", "FL_FALSE=false",
            "FL_NONE=null", "FL_TRUE=true"), told);
        final ResultSet shown = engine.executeQuery("SHOW STAGES LIKE 'FL%' IN SCHEMA test_db.test_schema");
        final List<String> flags = new ArrayList<>();
        for (final String name : new String[] {"FL_ALT", "FL_ALT2", "FL_ALT3", "FL_ALT4", "FL_FALSE", "FL_NONE", "FL_TRUE"}) {
            flags.add(name + "=" + cell(shown, soleRowWhere(shown, "name", name), "directory_enabled"));
        }
        assertEquals(rows("FL_ALT=Y", "FL_ALT2=N", "FL_ALT3=N", "FL_ALT4=N", "FL_FALSE=N", "FL_NONE=N", "FL_TRUE=Y"),
            flags);
    }

    @Test
    public void theViewListsEverySchemaOfItsDatabase() {
        engine.execute("CREATE STAGE here_st");
        engine.execute("CREATE SCHEMA test_db.other_schema");
        engine.execute("CREATE STAGE test_db.other_schema.there_st DIRECTORY = (ENABLE = TRUE)");
        engine.execute("USE SCHEMA test_db.test_schema");
        final List<String> told = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT stage_schema, stage_name, directory_enabled"
                + " FROM information_schema.stages ORDER BY 1, 2").getRows()) {
            told.add(row.getValue(0) + "." + row.getValue(1) + "=" + row.getValue(2));
        }
        assertEquals(rows("OTHER_SCHEMA.THERE_ST=true", "TEST_SCHEMA.HERE_ST=null"), told);
    }
}
