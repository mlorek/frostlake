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

package dev.frostlake.scripting;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for dollar-quoted string literals ($$...$$) in procedure and function definitions
 */
public class DollarQuotedTest extends BaseJdbcTest {

    @Test
    public void testCreateProcedureWithDollarQuotes() throws SQLException {
        // Create procedure using dollar-quoted string
        statement.execute("""
                CREATE PROCEDURE test_proc(x INTEGER) RETURNS INTEGER AS $$DECLARE result INTEGER; BEGIN SET result = x * 2; RETURN result; END; $$
                """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next());
        assertEquals("TEST_PROC", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testCreateFunctionWithDollarQuotes() throws SQLException {
        // Create function using dollar-quoted string
        statement.execute("CREATE FUNCTION add_ten(n INTEGER) RETURNS INTEGER AS $$n + 10$$");

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next());
        assertEquals("ADD_TEN", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotesWithSingleQuotes() throws SQLException {
        // Dollar quotes allow single quotes without escaping
        statement.execute("""
                CREATE PROCEDURE quote_test() RETURNS VARCHAR AS $$DECLARE msg VARCHAR; BEGIN SET msg = 'This has single quotes'; RETURN msg; END; $$
                """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next());
        assertEquals("QUOTE_TEST", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotesWithMultipleLines() throws SQLException {
        // Test multi-line procedure with dollar quotes
        statement.execute("""
                CREATE PROCEDURE multi_line_test(x INTEGER) RETURNS INTEGER AS $$
                DECLARE
                  result INTEGER;
                  temp INTEGER;
                BEGIN
                  SET temp = x * 2;
                  SET result = temp + 10;
                  RETURN result;
                END;
                $$
                """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next());
        assertEquals("MULTI_LINE_TEST", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testTraditionalSingleQuotesStillWork() throws SQLException {
        // Verify that traditional single-quoted strings still work
        statement.execute("CREATE FUNCTION double_value(x INTEGER) RETURNS INTEGER AS 'x * 2'");

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next());
        assertEquals("DOUBLE_VALUE", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotesWithComplexLogic() throws SQLException {
        // Test complex procedure with IF/WHILE using dollar quotes
        statement.execute("""
                CREATE PROCEDURE complex_proc(n INTEGER) RETURNS VARCHAR AS $$
                DECLARE
                  result VARCHAR;
                  counter INTEGER;
                BEGIN
                  SET counter = 0;
                  IF n > 10 THEN
                    SET result = 'Large number';
                  ELSIF n > 5 THEN
                    SET result = 'Medium number';
                  ELSE
                    SET result = 'Small number';
                  END IF;
                 \s
                  WHILE counter < n DO
                    SET counter = counter + 1;
                  END WHILE;
                 \s
                  RETURN result;
                END;
                $$
                """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next());
        assertEquals("COMPLEX_PROC", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotesWithObjectConstruct() throws SQLException {
        // Test procedure that uses OBJECT_CONSTRUCT with single quotes
        statement.execute("""
                CREATE PROCEDURE create_object() RETURNS OBJECT AS $$
                DECLARE
                  obj OBJECT;
                BEGIN
                  SET obj = OBJECT_CONSTRUCT('name', 'John', 'age', 30);
                  RETURN obj;
                END;
                $$
                """);

        // Verify procedure was created
        ResultSet rs = statement.executeQuery("SHOW PROCEDURES");
        assertTrue(rs.next());
        assertEquals("CREATE_OBJECT", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testTableFunctionWithDollarQuotes() throws SQLException {
        // Test table function with dollar quotes
        statement.execute("""
                CREATE FUNCTION get_numbers(max_val INTEGER)
                RETURNS TABLE(num INTEGER, squared INTEGER)
                AS $$SELECT n, n*n FROM numbers WHERE n <= max_val$$
                """);

        // Verify function was created
        ResultSet rs = statement.executeQuery("SHOW FUNCTIONS");
        assertTrue(rs.next());
        assertEquals("GET_NUMBERS", rs.getString("name"));
        rs.close();
    }
}
