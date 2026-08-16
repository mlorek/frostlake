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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A FLOAT's runtime carrier is a double, and everything that follows from it — read through the
 * driver on both engines, so each cell is driver-to-driver.
 *
 * <p>★ A FLOAT COLUMN HOLDS A DOUBLE. Every write path converts: a stored {@code 0.1} reads back as
 * {@code 0.10000000000000000555} through {@code ::NUMBER(38,20)}, a stored nineteen-digit integer is
 * {@code 1234567890123456768}, and {@code getObject} hands back a Double. Frostlake used to keep
 * whatever class the value arrived in, so one column held BigDecimals, Longs and Doubles side by side
 * and answered three different things through a cast, a comparison and a SUM.
 *
 * <p>★ NEGATIVE ZERO IS KEPT — through the cast, negation, ABS, arithmetic and the conditionals — and
 * it EQUALS zero: a tie keeps whichever was seen first, and DISTINCT counts the two zeros once. CEIL,
 * TRUNC, ROUND and MOD hand back a positive zero; FLOOR, SQRT and division keep the sign, all as the
 * account does.
 *
 * <p>★ A FLOAT BESIDE AN EXACT NUMBER COMPARES AS A DOUBLE, a double casts to NUMBER from its exact
 * binary digits, and a sum of doubles is COMPENSATED: ten tenths are exactly {@code 1}.
 *
 * <p>Two things are deliberately not pinned. The digits {@code getObject} carries for a value that is
 * not exactly representable: the in-process driver hands back the engine's own double, while the
 * account's driver in JSON result mode re-parses the fifteen-digit text it was sent. And the sign of a
 * zero read back through a TABLE aggregate or a constant column: the account answers those from
 * partition statistics, which hold no negative zero, so a stored {@code -0.0} reads as {@code 0} there
 * whenever it is the column's only value. A scalar subquery is a third, smaller caveat — the account
 * re-parses its FLOAT answer from fifteen-digit text — so every comparison below is written inside the
 * FROM it reads.
 */
public class FloatCarrierJdbcTest extends BaseJdbcTest {

    /** One expression as the driver renders it. */
    private String text(final String expression) throws SQLException {
        return firstText("SELECT " + expression + " AS v");
    }

    /** The first column of the first row of a query, as {@code getString} renders it. */
    private String firstText(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        rs.next();
        final String value = rs.getString(1);
        rs.close();
        return value;
    }

    /** The first column of the first row of a query, as {@code getObject} hands it back. */
    private Object firstObject(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        rs.next();
        final Object value = rs.getObject(1);
        rs.close();
        return value;
    }

    /** A predicate's answer. */
    private boolean truth(final String predicate) throws SQLException {
        return truthOf("SELECT " + predicate + " AS v");
    }

    /** The first column of the first row of a query, read as a boolean. */
    private boolean truthOf(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        rs.next();
        final boolean value = rs.getBoolean(1);
        rs.close();
        return value;
    }

    /** ★ A negative zero keeps its sign wherever the account keeps it. */
    @Test
    public void negativeZeroKeepsItsSign() throws SQLException {
        assertEquals("-0", text("-0.0::FLOAT"), "the cast binds tighter than the sign, so this negates a double");
        assertEquals("-0", text("-(0.0::FLOAT)"));
        assertEquals("-0", text("-0e0::FLOAT"));
        assertEquals("0", text("(-0.0)::FLOAT"), "a NUMBER literal has no sign of zero to carry into the cast");
        assertEquals("-0", text("TO_VARCHAR(-0.0::FLOAT)"));
        assertEquals("-0", text("-0.0::FLOAT || ''"));
        assertEquals("-0", text("(-0.0::FLOAT)::VARCHAR"));
        assertEquals("-0", text("ABS(-0.0::FLOAT)"), "★ ABS keeps it — it negates only what is below zero");
        assertEquals("-0", text("-0.0::FLOAT * 5"));
        assertEquals("-0", text("-0.0::FLOAT / 5"));
        assertEquals("-0", text("-0.0::FLOAT - 0"));
        assertEquals("-0", text("-0.0::FLOAT + -0.0::FLOAT"));
        assertEquals("-0", text("SQRT(-0.0::FLOAT)"));
        assertEquals("-0", text("FLOOR(-0.0::FLOAT)"));
        assertEquals("-0", text("IFF(TRUE, -0.0::FLOAT, 1)"));
        assertEquals("-0", text("COALESCE(-0.0::FLOAT, 1)"));
        assertEquals("-0", text("CASE WHEN TRUE THEN -0.0::FLOAT ELSE 0.0::FLOAT END"));
        assertEquals("{\"k\":-0.000000000000000e+00}", text("TO_JSON(OBJECT_CONSTRUCT('k', -0.0::FLOAT))"));
        assertEquals(Double.valueOf(-0.0), firstObject("SELECT -0.0::FLOAT AS v"),
            "★ getObject is a Double, and Double.equals tells the two zeros apart");
    }

