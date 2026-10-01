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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CREATE RECURSIVE VIEW} is a view whose body may name the view itself. The word stands where a
 * view's temporariness would and excludes it — {@code SECURE RECURSIVE} parses, {@code TEMPORARY
 * RECURSIVE} is a syntax error at RECURSIVE — and SHOW VIEWS keeps the statement as written.
 *
 * <p>COPY GRANTS copies from the object being replaced, so a view created without OR REPLACE has no
 * source to copy from and is refused, as a table's is.
 */
public class RecursiveViewCreationTest extends BaseDatabaseTest {

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The message of the refusal the statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " ");
    }

    /** It is created and read like any other view, with or without a column list. */
    @Test
    public void itIsCreatedAndRead() {
        engine.execute("CREATE RECURSIVE VIEW uv8 (n) COMMENT = 'r' AS SELECT 1");
        assertEquals("1", answer("SELECT * FROM uv8"));
        engine.execute("CREATE RECURSIVE VIEW uv9 AS SELECT 1 AS n");
        assertEquals("1", answer("SELECT * FROM uv9"));
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW uv8 (n) AS SELECT 2");
        assertEquals("2", answer("SELECT * FROM uv8"));
    }

    /** SECURE stands before it; a temporariness does not stand with it at all. */
    @Test
    public void secureStandsBeforeItAndTemporaryNotAtAll() {
        engine.execute("CREATE OR REPLACE SECURE RECURSIVE VIEW uv7 (n) AS SELECT 3");
        assertEquals("3", answer("SELECT * FROM uv7"));
        assertTrue(refusal("CREATE OR REPLACE TEMPORARY RECURSIVE VIEW uv6 (n) AS SELECT 4")
            .contains("syntax error line 1 at position 28 unexpected 'RECURSIVE'."));
    }

    /** SHOW VIEWS keeps the statement as written, the word included. */
    @Test
    public void showKeepsTheWordAsWritten() {
        engine.execute("CREATE OR REPLACE RECURSIVE VIEW uv4 (n) COMMENT = 'r' AS SELECT 1");
        engine.executeQuery("SHOW VIEWS LIKE 'UV4'");
        assertEquals("CREATE OR REPLACE RECURSIVE VIEW uv4 (n) comment = 'r' AS SELECT 1",
            answer("SELECT \"text\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"),
            "the statement as written, but for the property word the account lower-cases");
    }

    /** COPY GRANTS needs a source: without OR REPLACE a view and a materialized view are refused. */
    @Test
    public void copyGrantsNeedsAnObjectToCopyFrom() {
        assertEquals("SQL compilation error: Invalid operation COPY GRANTS without specifying source object.",
            refusal("CREATE VIEW ug5 COPY GRANTS COMMENT = 'x' AS SELECT 1 AS x"));
        assertEquals("SQL compilation error: Invalid operation COPY GRANTS without specifying source object.",
            refusal("CREATE MATERIALIZED VIEW ug7 COPY GRANTS AS SELECT 1 AS x"));
        assertEquals("SQL compilation error: Invalid operation COPY GRANTS without specifying source object.",
            refusal("CREATE TABLE ug8 COPY GRANTS (a INT)"));
    }

    /** With OR REPLACE there IS a source, and the create goes through. */
    @Test
    public void copyGrantsIsTakenWithOrReplace() {
        engine.execute("CREATE OR REPLACE VIEW ug6 COPY GRANTS AS SELECT 1 AS x");
        assertEquals("1", answer("SELECT x FROM ug6"));
    }
}
