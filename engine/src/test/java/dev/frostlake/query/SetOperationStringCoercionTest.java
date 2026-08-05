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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A set operation mixing a VARCHAR branch with a non-string branch unifies to the NON-STRING side,
 * and the string branch's VALUES convert to it — whichever side leads. Every cell here was measured
 * on a real Snowflake account, one query per cell:
 *
 * <ul>
 *   <li>a DECLARED VARCHAR branch (a cast or a column, any length — 1 through 16777216 measured
 *       alike) contributes {@code NUMBER(18,5)} to the numeric-supertype fold, so {@code ∪
 *       NUMBER(10,2)} declares NUMBER(18,5), {@code ∪ NUMBER(30,10)} NUMBER(30,10) and {@code ∪
 *       NUMBER(38,0)} NUMBER(38,5);</li>
 *   <li>a bare STRING LITERAL branch contributes the literal's own numeric measurement instead —
 *       {@code '5' ∪ 1} declares NUMBER(1,0), {@code '5' ∪ 1::NUMBER(10,2)} NUMBER(10,2) and
 *       {@code '2.75' ∪ 1} NUMBER(3,2);</li>
 *   <li>values render at the unified scale ({@code 5.00} and {@code 1.00} under NUMBER(10,2));</li>
 *   <li>an unconvertible string fails "Numeric value 'x' is not recognized" in either branch order,
 *       an empty string the same way, and a DATE pairing fails "Date 'y' is not recognized";</li>
 *   <li>beside FLOAT the union is FLOAT, beside DATE it is DATE.</li>
 * </ul>
 */
public class SetOperationStringCoercionTest extends BaseDatabaseTest {

    private String value(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** All first-column values in row order, joined with {@code |}. */
    private String columnValues(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder joined = new StringBuilder();
        for (final dev.frostlake.storage.Row row : rs.getRows()) {
            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(row.getValue(0));
        }
        return joined.toString();
    }

    private String declared(final String table, final String column) {
        return value("SELECT data_type || '(' || numeric_precision || ',' || numeric_scale || ')'"
            + " FROM test_db.information_schema.columns WHERE table_name = '" + table.toUpperCase()
            + "' AND column_name = '" + column.toUpperCase() + "'");
    }

    private void assertFailsWith(final String sql, final String expectedFragment) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedFragment),
            "expected \"" + expectedFragment + "\" in: " + error.getMessage());
    }

    // ── the declared type of the union ──

    @Test
    public void aDeclaredVarcharBranchContributesNumber18x5ToTheFold() {
        engine.execute("CREATE TABLE u1 AS SELECT '1'::VARCHAR(10) AS a UNION ALL SELECT 1");
        assertEquals("NUMBER(18,5)", declared("u1", "a"));
        engine.execute("CREATE TABLE u2 AS SELECT '1'::VARCHAR(10) AS a"
            + " UNION ALL SELECT 1::NUMBER(10,2)");
        assertEquals("NUMBER(18,5)", declared("u2", "a"));
        engine.execute("CREATE TABLE u3 AS SELECT '1'::VARCHAR(10) AS a"
            + " UNION ALL SELECT 1::NUMBER(30,10)");
        assertEquals("NUMBER(30,10)", declared("u3", "a"));
        engine.execute("CREATE TABLE u4 AS SELECT '1'::VARCHAR(10) AS a"
            + " UNION ALL SELECT 1::NUMBER(38,0)");
        assertEquals("NUMBER(38,5)", declared("u4", "a"));
    }

    @Test
    public void aStringLiteralBranchContributesItsOwnMeasurement() {
        engine.execute("CREATE TABLE l1 AS SELECT '5' AS a UNION ALL SELECT 1");
        assertEquals("NUMBER(1,0)", declared("l1", "a"));
        engine.execute("CREATE TABLE l2 AS SELECT '5' AS a UNION ALL SELECT 1::NUMBER(10,2)");
        assertEquals("NUMBER(10,2)", declared("l2", "a"));
        engine.execute("CREATE TABLE l3 AS SELECT '2.75' AS a UNION ALL SELECT 1");
        assertEquals("NUMBER(3,2)", declared("l3", "a"));
    }

    // ── the values ──

    @Test
    public void valuesRenderAtTheUnifiedScale() {
        assertEquals("5|1", columnValues("SELECT '5' AS a UNION ALL SELECT 1"));
        assertEquals("1.00|5.00", columnValues(
            "SELECT a FROM (SELECT '5' AS a UNION ALL SELECT 1::NUMBER(10,2)) ORDER BY a"));
        assertEquals("1.00000|2.70000", columnValues(
            "SELECT a FROM (SELECT '2.7'::VARCHAR(3) AS a UNION ALL SELECT 1) ORDER BY a"));
    }

    @Test
    public void anUnconvertibleStringFailsInEitherBranchOrder() {
        assertFailsWith("SELECT 'x' AS a UNION ALL SELECT 1", "Numeric value 'x' is not recognized");
        assertFailsWith("SELECT 1 AS a UNION ALL SELECT 'x'", "Numeric value 'x' is not recognized");
        assertFailsWith("SELECT 'x' AS a UNION ALL SELECT 1.5", "Numeric value 'x' is not recognized");
        assertFailsWith("SELECT ''::VARCHAR(3) AS a UNION ALL SELECT 1",
            "Numeric value '' is not recognized");
    }

    @Test
    public void aDatePairingConvertsTheStringOrFailsItsOwnWay() {
        assertFailsWith("SELECT '2024-01-01'::DATE AS a UNION ALL SELECT 'y'",
            "Date 'y' is not recognized");
        assertEquals("2024-01-01|2024-01-02", columnValues("SELECT a FROM ("
            + "SELECT '2024-01-01'::VARCHAR(10) AS a UNION ALL SELECT '2024-01-02'::DATE) ORDER BY a"));
    }

    /** Two string branches stay text — no numeric side, no conversion, '01' distinct from '1'. */
    @Test
    public void allStringBranchesStayText() {
        assertEquals("01|1", columnValues(
            "SELECT a FROM (SELECT '01' AS a UNION SELECT '1') ORDER BY a"));
    }
}
