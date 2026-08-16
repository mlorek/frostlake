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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CastExpressionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Create test table with various data types
        engine.execute("CREATE TABLE cast_test (id INTEGER, name VARCHAR, salary NUMBER, active BOOLEAN)");
        engine.execute("INSERT INTO cast_test VALUES (1, 'Alice', 50000, true)");
        engine.execute("INSERT INTO cast_test VALUES (2, 'Bob', 60000, false)");
        engine.execute("INSERT INTO cast_test VALUES (3, '123', 75000.50, true)");
        engine.execute("INSERT INTO cast_test VALUES (4, NULL, NULL, NULL)");
    }

    @Test
    public void testCastIntegerToVarchar() {
        final ResultSet result = engine.executeQuery("SELECT CAST(id AS VARCHAR) as id_str FROM cast_test WHERE id = 1");
        assertEquals(1, result.getRowCount());
        assertEquals("1", result.getRows().get(0).getValue(result.getColumnIndex("id_str")).toString());
    }

    @Test
    public void testCastStringToInteger() {
        final ResultSet result = engine.executeQuery("SELECT CAST(name AS INTEGER) as name_int FROM cast_test WHERE id = 3");
        assertEquals(1, result.getRowCount());
        assertEquals(123, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("name_int"))).intValue());
    }

    @Test
    public void testCastNumberToInteger() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST(salary AS INTEGER) as salary_int FROM cast_test WHERE id = 3
            """);
        assertEquals(1, result.getRowCount());
        // salary=75000.50 → Snowflake rounds HALF_AWAY_FROM_ZERO (INTEGER = NUMBER(38,0)), not truncation.
        assertEquals(75001, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("salary_int"))).intValue());
    }

    @Test
    public void testCastIntegerToFloat() {
        final ResultSet result = engine.executeQuery("SELECT CAST(id AS FLOAT) as id_float FROM cast_test WHERE id = 1");
        assertEquals(1, result.getRowCount());
        assertEquals(1.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("id_float"))).doubleValue(), 0.001);
    }

    @Test
    public void testCastIntegerToDouble() {
        final ResultSet result = engine.executeQuery("SELECT CAST(id AS DOUBLE) as id_double FROM cast_test WHERE id = 2");
        assertEquals(1, result.getRowCount());
        assertEquals(2.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("id_double"))).doubleValue(), 0.001);
    }

    @Test
    public void testCastStringToBoolean() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST('true' AS BOOLEAN) as bool_val FROM cast_test WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        assertTrue((Boolean) result.getRows().get(0).getValue(result.getColumnIndex("bool_val")));
    }

    @Test
    public void testCastIntegerToBoolean() {
        final ResultSet result = engine.executeQuery("SELECT CAST(1 AS BOOLEAN) as bool_val FROM cast_test WHERE id = 1");
        assertEquals(1, result.getRowCount());
        assertTrue((Boolean) result.getRows().get(0).getValue(result.getColumnIndex("bool_val")));
    }

    @Test
    public void testCastBooleanToVarchar() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST(active AS VARCHAR) as active_str FROM cast_test WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        assertEquals("true", result.getRows().get(0).getValue(result.getColumnIndex("active_str")).toString());
    }

    @Test
    public void testCastOperatorSyntax() {
        final ResultSet result = engine.executeQuery("SELECT id::VARCHAR as id_str FROM cast_test WHERE id = 1");
        assertEquals(1, result.getRowCount());
        assertEquals("1", result.getRows().get(0).getValue(result.getColumnIndex("id_str")).toString());
    }

    @Test
    public void testCastOperatorWithExpression() {
        final ResultSet result = engine.executeQuery("SELECT (id + 10)::VARCHAR as result FROM cast_test WHERE id = 1");
        assertEquals(1, result.getRowCount());
        // Integer arithmetic returns Long, which casts to "11" not "11.0"
        assertEquals("11", result.getRows().get(0).getValue(result.getColumnIndex("result")).toString());
    }

    @Test
    public void testCastWithNullValue() {
        final ResultSet result = engine.executeQuery("SELECT CAST(name AS INTEGER) as name_int FROM cast_test WHERE id = 4");
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("name_int")));
    }

    @Test
    public void testCastNullSalaryToVarchar() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST(salary AS VARCHAR) as salary_str FROM cast_test WHERE id = 4
            """);
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("salary_str")));
    }

    @Test
    public void testCastInWhereClause() {
        final ResultSet result = engine.executeQuery("SELECT name FROM cast_test WHERE CAST(id AS VARCHAR) = '1'");
        assertEquals(1, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(result.getColumnIndex("name")));
    }

    @Test
    public void testCastInConcatenation() {
        final ResultSet result = engine.executeQuery("""
            SELECT 'ID: ' || CAST(id AS VARCHAR) as result FROM cast_test WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        assertEquals("ID: 1", result.getRows().get(0).getValue(result.getColumnIndex("result")));
    }

    @Test
    public void testCastFloatToInteger() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST(75000.75 AS INTEGER) as int_val FROM cast_test WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        // 75000.75 → Snowflake rounds HALF_AWAY_FROM_ZERO, giving 75001 (not truncated 75000).
        assertEquals(75001, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("int_val"))).intValue());
    }

    @Test
    public void testIntegerCastRoundsHalfAwayFromZero() {
        // Snowflake casts to INTEGER (an alias of NUMBER(38,0)) by rounding HALF_AWAY_FROM_ZERO,
        // not truncating toward zero. This holds for CAST(...) and the :: operator alike.
        assertEquals(4, castToInt("CAST(3.9 AS INTEGER)"));
        assertEquals(3, castToInt("CAST(2.5 AS INT)"));     // exactly .5 rounds away from zero
        assertEquals(2, castToInt("CAST(2.4 AS INT)"));     // below .5 rounds down
        assertEquals(-3, castToInt("CAST(-2.5 AS INT)"));   // symmetric for negatives
        assertEquals(-4, castToInt("CAST(-3.9 AS BIGINT)"));
        assertEquals(4, castToInt("3.9::INT"));             // :: shares the CAST evaluation path
    }

    /** Evaluate a scalar integer-cast expression against a single row and return it as an int. */
    private int castToInt(final String castExpr) {
        final ResultSet r = engine.executeQuery(
            "SELECT " + castExpr + " AS r FROM cast_test WHERE id = 1");
        return ((Number) r.getRows().get(0).getValue(r.getColumnIndex("r"))).intValue();
    }

    @Test
    public void testCastToNumber() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST('123.45' AS NUMBER) as num_val FROM cast_test WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        // Bare NUMBER is NUMBER(38,0): CAST('123.45' AS NUMBER) rounds to a whole number (Snowflake).
        assertEquals(123.0, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("num_val"))).doubleValue(), 0.001);
    }

    @Test
    public void testCastChaining() {
        final ResultSet result = engine.executeQuery("""
            SELECT CAST(CAST(id AS VARCHAR) AS INTEGER) as result FROM cast_test WHERE id = 1
            """);
        assertEquals(1, result.getRowCount());
        assertEquals(1, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("result"))).intValue());
    }

    @Test
    public void testInvalidCastStringToInteger() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT CAST(name AS INTEGER) FROM cast_test WHERE id = 1");
                
            }
        });
    }

    @Test
    public void testCastWithCaseExpression() {
        final ResultSet result = engine.executeQuery(
            "SELECT CASE WHEN id = 1 THEN CAST(id AS VARCHAR) ELSE 'other' END as result FROM cast_test WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("1", result.getRows().get(0).getValue(result.getColumnIndex("result")));
    }

    @Test
    public void testMultipleCastsInSelect() {
        final ResultSet result = engine.executeQuery(
            "SELECT CAST(id AS VARCHAR) as id_str, CAST(salary AS INTEGER) as salary_int FROM cast_test WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("1", result.getRows().get(0).getValue(result.getColumnIndex("id_str")).toString());
        assertEquals(50000, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("salary_int"))).intValue());
    }
}