    /** And loses it exactly where the account does. */
    @Test
    public void negativeZeroTurnsPositiveWhereTheAccountTurnsIt() throws SQLException {
        assertEquals("0", text("-0.0::FLOAT + 0"));
        assertEquals("0", text("-0.0::FLOAT * -1"));
        assertEquals("0", text("-(-0.0::FLOAT)"));
        assertEquals("0", text("CEIL(-0.4::FLOAT)"), "★ CEIL gives a positive zero where IEEE ceiling gives -0");
        assertEquals("0", text("CEIL(-0.04::FLOAT, 1)"));
        assertEquals("0", text("TRUNC(-0.4::FLOAT)"));
        assertEquals("0", text("TRUNC(-0.04::FLOAT, 1)"));
        assertEquals("0", text("ROUND(-0.4::FLOAT)"));
        assertEquals("0", text("ROUND(-0.0::FLOAT, 2)"));
        assertEquals("0", text("MOD(-0.0::FLOAT, 1)"));
        assertEquals("0", text("SIGN(-0.0::FLOAT)"));
    }

    /** The two zeros are EQUAL, a tie keeps the first, and DISTINCT counts them once. */
    @Test
    public void theTwoZerosAreOneValue() throws SQLException {
        assertTrue(truth("-0.0::FLOAT = 0.0::FLOAT"));
        assertFalse(truth("-0.0::FLOAT < 0"));
        assertTrue(truth("-0.0::FLOAT IN (0)"));
        assertEquals("-0", text("GREATEST(-0.0::FLOAT, 0.0::FLOAT)"), "★ a tie keeps the first seen");
        assertEquals("0", text("GREATEST(0.0::FLOAT, -0.0::FLOAT)"));
        assertEquals("-0", text("LEAST(-0.0::FLOAT, 0.0::FLOAT)"));
        assertEquals("0", text("LEAST(0.0::FLOAT, -0.0::FLOAT)"));
        statement.execute("CREATE OR REPLACE TABLE fzz (f FLOAT)");
        statement.execute("INSERT INTO fzz SELECT -0.0::FLOAT");
        statement.execute("INSERT INTO fzz SELECT 0.0::FLOAT");
        assertEquals("1", firstText("SELECT COUNT(DISTINCT f) FROM fzz"));
        final ResultSet distinct = statement.executeQuery("SELECT DISTINCT f FROM fzz");
        int rows = 0;
        while (distinct.next()) {
            rows++;
        }
        distinct.close();
        assertEquals(1, rows, "SELECT DISTINCT folds the two zeros into one row");
        statement.execute("DROP TABLE fzz");
    }

    /** ★ A FLOAT column holds a double, through every write path. */
    @Test
    public void aFloatColumnHoldsADouble() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE fw (id INT, f FLOAT, n NUMBER(10,2))");
        statement.execute("INSERT INTO fw VALUES (1, 0.1, 1.5)");
        statement.execute("INSERT INTO fw VALUES (2, 1234567890123456789, 1.5)");
        statement.execute("INSERT INTO fw VALUES (3, '0.1', 1.5)");
        statement.execute("INSERT INTO fw VALUES (4, 3, 1.5)");
        statement.execute("INSERT INTO fw SELECT 5, 9007199254740993, 1.5");
        statement.execute("INSERT INTO fw (id, f) SELECT 6, n FROM fw WHERE id = 1");
        assertEquals("0.10000000000000000555", firstText("SELECT f::NUMBER(38,20) FROM fw WHERE id = 1"),
            "★ a VALUES literal became the nearest double, whose exact digits a cast can show");
        assertEquals("0.10000000000000000555", firstText("SELECT f::NUMBER(38,20) FROM fw WHERE id = 3"),
            "text converts the same way");
        assertEquals("1234567890123456768", firstText("SELECT f::NUMBER(38,0) FROM fw WHERE id = 2"),
            "★ a nineteen-digit integer is the double nearest it");
        assertEquals("1.23456789012346e+18", firstText("SELECT f FROM fw WHERE id = 2"));
        assertEquals("1.23456789012346e+18", firstText("SELECT TO_VARCHAR(f) FROM fw WHERE id = 2"));
        assertEquals("1.23456789012346e+18", firstText("SELECT f::VARCHAR FROM fw WHERE id = 2"));
        assertEquals("9007199254740992", firstText("SELECT f::NUMBER(38,0) FROM fw WHERE id = 5"),
            "one past 2^53 rounds to the even double");
        assertEquals("3", firstText("SELECT f FROM fw WHERE id = 4"));
        assertEquals("1.50000000000000000000", firstText("SELECT f::NUMBER(38,20) FROM fw WHERE id = 6"),
            "a NUMBER(10,2) value converts exactly when the double can hold it");
        assertTrue(truthOf("SELECT f = 1234567890123456768 FROM fw WHERE id = 2"));
        assertTrue(truthOf("SELECT f = 1234567890123456789 FROM fw WHERE id = 2"),
            "★ the exact side of a comparison is converted to a double too");
        assertEquals(Double.valueOf(0.1), firstObject("SELECT f FROM fw WHERE id = 1"),
            "getObject on a FLOAT column is a Double");

