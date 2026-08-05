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

package dev.frostlake.expressions;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON object and array literals in expressions
 */
public class JsonLiteralsTest extends BaseJdbcTest {

    @Test
    public void testSelectJsonObjectLiteral() throws SQLException {
        // Simple JSON object literal
        ResultSet rs = statement.executeQuery("SELECT {'name': 'John', 'age': 30} as user_json");
        assertTrue(rs.next());
        String json = rs.getString("user_json");
        assertNotNull(json);
        assertTrue(json.contains("name"));
        assertTrue(json.contains("John"));
        assertTrue(json.contains("age"));
        assertTrue(json.contains("30"));
        rs.close();
    }

    @Test
    public void controlCharactersInLiteralStringsAreJsonEscaped() throws SQLException {
        // A data value with an embedded newline (or quote/backslash) must be ESCAPED in the literal's
        // JSON text; emitting it raw made the stored VARIANT invalid JSON, and every later path access
        // over it silently returned NULL — an ingest row whose file path contained a newline vanished
        // from a loader's output entirely.
        ResultSet rs = statement.executeQuery(
            "SELECT {'a': 'x\ny'}:a::VARCHAR AS v, {'q': 'he said \"hi\"'}:q::VARCHAR AS w,"
            + " {'deep': {'p': 'l1\nl2'}}:deep:p::VARCHAR AS d");
        assertTrue(rs.next());
        // Engine leniency: the string path result arrives unwrapped (live Snowflake shows the
        // QUOTED variant text "x\ny" — recorded as an open display-fidelity lead).
        assertEquals("x\ny", rs.getString("v"));
        assertEquals("he said \"hi\"", rs.getString("w"));
        assertEquals("l1\nl2", rs.getString("d"));
        rs.close();
    }

    @Test
    public void arrayLiteralElementsAreJsonEscapedToo() throws SQLException {
        ResultSet rs = statement.executeQuery("SELECT ['a\nb', 'c']::VARIANT AS arr, ['a\nb'][0]::VARCHAR AS el");
        assertTrue(rs.next());
        assertEquals("a\nb", rs.getString("el"));
        rs.close();
    }

    @Test
    public void testSelectJsonArrayLiteral() throws SQLException {
        // Simple JSON array literal
        ResultSet rs = statement.executeQuery("SELECT [1, 2, 3, 4, 5] as numbers");
        assertTrue(rs.next());
        String json = rs.getString("numbers");
        assertNotNull(json);
        assertTrue(json.contains("1"));
        assertTrue(json.contains("5"));
        rs.close();
    }

    @Test
    public void testInsertJsonObjectIntoTable() throws SQLException {
        // Create table with VARIANT column
        statement.execute("CREATE TABLE users (id INTEGER, profile VARIANT)");

        // A semi-structured literal in a VALUES clause is rejected (live-verified Snowflake rule);
        // INSERT ... SELECT is the supported route.
        try {
            statement.execute("INSERT INTO users VALUES (1, {'name': 'Alice', 'email': 'alice@example.com'})");
            fail("expected rejection of a JSON literal in VALUES");
        } catch (final SQLException rejected) {
            assertTrue(rejected.getMessage().contains("in VALUES clause"), rejected.getMessage());
        }
        statement.execute("INSERT INTO users SELECT 1, {'name': 'Alice', 'email': 'alice@example.com'}");

        // Verify
        ResultSet rs = statement.executeQuery("SELECT * FROM users");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        String profile = rs.getString("profile");
        assertNotNull(profile);
        assertTrue(profile.contains("Alice"));
        rs.close();
    }

    @Test
    public void testInsertJsonArrayIntoTable() throws SQLException {
        // Create table with VARIANT column
        statement.execute("CREATE TABLE tags_table (id INTEGER, tag_list VARIANT)");

        // Same VALUES-clause rejection applies to array literals; load via SELECT.
        try {
            statement.execute("INSERT INTO tags_table VALUES (1, ['java', 'sql', 'database'])");
            fail("expected rejection of an array literal in VALUES");
        } catch (final SQLException rejected) {
            assertTrue(rejected.getMessage().contains("in VALUES clause"), rejected.getMessage());
        }
        statement.execute("INSERT INTO tags_table SELECT 1, ['java', 'sql', 'database']");

        // Verify
        ResultSet rs = statement.executeQuery("SELECT * FROM tags_table");
        assertTrue(rs.next());
        String tags = rs.getString("tag_list");
        assertNotNull(tags);
        assertTrue(tags.contains("java"));
        assertTrue(tags.contains("sql"));
        rs.close();
    }

    @Test
    public void testNestedJsonObjects() throws SQLException {
        // Nested JSON objects
        String sql = "SELECT {'user': {'name': 'Bob', 'age': 25}, 'active': 'true'} as data";
        ResultSet rs = statement.executeQuery(sql);
        assertTrue(rs.next());
        String data = rs.getString("data");
        assertNotNull(data);
        assertTrue(data.contains("user"));
        assertTrue(data.contains("Bob"));
        assertTrue(data.contains("active"));
        rs.close();
    }

    @Test
    public void testJsonArrayOfObjects() throws SQLException {
        // Array of JSON objects
        String sql = "SELECT [{'id': 1, 'name': 'Item1'}, {'id': 2, 'name': 'Item2'}] as items";
        ResultSet rs = statement.executeQuery(sql);
        assertTrue(rs.next());
        String items = rs.getString("items");
        assertNotNull(items);
        assertTrue(items.contains("Item1"));
        assertTrue(items.contains("Item2"));
        rs.close();
    }

