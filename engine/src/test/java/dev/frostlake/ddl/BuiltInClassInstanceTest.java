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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.storage.Row;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * The classes every account holds in its SNOWFLAKE database — SNOWFLAKE.ML.FORECAST, SNOWFLAKE.CORE.BUDGET, … —
 * exist, so a routine or role listing scoped to one of their instances, and a DROP of one, resolve the INSTANCE as
 * any schema object's name and miss it, after a LIMIT 0 and a WITH PRIVILEGES are judged; another name in their
 * schemas misses as a class. The SNOWFLAKE database's schemas resolve as live reaches them — ACCOUNT_USAGE does, TAGS
 * and PUBLIC do not. A class written with a quoted or a qualified name takes no CASCADE or RESTRICT on a DROP. Every
 * cell is live-verified.
 */
public class BuiltInClassInstanceTest extends BaseDatabaseTest {

    private static final String NO_OBJECT = "SQL compilation error:|Object does not exist, or operation cannot be performed.";
    private static final String PAGE_SIZE = "page size \"0\" must be greater than 0 in limit clause";

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The status a DROP answers, read off a raw connection in live mode, where the harness reports a count. */
    private String statusOf(final String sql) {
        if (LiveSnowflake.enabled()) {
            try {
                final Connection connection = LiveSnowflake.shared();
                final Statement st = connection.createStatement();
                final ResultSet rs = st.executeQuery(sql);
                rs.next();
                final String value = rs.getString(1);
                rs.close();
                st.close();
                return value;
            } catch (final SQLException e) {
                throw new IllegalStateException(sql + " failed on live: " + e.getMessage(), e);
            }
        }
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** Asserts a refusal by its first sentence, which a hint may follow. */
    private void assertRefused(final String sentence, final String sql) {
        final String answer = answer(sql);
        assertTrue(answer.startsWith(sentence), sql + " answered " + answer);
    }

    private static String missing(final String kind, final String name) {
        return "SQL compilation error:|" + kind + " '" + name + "' does not exist or not authorized.";
    }

    private static String missingClass(final String name) {
        return "SQL compilation error: Object type or Class '" + name + "' does not exist or not authorized.";
    }

    private static String syntaxError(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aRoutineListingResolvesTheInstanceOfABuiltInClass() {
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "SHOW FUNCTIONS IN snowflake.ml.forecast y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.\"y\""), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST \"y\"");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"),
            "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST IDENTIFIER('y')");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"),
            "SHOW FUNCTIONS IN IDENTIFIER('SNOWFLAKE.ML.FORECAST') y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST TEST_SCHEMA.y");
        assertRefused(missing("Instance", "SNOWFLAKE.ML.Y"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.ML.y");
        assertRefused(missing("Schema", "TEST_DB.A"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST a.b");
        assertRefused(missing("Database", "A"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST a.b.c");
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST a.b.c.d"));
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "SHOW USER FUNCTIONS IN SNOWFLAKE.ML.ANOMALY_DETECTION y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "SHOW PROCEDURES IN SNOWFLAKE.CORE.BUDGET y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.\"y\""), "SHOW ROLES IN SNOWFLAKE.CORE.BUDGET \"y\"");
        assertRefused(missing("Schema", "TEST_DB.A"), "SHOW ROLES IN SNOWFLAKE.ML.FORECAST a.b");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"),
            "SHOW FUNCTIONS IN SNOWFLAKE.DATA_PRIVACY.CUSTOM_CLASSIFIER y");
    }

    @Test
    public void anotherNameInABuiltInClassSchemaMissesAsAClass() {
        assertEquals(missingClass("SNOWFLAKE.ML.NOSUCH"), answer("SHOW FUNCTIONS IN SNOWFLAKE.ML.NOSUCH y"));
        assertEquals(missingClass("SNOWFLAKE.ML.NOSUCH"), answer("SHOW FUNCTIONS IN SNOWFLAKE.ML.NOSUCH a.b"));
        assertEquals(missingClass("SNOWFLAKE.CORE.AVG"), answer("SHOW FUNCTIONS IN SNOWFLAKE.CORE.AVG y"));
        assertEquals(missingClass("SNOWFLAKE.ML.\"forecast\""), answer("SHOW FUNCTIONS IN \"SNOWFLAKE\".\"ML\".\"forecast\" y"));
        assertEquals(missingClass("FORECAST"), answer("SHOW FUNCTIONS IN FORECAST y"));
        assertRefused(missing("Schema", "TEST_DB.ML"), "SHOW FUNCTIONS IN ML.FORECAST y");
        assertEquals(missingClass("SNOWFLAKE.ML.NOSUCH"), answer("DROP SNOWFLAKE.ML.NOSUCH y"));
    }

    @Test
    public void theSnowflakeDatabaseResolvesTheSchemasLiveReaches() {
        assertRefused(missing("Instance", "SNOWFLAKE.ACCOUNT_USAGE.Y"),
            "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.ACCOUNT_USAGE.y");
        assertRefused(missing("Instance", "SNOWFLAKE.CORTEX.Y"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.CORTEX.y");
        assertRefused(missing("Instance", "SNOWFLAKE.ACCOUNT_USAGE.Y"),
            "SHOW PROCEDURES IN SNOWFLAKE.CORE.BUDGET snowflake.account_usage.y");
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.PUBLIC.y");
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "SHOW ROLES IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.PUBLIC.y");
        assertRefused(missing("Schema", "SNOWFLAKE.TAGS"), "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST SNOWFLAKE.TAGS.y");
        assertEquals(missingClass("SNOWFLAKE.CORTEX.NOSUCH"), answer("SHOW FUNCTIONS IN SNOWFLAKE.CORTEX.NOSUCH y"));
        assertEquals(missingClass("SNOWFLAKE.ACCOUNT_USAGE.NOSUCH"), answer("DROP SNOWFLAKE.ACCOUNT_USAGE.NOSUCH y"));
        assertEquals(missingClass("SNOWFLAKE.CORTEX.NOSUCH"), answer("SHOW SNOWFLAKE.CORTEX.NOSUCH"));
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "SHOW FUNCTIONS IN SNOWFLAKE.PUBLIC.NOSUCH y");
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "SHOW FUNCTIONS IN SNOWFLAKE..FORECAST y");
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "SHOW SNOWFLAKE.PUBLIC.NOSUCH");
    }

    @Test
    public void aDropInTheSnowflakeDatabaseResolvesItsSchemaFirst() {
        assertRefused(missing("Instance", "SNOWFLAKE.ACCOUNT_USAGE.Y"),
            "DROP SNOWFLAKE.ML.FORECAST SNOWFLAKE.ACCOUNT_USAGE.y");
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "DROP SNOWFLAKE.ML.FORECAST SNOWFLAKE.PUBLIC.y");
        assertRefused(missing("Schema", "SNOWFLAKE.PUBLIC"), "DROP SNOWFLAKE.ML.FORECAST IF EXISTS SNOWFLAKE.PUBLIC.y");
        assertEquals("Drop statement executed successfully (Y already dropped).",
            statusOf("DROP SNOWFLAKE.ML.FORECAST IF EXISTS SNOWFLAKE.ACCOUNT_USAGE.y"));
    }

    @Test
    public void theListingsOwnRefusalsComeBeforeTheLookup() {
        assertEquals(PAGE_SIZE, answer("SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST a.b LIMIT 0"));
        assertEquals(PAGE_SIZE, answer("SHOW ROLES IN SNOWFLAKE.ML.FORECAST y LIMIT 0"));
        assertEquals(PAGE_SIZE, answer("SHOW FUNCTIONS IN nosuch y LIMIT 0"));
        assertEquals(PAGE_SIZE, answer("SHOW FUNCTIONS IN a.b y LIMIT 0"));
        assertEquals("Unsupported feature 'SHOW PROCEDURES ... WITH PRIVILEGES <>'.",
            answer("SHOW PROCEDURES IN SNOWFLAKE.ML.FORECAST a.b WITH PRIVILEGES USAGE"));
        assertEquals("Unsupported feature 'SHOW FUNCTIONS ... WITH PRIVILEGES <>'.",
            answer("SHOW FUNCTIONS IN nosuch y WITH PRIVILEGES USAGE"));
        assertEquals("syntax error line 1 at position 15 unexpected 'SNOWFLAKE.ML.FORECAST'.",
            answer("SHOW TABLES IN SNOWFLAKE.ML.FORECAST y"));
        assertEquals("SQL compilation error:|Unsupported statement type 'Cannot show objects of type VIEW in INSTANCE'.",
            answer("SHOW VIEWS IN SNOWFLAKE.ML.FORECAST y"));
    }

    @Test
    public void aDropResolvesTheInstanceOfABuiltInClass() {
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "DROP SNOWFLAKE.ML.FORECAST y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.\"y\""), "DROP SNOWFLAKE.ML.FORECAST \"y\"");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "DROP SNOWFLAKE.ML.FORECAST IDENTIFIER('y')");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "DROP IDENTIFIER('SNOWFLAKE.ML.FORECAST') y");
        assertRefused(missing("Instance", "TEST_DB.TEST_SCHEMA.Y"), "DROP SNOWFLAKE.CORE.BUDGET y");
        assertRefused(missing("Schema", "TEST_DB.A"), "DROP SNOWFLAKE.ML.FORECAST a.b");
        assertRefused(missing("Database", "A"), "DROP SNOWFLAKE.ML.FORECAST a.b.c");
        assertEquals(NO_OBJECT, answer("DROP SNOWFLAKE.ML.FORECAST a.b.c.d"));
        assertEquals(NO_OBJECT, answer("DROP SNOWFLAKE.ML.FORECAST IF EXISTS a.b.c.d"));
        assertRefused(missing("Schema", "TEST_DB.A"), "DROP SNOWFLAKE.CORE.BUDGET IF EXISTS a.b");
        assertEquals("Drop statement executed successfully (Y already dropped).",
            statusOf("DROP SNOWFLAKE.ML.FORECAST IF EXISTS y"));
    }

    @Test
    public void aClassNamedOtherwiseThanByAWordTakesNoCascadeOrRestrict() {
        assertEquals(syntaxError(16, "CASCADE"), answer("DROP \"nosuch\" y CASCADE"));
        assertEquals(syntaxError(16, "CASCADE"), answer("DROP \"NOSUCH\" y CASCADE"));
        assertEquals(syntaxError(16, "RESTRICT"), answer("DROP \"nosuch\" y RESTRICT"));
        assertEquals(syntaxError(26, "CASCADE"), answer("DROP \"nosuch\" IF EXISTS y CASCADE"));
        assertEquals(syntaxError(13, "CASCADE"), answer("DROP \"a.b\" y CASCADE"));
        assertEquals(syntaxError(35, "CASCADE"), answer("DROP \"SNOWFLAKE\".\"ML\".\"FORECAST\" y CASCADE"));
        assertEquals(syntaxError(29, "CASCADE"), answer("DROP SNOWFLAKE.ML.FORECAST y CASCADE"));
        assertEquals(syntaxError(29, "RESTRICT"), answer("DROP SNOWFLAKE.ML.FORECAST y RESTRICT"));
        assertEquals(syntaxError(39, "CASCADE"), answer("DROP SNOWFLAKE.ML.FORECAST IF EXISTS y CASCADE"));
        assertEquals(syntaxError(31, "CASCADE"), answer("DROP SNOWFLAKE.ML.FORECAST a.b CASCADE"));
        assertEquals(syntaxError(27, "CASCADE"), answer("DROP SNOWFLAKE.ML.NOSUCH y CASCADE"));
        assertEquals(syntaxError(21, "CASCADE"), answer("DROP PUBLIC.NOSUCH y CASCADE"));
        assertEquals(syntaxError(20, "CASCADE"), answer("DROP NOSUCHDB.X.Y y CASCADE"));
        assertEquals("Unsupported feature 'DROP NOSUCH'.", answer("DROP nosuch y CASCADE"));
    }
}