        statement.execute("UPDATE fw SET f = 0.3 WHERE id = 4");
        assertEquals("0.29999999999999998890", firstText("SELECT f::NUMBER(38,20) FROM fw WHERE id = 4"),
            "UPDATE converts");
        statement.execute("MERGE INTO fw t USING (SELECT 7 AS id, 0.3 AS v) s ON t.id = s.id"
            + " WHEN NOT MATCHED THEN INSERT (id, f) VALUES (s.id, s.v)");
        assertEquals("0.29999999999999998890", firstText("SELECT f::NUMBER(38,20) FROM fw WHERE id = 7"),
            "MERGE converts");
        statement.execute("CREATE OR REPLACE TABLE fwc (f FLOAT) AS SELECT 0.1");
        assertEquals("0.10000000000000000555", firstText("SELECT f::NUMBER(38,20) FROM fwc"),
            "a typed CTAS converts");
        statement.execute("CREATE OR REPLACE TABLE fwd (f FLOAT DEFAULT 0.1)");
        statement.execute("INSERT INTO fwd (f) VALUES (DEFAULT)");
        assertEquals("0.10000000000000000555", firstText("SELECT f::NUMBER(38,20) FROM fwd"),
            "a column DEFAULT converts");
        statement.execute("DROP TABLE fw");
        statement.execute("DROP TABLE fwc");
        statement.execute("DROP TABLE fwd");
    }

    /** ★ A double casts to an exact number from its exact binary digits. */
    @Test
    public void aDoubleCastsFromItsExactDigits() throws SQLException {
        assertEquals("1234567890123456768", text("(1234567890123456789::FLOAT)::NUMBER(38,0)"));
        assertEquals("0.10000000000000000555", text("(0.1::FLOAT)::NUMBER(38,20)"));
        assertEquals("0.30000000000000004441", text("(0.1::FLOAT * 3)::NUMBER(38,20)"));
        assertEquals("0.66666666666666662966", text("(2::FLOAT / 3)::NUMBER(38,20)"));
        assertEquals("0.333333333333333314829616256247", text("(1::FLOAT / 3)::NUMBER(38,30)"));
        assertEquals("9999999999999999538762658202121142272", text("(1e37::FLOAT)::NUMBER(38,0)"));
        assertEquals("99999999999999997748809823456034029568", text("(1e38::FLOAT)::NUMBER(38,0)"),
            "★ thirty-eight digits, so it fits — the shortest spelling would have been thirty-nine");
        assertEquals("12345678901234567525491324606797053952",
            text("(12345678901234567890123456789012345678::FLOAT)::NUMBER(38,0)"));
        assertEquals("9007199254740992", text("(9007199254740993::FLOAT)::NUMBER(38,0)"));
        assertEquals("1.23456789012346e+18", text("TO_VARCHAR(1234567890123456789::FLOAT)"),
            "the TEXT keeps Snowflake's width; only the exact cast shows the digits");
    }

    /** ★ A FLOAT beside an exact number compares as a double. */
    @Test
    public void aFloatComparesAsADouble() throws SQLException {
        assertTrue(truth("1234567890123456789::FLOAT = 1234567890123456768"));
        assertTrue(truth("1234567890123456789::FLOAT = 1234567890123456700"),
            "both exact sides become the same double");
        assertFalse(truth("1234567890123456789::FLOAT > 1234567890123456700"));
        assertTrue(truth("(1234567890123456789::FLOAT)::NUMBER(38,0) = 1234567890123456768"));
        assertTrue(truth("0.1::FLOAT = 0.1"));
        assertTrue(truth("0.1::FLOAT = 0.10000000000000000555"));
        assertTrue(truth("0.1::FLOAT = 0.1000000000000000055511151231257827"));
        assertFalse(truth("0.1::FLOAT > 0.1"));
        assertFalse(truth("0.1::FLOAT + 0.2::FLOAT = 0.3::FLOAT"), "double arithmetic, as everywhere");
        assertTrue(truth("1e18::FLOAT + 1 = 1e18::FLOAT"));
    }

    /**
     * ★ A sum of doubles is compensated, which naive left-to-right addition cannot reproduce: seven
     * tenths, ten, eleven and a hundred all come out as the account has them, and ten tenths EQUAL one.
     * Six tenths are the one measured count the account adds the naive way when read from a table, and
     * the compensated way when generated in place — its answer depends on the plan — so six is left out.
     */
    @Test
    public void aSumOfDoublesIsCompensated() throws SQLException {
        final String[][] tenths = {
            {"3", "0.30000000000000004441"}, {"7", "0.70000000000000006661"},
            {"10", "1.00000000000000000000"}, {"11", "1.10000000000000008882"},
            {"100", "10.00000000000000000000"}};
        for (final String[] cell : tenths) {
            statement.execute("CREATE OR REPLACE TABLE fs (g INT, f FLOAT)");
            statement.execute("INSERT INTO fs SELECT 1, 0.1::FLOAT FROM TABLE(GENERATOR(ROWCOUNT => "
                + cell[0] + "))");
            assertEquals(cell[1], firstText("SELECT SUM(f)::NUMBER(38,20) FROM fs"), cell[0] + " tenths");
            assertEquals(cell[1], firstText("SELECT SUM(f)::NUMBER(38,20) FROM fs GROUP BY g"),
                cell[0] + " tenths, grouped");
            assertEquals(cell[1], firstText("SELECT MAX(w)::NUMBER(38,20) FROM (SELECT SUM(f) OVER () AS w FROM fs)"),
                cell[0] + " tenths, windowed");
        }
        assertEquals("10", firstText("SELECT TO_VARCHAR(SUM(f)) FROM fs"),
            "and a hundred tenths print as the whole number they sum to");
        statement.execute("CREATE OR REPLACE TABLE fs (g INT, f FLOAT)");
        statement.execute("INSERT INTO fs SELECT 1, 0.1::FLOAT FROM TABLE(GENERATOR(ROWCOUNT => 10))");
        assertTrue(truthOf("SELECT SUM(f) = 1 FROM fs"), "★ ten tenths are exactly one");
        statement.execute("CREATE OR REPLACE TABLE fs (g INT, f FLOAT)");
        statement.execute("INSERT INTO fs SELECT 1, 0.1::FLOAT FROM TABLE(GENERATOR(ROWCOUNT => 3))");
        assertEquals("0.10000000000000001943", firstText("SELECT AVG(f)::NUMBER(38,20) FROM fs"),
            "AVG is the compensated sum over the count");
        statement.execute("CREATE OR REPLACE TABLE fs (g INT, f FLOAT)");
        statement.execute("INSERT INTO fs SELECT 1, 0.1::FLOAT FROM TABLE(GENERATOR(ROWCOUNT => 7))");
        assertEquals("0.10000000000000000555", firstText("SELECT AVG(f)::NUMBER(38,20) FROM fs"));
        statement.execute("DROP TABLE fs");
    }

    /** getObject's CLASS follows the declared family on both drivers. */
    @Test
    public void getObjectFollowsTheDeclaredFamily() throws SQLException {
        assertEquals(Double.valueOf(3.0), firstObject("SELECT 3.0::FLOAT AS v"));
        assertEquals(Double.valueOf(1.0E18), firstObject("SELECT 1e18::FLOAT AS v"));
        assertEquals(Double.valueOf(0.1), firstObject("SELECT 0.1::FLOAT AS v"));
        assertTrue(firstObject("SELECT 1234567890123456789::FLOAT AS v") instanceof Double);
        assertTrue(firstObject("SELECT SQRT(2) AS v") instanceof Double);
        assertTrue(firstObject("SELECT -SQRT(2) AS v") instanceof Double, "negation keeps the carrier");
        assertTrue(firstObject("SELECT ABS(-1.5::FLOAT) AS v") instanceof Double);
        assertEquals(new BigDecimal("1.50"), firstObject("SELECT 1.50::NUMBER(10,2) AS v"),
            "an exact number is still exact");
    }
}
