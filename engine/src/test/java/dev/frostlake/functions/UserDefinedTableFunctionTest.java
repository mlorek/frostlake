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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserDefinedTableFunctionTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(UserDefinedTableFunctionTest.class);

    @Test
    public void testCreateAndCallBasicUdtf() {
        engine.execute("""
            CREATE FUNCTION t()
                RETURNS TABLE(msg VARCHAR)
                AS
                $$
                    SELECT 'Hello'
                    UNION
                    SELECT 'World'
                $$
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(t())");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        logger.info("UDTF result: {} rows", rs.getRowCount());
    }

    /**
     * The declared RETURNS TABLE column reaches the result set under its own name, folded to upper
     * case. Asserted through SQL rather than off the catalog object: a catalog assertion reads the
     * EMBEDDED metastore even on a live run, where the function was created on the account, so it
     * could only ever have tested one side.
     */
    @Test
    public void testUdtfReturnColumnNames() {
        engine.execute("""
            CREATE FUNCTION greet()
                RETURNS TABLE(msg VARCHAR)
                AS $$
                    SELECT 'Hello' UNION SELECT 'World'
                $$
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(greet())");
        assertEquals(1, rs.getColumns().size());
        assertEquals("MSG", rs.getColumns().get(0).getName());
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void testUdtfWithOrReplace() {
        engine.execute("CREATE FUNCTION t() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'v1' $$");
        engine.execute("CREATE OR REPLACE FUNCTION t() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'v2' $$");

        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(t())");
        assertEquals(1, rs.getRowCount());
        assertEquals("v2", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testUdtfWithParameter() {
        engine.execute("""
            CREATE FUNCTION repeat_msg(n INTEGER)
                RETURNS TABLE(msg VARCHAR)
                AS $$
                    SELECT 'hello'
                $$
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(repeat_msg(3))");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1);
    }

    @Test
    public void testUdtfInJoin() {
        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'a'), (2, 'b')");

        engine.execute("""
            CREATE FUNCTION two_rows()
                RETURNS TABLE(val VARCHAR)
                AS $$ SELECT 'x' UNION SELECT 'y' $$
            """);

        final ResultSet rs = engine.executeQuery("""
            SELECT i.name
            FROM items i, TABLE(two_rows()) f
            """);
        assertNotNull(rs);
        // cross-join: 2 items × 2 rows = 4
        assertEquals(4, rs.getRowCount());
    }

    @Test
    public void testUdtfMultipleColumns() {
        engine.execute("""
            CREATE FUNCTION multi()
                RETURNS TABLE(id INTEGER, name VARCHAR)
                AS $$
                    SELECT 1, 'Alice'
                    UNION
                    SELECT 2, 'Bob'
                $$
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(multi())");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(2, rs.getColumnCount());
    }

    @Test
    public void testUdtfDropFunction() {
        engine.execute("CREATE FUNCTION to_drop() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'x' $$");
        engine.execute("DROP FUNCTION to_drop()");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM TABLE(to_drop())");
            }
        });
    }
}