    @Test
    public void testEmptyJsonObject() throws SQLException {
        // Empty JSON object
        ResultSet rs = statement.executeQuery("SELECT {} as empty_obj");
        assertTrue(rs.next());
        String obj = rs.getString("empty_obj");
        assertNotNull(obj);
        assertTrue(obj.equals("{}") || obj.equals("{ }"));
        rs.close();
    }

    @Test
    public void testEmptyJsonArray() throws SQLException {
        // Empty JSON array
        ResultSet rs = statement.executeQuery("SELECT [] as empty_arr");
        assertTrue(rs.next());
        String arr = rs.getString("empty_arr");
        assertNotNull(arr);
        assertTrue(arr.equals("[]") || arr.equals("[ ]"));
        rs.close();
    }

    @Test
    public void testJsonWithColumnReferences() throws SQLException {
        // Create table
        statement.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, age INTEGER)");
        statement.execute("INSERT INTO employees VALUES (1, 'Alice', 30)");
        statement.execute("INSERT INTO employees VALUES (2, 'Bob', 25)");

        // Select with JSON object containing column references
        ResultSet rs = statement.executeQuery(
                "SELECT id, {'name': name, 'age': age} as json_data FROM employees ORDER BY id"
        );

        // First row
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        String json1 = rs.getString("json_data");
        assertNotNull(json1);
        assertTrue(json1.contains("Alice"));
        assertTrue(json1.contains("30"));

        // Second row
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        String json2 = rs.getString("json_data");
        assertNotNull(json2);
        assertTrue(json2.contains("Bob"));
        assertTrue(json2.contains("25"));

        rs.close();
    }

    @Test
    public void testJsonArrayWithColumnReferences() throws SQLException {
        // Create table
        statement.execute("CREATE TABLE products (id INTEGER, price DECIMAL(10,2))");
        statement.execute("INSERT INTO products VALUES (1, 10.99)");
        statement.execute("INSERT INTO products VALUES (2, 20.50)");
        statement.execute("INSERT INTO products VALUES (3, 15.75)");

        // Select array of prices
        ResultSet rs = statement.executeQuery(
                "SELECT [price, price, price] as price_array FROM products WHERE id = 1"
        );

        assertTrue(rs.next());
        String arr = rs.getString("price_array");
        assertNotNull(arr);
        assertTrue(arr.contains("10.99"));
        rs.close();
    }

    @Test
    public void testJsonWithDifferentDataTypes() throws SQLException {
        // JSON with various data types
        String sql = """
            SELECT {'string': 'hello', 'number': 42, 'bool_true': 'true', 'bool_false': 'false', 'null_val': NULL} as mixed
            """;
        ResultSet rs = statement.executeQuery(sql);
        assertTrue(rs.next());
        String mixed = rs.getString("mixed");
        assertNotNull(mixed);
        assertTrue(mixed.contains("hello"));
        assertTrue(mixed.contains("42"));
        rs.close();
    }

    @Test
    public void testComplexNestedStructure() throws SQLException {
        // Complex nested JSON structure
        String sql = """
                SELECT {
                    'company': 'Acme Corp',
                    'employees': [
                        {'name': 'Alice', 'role': 'Engineer'},
                        {'name': 'Bob', 'role': 'Manager'}
                    ],
                    'metadata': {
                        'created': '2024-01-01',
                        'active': 'true'
                    }
                } as company_data
                """;

        ResultSet rs = statement.executeQuery(sql);
        assertTrue(rs.next());
        String data = rs.getString("company_data");
        assertNotNull(data);
        assertTrue(data.contains("Acme Corp"));
        assertTrue(data.contains("Alice"));
        assertTrue(data.contains("Engineer"));
        assertTrue(data.contains("metadata"));
        rs.close();
    }

    @Test
    public void objectLiteralDropsSqlNullPairsLikeObjectConstruct() throws SQLException {
        // Snowflake object constants are equivalent to OBJECT_CONSTRUCT: a pair whose value is SQL
        // NULL is omitted, while a JSON null (PARSE_JSON('null')) is kept as a real null member.
        ResultSet rs = statement.executeQuery("SELECT {'a': 1, 'gone': NULL} AS o");
        assertTrue(rs.next());
        assertEquals("{\"a\":1}", rs.getString("o"));
        rs.close();

        rs = statement.executeQuery("SELECT {'a': 1, 'kept': PARSE_JSON('null')} AS o");
        assertTrue(rs.next());
        assertEquals("{\"a\":1,\"kept\":null}", rs.getString("o"));
        rs.close();
    }

    @Test
    public void bracketLiteralsCanonicalizeLikeParseJson() throws SQLException {
        // An array/object literal must store the same canonical text a VARIANT extraction produces —
        // keys sorted, compact separators — or EXCEPT sees a phantom diff between them.
        ResultSet rs = statement.executeQuery("SELECT [{'edition': 'pro*'}] AS c");
        assertTrue(rs.next());
        assertEquals("[{\"edition\":\"pro*\"}]", rs.getString("c"));
        rs.close();

        rs = statement.executeQuery("SELECT {'b': 2, 'a': 1} AS o");
        assertTrue(rs.next());
        assertEquals("{\"a\":1,\"b\":2}", rs.getString("o"));
        rs.close();

        rs = statement.executeQuery(
            "SELECT [{'max_version': '9.1','min_version': '9.0'}] EXCEPT SELECT PARSE_JSON('[{\"min_version\":\"9.0\",\"max_version\":\"9.1\"}]')");
        assertFalse(rs.next());
        rs.close();
    }
}
