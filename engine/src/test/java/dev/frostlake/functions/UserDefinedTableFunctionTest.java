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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class UserDefinedTableFunctionTest {

    private static final Logger logger = LoggerFactory.getLogger(UserDefinedTableFunctionTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

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

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(t())");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        logger.info("UDTF result: {} rows", rs.getRowCount());
    }

    @Test
    public void testUdtfReturnColumnNames() {
        engine.execute("""
            CREATE FUNCTION greet()
                RETURNS TABLE(msg VARCHAR)
                AS $$
                    SELECT 'Hello' UNION SELECT 'World'
                $$
            """);

        String db = engine.getCatalog().getCurrentDatabase();
        String sc = engine.getCatalog().getCurrentSchema();
        Function f = engine.getCatalog().getDatabase(db).getSchema(sc).getFunction("GREET");
        assertNotNull(f);
        assertTrue(f.isTableFunction());
        assertEquals(1, f.getReturnColumns().size());
        assertEquals("MSG", f.getReturnColumns().get(0).getName());
    }

    @Test
    public void testUdtfWithOrReplace() {
        engine.execute("CREATE FUNCTION t() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'v1' $$");
        engine.execute("CREATE OR REPLACE FUNCTION t() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'v2' $$");

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(t())");
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

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(repeat_msg(3))");
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

        ResultSet rs = engine.executeQuery("""
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

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(multi())");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(2, rs.getColumnCount());
    }

    @Test
    public void testUdtfDropFunction() {
        engine.execute("CREATE FUNCTION to_drop() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'x' $$");
        engine.execute("DROP FUNCTION to_drop()");

        assertThrows(RuntimeException.class, () ->
            engine.executeQuery("SELECT * FROM TABLE(to_drop())"));
    }
}
