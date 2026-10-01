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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SHOW scope names its container with an IDENTIFIER() reference as well as a written name, with or without
 * the container's kind; an account scope that names something is refused naming its last part; and a bare IN
 * needs a name after it.
 */
public class ShowScopeIdentifierAndAccountNameTest extends BaseDatabaseTest {

    /** The named column's cells, sorted as the listing gives them, or the refusal on one line. */
    private String names(final String sql, final String column) {
        try {
            final ResultSet listing = engine.executeQuery(sql);
            int index = -1;
            for (int i = 0; i < listing.getColumns().size(); i++) {
                if (listing.getColumns().get(i).getName().equalsIgnoreCase(column)) {
                    index = i;
                }
            }
            final StringBuilder out = new StringBuilder();
            for (final Row row : listing.getRows()) {
                out.append(out.length() > 0 ? ", " : "").append(row.getValue(index));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String fullPath(final String part) {
        return "SQL compilation error:|Must specify the full search path starting from database for " + part;
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void anIdentifierReferenceNamesTheScope() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT 1 AS x");
        engine.execute("SET sch = 'test_db.test_schema'");
        assertEquals("T1", names("SHOW TABLES IN IDENTIFIER('test_db.test_schema')", "name"));
        assertEquals("T1", names("SHOW TABLES IN SCHEMA IDENTIFIER('test_db.test_schema')", "name"));
        assertEquals("T1", names("SHOW TABLES IN IDENTIFIER($sch)", "name"));
        assertEquals("T1", names("SHOW TABLES IN IDENTIFIER('test_db.test_schema') LIMIT 1", "name"));
        assertEquals("V1", names("SHOW TERSE VIEWS IN SCHEMA IDENTIFIER('test_db.test_schema')", "name"));
        assertEquals("INFORMATION_SCHEMA, PUBLIC, TEST_SCHEMA", names("SHOW SCHEMAS IN IDENTIFIER('test_db')", "name"));
        assertEquals("INFORMATION_SCHEMA, PUBLIC, TEST_SCHEMA",
            names("SHOW SCHEMAS IN DATABASE IDENTIFIER('test_db')", "name"));
        assertEquals("A", names("SHOW COLUMNS IN TABLE IDENTIFIER('test_db.test_schema.t1')", "column_name"));
        assertEquals("A", names("SHOW COLUMNS IN IDENTIFIER('test_db.test_schema.t1')", "column_name"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            names("SHOW TABLES IN IDENTIFIER('nosuch')", "name"));
    }

    @Test
    public void anAccountScopeNamingSomethingIsRefused() {
        assertEquals(fullPath("TEST_DB"), names("SHOW TABLES IN ACCOUNT test_db", "name"));
        assertEquals(fullPath("TEST_SCHEMA"), names("SHOW TABLES IN ACCOUNT test_db.test_schema", "name"));
        assertEquals(fullPath("D"), names("SHOW TABLES IN ACCOUNT a.b.c.d", "name"));
        assertEquals(fullPath("lower"), names("SHOW TABLES IN ACCOUNT \"lower\"", "name"));
        assertEquals(fullPath("a.b"), names("SHOW TABLES IN ACCOUNT \"a.b\"", "name"));
        assertEquals(fullPath("ABC"), names("SHOW TABLES IN ACCOUNT IDENTIFIER('abc')", "name"));
        assertEquals(fullPath("ABC"), names("SHOW TERSE TABLES IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW TABLES LIKE 'a' IN ACCOUNT abc LIMIT 1", "name"));
        assertEquals(fullPath("Y"), names("SHOW SCHEMAS IN ACCOUNT x.y", "name"));
        assertEquals(fullPath("ABC"), names("SHOW VIEWS IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW COLUMNS IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW SEQUENCES IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW STAGES IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW OBJECTS IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW TASKS IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW PRIMARY KEYS IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW FUNCTIONS IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW USER PROCEDURES IN ACCOUNT abc", "name"));
        assertEquals(fullPath("ABC"), names("SHOW MATERIALIZED VIEWS IN ACCOUNT abc", "name"));
        assertEquals(syntax(27, "y"), names("SHOW TABLES IN ACCOUNT abc y", "name"));
        assertEquals(syntax(31, "."), names("SHOW TABLES IN ACCOUNT test_db..t", "name"));
    }

    @Test
    public void aBareInNeedsAName() {
        assertEquals(syntax(15, "<EOF>"), names("SHOW COLUMNS IN", "name"));
        assertEquals(syntax(13, "<EOF>"), names("SHOW VIEWS IN", "name"));
        assertEquals(syntax(17, "<EOF>"), names("SHOW SEQUENCES IN", "name"));
    }
}
