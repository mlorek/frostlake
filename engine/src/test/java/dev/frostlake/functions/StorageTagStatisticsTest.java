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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The storage tag {@code SYSTEM$TYPEOF} prints after an exact NUMBER is the plan's, not the row's:
 * a column is tagged by its STATISTICS — the widest value anywhere in the table, on every row alike,
 * whatever a WHERE keeps — and an expression by the interval those statistics propagate through it.
 * Live-verified, shape by shape:
 *
 * <ul>
 *   <li>the bounds are signed, so -128 is SB1 and -129 is SB2;</li>
 *   <li>arithmetic follows the interval; a division is as wide as its integer intermediate or its
 *       divisor, whichever is wider; a cast between NUMBERs keeps the interval at the new scale;</li>
 *   <li>a conditional unites the branches the statistics cannot rule out — a WHEN they settle ends
 *       the walk, and LEAST drops an argument that can never win;</li>
 *   <li>SUM and AVG multiply the interval by a trillion (SUM over 9,200,000 is SB8, over 9,300,000
 *       SB16), MAX and MIN narrow it to one end, COUNT runs from zero to the row count, a window AVG
 *       and a VARIANCE are always sixteen bytes wide;</li>
 *   <li>where no interval can be known — ROUND, CEIL, TRUNC, LENGTH, a cast from text or FLOAT,
 *       ROW_NUMBER, a DISTINCT count — the tag is the declared width's own;</li>
 *   <li>a derived relation, a CTE, a view, a set operation and a grouped query carry the interval
 *       through.</li>
 * </ul>
 *
 * <p>Frostlake used to tag the row's own value, so the two rows of one column printed two tags.
 *
 * <p>NOT COVERED: a scalar subquery, a VALUES clause and FLATTEN's INDEX, which Frostlake does not type.
 */
public class StorageTagStatisticsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE st (n4_0 NUMBER(4,0), n10_2 NUMBER(10,2),"
            + " n38_12 NUMBER(38,12), s VARCHAR(10))");
        engine.execute("INSERT INTO st SELECT 1, 1.5, 1.5, '1'");
        engine.execute("INSERT INTO st SELECT 9999, 99999999.99, 100000000000000000000, '9999'");
        engine.execute("CREATE OR REPLACE TABLE s1 (n4_0 NUMBER(4,0), n10_2 NUMBER(10,2),"
            + " n38_12 NUMBER(38,12), s VARCHAR(10))");
        engine.execute("INSERT INTO s1 SELECT 1, 1.5, 1.5, '1'");
        engine.execute("CREATE OR REPLACE TABLE neg (a NUMBER(5,0), b NUMBER(5,0), c NUMBER(5,0))");
        engine.execute("INSERT INTO neg SELECT -128, -129, -32768");
        engine.execute("CREATE OR REPLACE TABLE mixed (a NUMBER(5,0))");
        engine.execute("INSERT INTO mixed SELECT -100 UNION ALL SELECT 200");
    }

    /** Every row's cell, joined, or the refusal on one line. */
    private String cells(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                out.append(String.valueOf(rs.getValue(0)));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    private String typeOf(final String expression, final String table) {
        return cells("SELECT SYSTEM$TYPEOF(" + expression + ") FROM " + table + " LIMIT 1");
    }

    private String typeOfLiteral(final String expression) {
        return cells("SELECT SYSTEM$TYPEOF(" + expression + ")");
    }

    /** ★ The tag is the table's, on every row — a WHERE keeps the statistics it filters. */
    @Test
    public void aColumnIsTaggedByItsStatisticsOnEveryRow() {
        assertEquals("NUMBER(4,0)[SB2] | NUMBER(4,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(n4_0) FROM st"));
        assertEquals("NUMBER(4,0)[SB1]", typeOf("n4_0", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("n10_2", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("n10_2", "s1"));
        assertEquals("NUMBER(38,12)[SB16]", typeOf("n38_12", "st"));
        assertEquals("NUMBER(38,12)[SB8]", typeOf("n38_12", "s1"));
        assertEquals("NUMBER(4,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(n4_0) FROM st WHERE n4_0 = 1"),
            "the filtered row is still tagged by the whole table");
        engine.execute("CREATE OR REPLACE TABLE st3 AS SELECT * FROM st");
        engine.execute("DELETE FROM st3 WHERE n4_0 = 9999");
        assertEquals("NUMBER(4,0)[SB1]", typeOf("n4_0", "st3"), "and the statistics follow a delete");
        engine.execute("CREATE OR REPLACE TABLE st4 AS SELECT * FROM st");
        engine.execute("INSERT INTO st4 SELECT NULL, NULL, NULL, NULL");
        assertEquals("NUMBER(4,0)[SB2]", typeOf("n4_0", "st4"), "a NULL row widens nothing");
        engine.execute("CREATE OR REPLACE TABLE nul (n NUMBER(10,2))");
        engine.execute("INSERT INTO nul SELECT NULL");
        assertEquals("NUMBER(10,2)[SB1]", typeOf("n", "nul"));
        assertEquals("NUMBER(22,2)[SB1]", typeOf("SUM(n)", "nul"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("-n4_0", "st"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("ABS(n4_0)", "st"));
    }

    /** ★ Signed bounds: a byte holds -128. */
    @Test
    public void theBoundsAreSigned() {
        assertEquals("NUMBER(5,0)[SB1]", typeOf("a", "neg"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("b", "neg"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("c", "neg"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("-a", "neg"), "and 128 does not fit one");
        assertEquals("NUMBER(5,0)[SB2]", typeOf("ABS(a)", "neg"));
        assertEquals("NUMBER(3,0)[SB1]", typeOfLiteral("-128"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("a", "mixed"));
        assertEquals("NUMBER(5,0)[SB1]", typeOf("MIN(a)", "mixed"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("MAX(a)", "mixed"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("-a", "mixed"));
        assertEquals("NUMBER(10,0)[SB4]", typeOf("a * a", "mixed"), "the corners of the product");
        assertEquals("NUMBER(8,0)[SB4]", typeOf("a * -300", "mixed"));
    }

    /** ★ Interval arithmetic, and a division as wide as its integer intermediate or its divisor. */
    @Test
    public void arithmeticFollowsTheInterval() {
        assertEquals("NUMBER(5,0)[SB2]", typeOf("n4_0 + 1", "st"));
        assertEquals("NUMBER(5,0)[SB1]", typeOf("n4_0 + 1", "s1"));
        assertEquals("NUMBER(8,0)[SB4]", typeOf("n4_0 + 1000000", "s1"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("n4_0 * 2", "st"));
        assertEquals("NUMBER(5,0)[SB1]", typeOf("n4_0 * 2", "s1"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("n4_0 + n4_0", "st"));
        assertEquals("NUMBER(20,4)[SB16]", typeOf("n10_2 * n10_2", "st"));
        assertEquals("NUMBER(20,4)[SB2]", typeOf("n10_2 * n10_2", "s1"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("n10_2 - n10_2", "s1"));
        assertEquals("NUMBER(10,6)[SB8]", typeOf("n4_0 / 2", "st"));
        assertEquals("NUMBER(10,6)[SB4]", typeOf("n4_0 / 2", "s1"));
        assertEquals("NUMBER(10,6)[SB8]", typeOf("10 / n10_2", "st"), "the divisor's width when wider");
        assertEquals("NUMBER(10,6)[SB4]", typeOf("10 / n10_2", "s1"));
        assertEquals("NUMBER(20,6)[SB16]", typeOf("10 / n38_12", "s1"),
            "ten shifted eighteen places is the intermediate");
        assertEquals("NUMBER(17,8)[SB4]", typeOf("n10_2 / 0.5", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("n10_2 % 3", "st"), "a remainder keeps its dividend's");
        assertEquals("NUMBER(2,0)[SB8]", typeOf("SIGN(n10_2)", "st"), "SIGN keeps its argument's width");
        assertEquals("NUMBER(2,0)[SB2]", typeOf("SIGN(n10_2)", "s1"));
        assertEquals("NUMBER(38,0)[SB4]", typeOf("n10_2::NUMBER(38,0)", "st"), "a cast rescales the interval");
        assertEquals("NUMBER(38,0)[SB1]", typeOf("n10_2::NUMBER(38,0)", "s1"));
        assertEquals("NUMBER(5,2)[SB8]", typeOf("n10_2::NUMBER(5,2)", "st"));
        assertEquals("NUMBER(5,2)[SB2]", typeOf("n10_2::NUMBER(5,2)", "s1"));
        assertEquals("NUMBER(3,0)[SB16]", typeOf("n38_12::NUMBER(3,0)", "st"),
            "an interval the target cannot hold is not checked here");
        assertEquals("NUMBER(3,0)[SB1]", typeOf("n38_12::NUMBER(3,0)", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("TO_NUMBER(n10_2, 10, 2)", "st"));
        assertEquals("NUMBER(38,0)[SB2]", typeOf("CAST(n4_0 AS INTEGER)", "st"));
    }

    /** ★ A conditional unites what the statistics cannot rule out. */
    @Test
    public void conditionalsUniteTheBranchesTheStatisticsAllow() {
        assertEquals("NUMBER(10,2)[SB8]", typeOf("COALESCE(n10_2, 0)", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("COALESCE(n10_2, 0)", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("NVL(n10_2, 0)", "st"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("IFF(TRUE, n4_0, n4_0)", "st"));
        assertEquals("NUMBER(7,0)[SB4]", typeOf("CASE WHEN n4_0 > 1 THEN n4_0 ELSE 1000000 END", "st"));
        assertEquals("NUMBER(7,0)[SB4]", typeOf("CASE WHEN n4_0 > 1 THEN n4_0 ELSE 1000000 END", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("CASE WHEN n10_2 > 1 THEN n10_2 ELSE 1000000 END", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("CASE WHEN n10_2 > 1 THEN n10_2 ELSE 1000000 END", "s1"),
            "a WHEN the statistics settle ends the walk");
        assertEquals("NUMBER(4,0)[SB2]", typeOf("DECODE(n4_0, 1, n4_0, 0)", "st"));
        assertEquals("NUMBER(4,0)[SB1]", typeOf("DECODE(n4_0, 1, n4_0, 0)", "s1"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("DECODE(n10_2, 1, n10_2, 0)", "st"),
            "a search the statistics rule out contributes nothing");
        assertEquals("NUMBER(38,12)[SB1]", typeOf("DECODE(n38_12, 1, n38_12, 0)", "s1"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("LEAST(n4_0, 1)", "st"), "a tie at the boundary keeps both");
        assertEquals("NUMBER(4,0)[SB1]", typeOf("LEAST(n4_0, 1)", "s1"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("LEAST(n10_2, 1)", "st"), "the literal alone when it always wins");
        assertEquals("NUMBER(38,12)[SB8]", typeOf("LEAST(n38_12, 1)", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("GREATEST(n10_2, 1)", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("GREATEST(n10_2, 1)", "s1"));
        assertEquals("NUMBER(38,12)[SB8]", typeOf("ZEROIFNULL(n38_12)", "s1"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("NULLIF(n10_2, 0)", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("NULLIFZERO(n10_2)", "s1"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("NVL2(n10_2, n10_2, 0)", "s1"));
    }

    /** ★ A trillion rows for SUM and AVG, one end for MIN and MAX, the row count for COUNT. */
    @Test
    public void aggregatesScaleTheIntervalByATrillion() {
        assertEquals("NUMBER(16,0)[SB8]", typeOf("SUM(n4_0)", "st"));
        assertEquals("NUMBER(16,0)[SB8]", typeOf("SUM(n4_0)", "s1"));
        assertEquals("NUMBER(22,2)[SB16]", typeOf("SUM(n10_2)", "st"));
        assertEquals("NUMBER(22,2)[SB8]", typeOf("SUM(n10_2)", "s1"));
        assertEquals("NUMBER(38,12)[SB16]", typeOf("SUM(n38_12)", "s1"));
        engine.execute("CREATE OR REPLACE TABLE m_low (n NUMBER(38,0))");
        engine.execute("INSERT INTO m_low SELECT 9200000");
        engine.execute("CREATE OR REPLACE TABLE m_high (n NUMBER(38,0))");
        engine.execute("INSERT INTO m_high SELECT 9300000");
        assertEquals("NUMBER(38,0)[SB8]", typeOf("SUM(n)", "m_low"), "9.2 million trillion fits eight bytes");
        assertEquals("NUMBER(38,0)[SB16]", typeOf("SUM(n)", "m_high"), "9.3 million trillion does not");
        engine.execute("CREATE OR REPLACE TABLE a_low (n NUMBER(38,0))");
        engine.execute("INSERT INTO a_low SELECT 9");
        engine.execute("CREATE OR REPLACE TABLE a_high (n NUMBER(38,0))");
        engine.execute("INSERT INTO a_high SELECT 10");
        assertEquals("NUMBER(38,6)[SB8]", typeOf("AVG(n)", "a_low"), "the same trillion at AVG's scale");
        assertEquals("NUMBER(38,6)[SB16]", typeOf("AVG(n)", "a_high"));
        assertEquals("NUMBER(22,6)[SB16]", typeOf("AVG(n4_0)", "st"));
        assertEquals("NUMBER(22,6)[SB8]", typeOf("AVG(n4_0)", "s1"));
        assertEquals("NUMBER(4,0)[SB1]", typeOf("MIN(n4_0)", "st"), "MIN is the least value's width");
        assertEquals("NUMBER(4,0)[SB2]", typeOf("MAX(n4_0)", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("MIN(n10_2)", "st"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("MAX(n10_2)", "st"));
        assertEquals("NUMBER(18,0)[SB1]", typeOf("COUNT(n4_0)", "st"), "two rows fit a byte");
        assertEquals("NUMBER(18,0)[SB1]", typeOf("COUNT(*)", "st"));
        assertEquals("NUMBER(19,0)[SB1]", typeOf("COUNT(n4_0) + 1", "st"));
        assertEquals("NUMBER(18,0)[SB8]", typeOf("COUNT(DISTINCT n4_0)", "st"), "a DISTINCT count is unbounded");
        assertEquals("NUMBER(7,3)[SB4]", typeOf("MEDIAN(n4_0)", "st"));
        assertEquals("NUMBER(7,3)[SB2]", typeOf("MEDIAN(n4_0)", "s1"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n4_0)", "st"));
        assertEquals("NUMBER(38,6)[SB16]", typeOf("VARIANCE(n4_0)", "s1"), "a variance is always sixteen bytes");
        assertEquals("NUMBER(38,10)[SB16]", typeOf("VAR_POP(n10_2)", "s1"));
        assertEquals("NUMBER(17,0)[SB8]", typeOf("SUM(n4_0) + 1", "st"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("MAX(n4_0) + 1", "st"));
        assertEquals("NUMBER(22,2)[SB16]", typeOf("ABS(SUM(n10_2))", "st"));
        assertEquals("NUMBER(22,2)[SB8]", typeOf("ABS(SUM(n10_2))", "s1"));
        assertEquals("NUMBER(16,0)[SB8]", typeOf("SUM(DISTINCT n4_0)", "st"));
        assertEquals("NUMBER(22,6)[SB16]", typeOf("SUM(n4_0) / COUNT(n4_0)", "st"));
        assertEquals("NUMBER(22,6)[SB8]", typeOf("SUM(n4_0) / COUNT(n4_0)", "s1"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("ANY_VALUE(n4_0)", "st"));
    }

    /** ★ A window SUM is its aggregate's; a window AVG is sixteen bytes; a window MIN keeps the interval. */
    @Test
    public void windowsFollowTheirAggregates() {
        assertEquals("NUMBER(16,0)[SB8]", typeOf("SUM(n4_0) OVER ()", "st"));
        assertEquals("NUMBER(22,2)[SB16]", typeOf("SUM(n10_2) OVER (ORDER BY n10_2)", "st"));
        assertEquals("NUMBER(22,2)[SB8]", typeOf("SUM(n10_2) OVER (ORDER BY n10_2)", "s1"));
        assertEquals("NUMBER(19,3)[SB16]", typeOf("AVG(n4_0) OVER ()", "s1"));
        assertEquals("NUMBER(22,6)[SB16]", typeOf("AVG(n4_0) OVER (ORDER BY n4_0)", "s1"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("MIN(n4_0) OVER ()", "st"), "unlike the aggregate MIN");
        assertEquals("NUMBER(4,0)[SB2]", typeOf("MAX(n4_0) OVER ()", "st"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("LAG(n10_2) OVER (ORDER BY n10_2)", "st"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("FIRST_VALUE(n10_2) OVER (ORDER BY n10_2)", "s1"));
        assertEquals("NUMBER(18,0)[SB8]", typeOf("ROW_NUMBER() OVER (ORDER BY n4_0)", "st"));
        assertEquals("NUMBER(18,0)[SB8]", typeOf("RANK() OVER (ORDER BY n4_0)", "s1"));
        assertEquals("NUMBER(18,0)[SB8]", typeOf("COUNT(*) OVER ()", "st"));
        assertEquals("NUMBER(10,6)[SB8]", typeOf("RATIO_TO_REPORT(n4_0) OVER ()", "st"),
            "as wide as the SUM it divides by");
        assertEquals("NUMBER(10,6)[SB8]", typeOf("RATIO_TO_REPORT(n4_0) OVER ()", "s1"));
        assertEquals("NUMBER(18,8)[SB16]", typeOf("RATIO_TO_REPORT(n10_2) OVER ()", "st"));
        assertEquals("NUMBER(18,8)[SB8]", typeOf("RATIO_TO_REPORT(n10_2) OVER ()", "s1"));
        assertEquals("NUMBER(38,12)[SB16]", typeOf("RATIO_TO_REPORT(n38_12) OVER ()", "s1"));
        assertEquals("NUMBER(7,3)[SB4]", typeOf("MEDIAN(n4_0) OVER ()", "st"));
        assertEquals("NUMBER(16,0)[SB8]", typeOf("SUM(n4_0) OVER (PARTITION BY n4_0)", "s1"));
    }

    /** ★ No interval, the declared width — and a FLOOR over a constant folds. */
    @Test
    public void unknownIntervalsFallBackToTheDeclaredWidth() {
        assertEquals("NUMBER(4,0)[SB2]", typeOf("ROUND(n4_0, 1)", "s1"));
        assertEquals("NUMBER(11,1)[SB8]", typeOf("ROUND(n10_2, 1)", "s1"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("CEIL(n4_0)", "s1"));
        assertEquals("NUMBER(11,0)[SB8]", typeOf("CEIL(n10_2)", "s1"));
        assertEquals("NUMBER(38,0)[SB16]", typeOf("TRUNC(n38_12)", "s1"));
        assertEquals("NUMBER(11,1)[SB8]", typeOf("TRUNCATE(n10_2, 1)", "s1"));
        assertEquals("NUMBER(11,0)[SB1]", typeOf("FLOOR(n10_2)", "s1"), "a constant folds");
        assertEquals("NUMBER(11,0)[SB8]", typeOf("FLOOR(n10_2)", "st"), "an interval does not");
        assertEquals("NUMBER(18,0)[SB8]", typeOfLiteral("LENGTH('abc')"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("n10_2::VARCHAR::NUMBER(10,2)", "s1"), "a cast from text");
        assertEquals("NUMBER(10,2)[SB8]", typeOf("n10_2::FLOAT::NUMBER(10,2)", "s1"), "a cast from a FLOAT");
        assertEquals("NUMBER(38,0)[SB16]", typeOf("TO_NUMBER(s)", "st"));
        assertEquals("NUMBER(10,2)[SB8]", typeOf("TO_NUMBER(s, 10, 2)", "st"));
        assertEquals("NUMBER(38,0)[SB16]", typeOf("TRY_TO_NUMBER(s)", "st"));
        assertEquals("NUMBER(6,1)[SB4]", typeOf("s::NUMBER(6,1)", "st"));
        assertEquals("NUMBER(10,0)[SB8]", typeOfLiteral("PARSE_JSON('100000')::NUMBER(10,0)"));
        assertEquals("NUMBER(19,0)[SB8]", typeOf("HASH(n4_0)", "s1"), "a hash is a 64-bit integer");
        assertEquals("NUMBER(10,0)[SB4]", cells("SELECT SYSTEM$TYPEOF(SEQ4()) FROM TABLE(GENERATOR(ROWCOUNT => 2)) LIMIT 1"));
        assertEquals("NUMBER(19,0)[SB8]", cells("SELECT SYSTEM$TYPEOF(SEQ8()) FROM TABLE(GENERATOR(ROWCOUNT => 2)) LIMIT 1"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("MODE(n4_0)", "s1"));
    }

    /** A literal is its own interval, a NULL none. */
    @Test
    public void literalsAreTheirOwnInterval() {
        assertEquals("NUMBER(3,0)[SB1]", typeOfLiteral("127"));
        assertEquals("NUMBER(3,0)[SB2]", typeOfLiteral("128"));
        assertEquals("NUMBER(5,0)[SB2]", typeOfLiteral("32767"));
        assertEquals("NUMBER(5,0)[SB4]", typeOfLiteral("32768"));
        assertEquals("NUMBER(10,0)[SB4]", typeOfLiteral("2147483647"));
        assertEquals("NUMBER(10,0)[SB8]", typeOfLiteral("2147483648"));
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("9223372036854775807"));
        assertEquals("NUMBER(19,0)[SB16]", typeOfLiteral("9223372036854775808"));
        assertEquals("NUMBER(2,1)[SB1]", typeOfLiteral("1.5"));
        assertEquals("NUMBER(8,3)[SB4]", typeOfLiteral("12345.678"));
        assertEquals("NUMBER(14,0)[SB8]", typeOfLiteral("1000000 * 1000000"));
        assertEquals("NUMBER(7,6)[SB4]", typeOfLiteral("1 / 3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOfLiteral("CAST(1000.00 AS NUMBER(10,2))"));
        assertEquals("NUMBER(10,2)[SB1]", typeOfLiteral("CAST(NULL AS NUMBER(10,2))"));
        assertEquals("NUMBER(38,37)[SB16]", typeOfLiteral("1::NUMBER(38,37)"));
        assertEquals("NUMBER(9,2)[SB4]", typeOfLiteral("ROUND(1234.5678, 2)"));
        assertEquals("NUMBER(4,0)[SB2]", typeOfLiteral("ABS(-1000)"));
    }

    /** ★ A derived relation, a CTE, a view, a set operation and a grouped query carry the interval. */
    @Test
    public void derivedRelationsPropagateTheInterval() {
        assertEquals("NUMBER(4,0)[SB1]", cells("WITH x AS (SELECT n4_0 AS k FROM s1) SELECT SYSTEM$TYPEOF(k) FROM x"));
        assertEquals("NUMBER(4,0)[SB2]",
            cells("WITH x AS (SELECT n4_0 AS k FROM st) SELECT SYSTEM$TYPEOF(k) FROM x LIMIT 1"));
        assertEquals("NUMBER(5,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(k) FROM (SELECT n4_0 * 2 AS k FROM st) LIMIT 1"));
        assertEquals("NUMBER(16,0)[SB8]", cells("SELECT SYSTEM$TYPEOF(k) FROM (SELECT SUM(n4_0) AS k FROM s1)"));
        assertEquals("NUMBER(17,0)[SB8]", cells("SELECT SYSTEM$TYPEOF(k + 1) FROM (SELECT SUM(n4_0) AS k FROM s1)"));
        assertEquals("NUMBER(4,0)[SB2]",
            cells("SELECT SYSTEM$TYPEOF(k) FROM (SELECT n4_0 AS k FROM st WHERE n4_0 = 1)"),
            "the inner WHERE keeps the statistics too");
        assertEquals("NUMBER(4,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(k) FROM"
            + " (SELECT n4_0 AS k FROM s1 UNION ALL SELECT n4_0 FROM st) LIMIT 1"), "the arms unite");
        assertEquals("NUMBER(4,0)[SB1]",
            cells("SELECT SYSTEM$TYPEOF(k) FROM (SELECT n4_0 AS k FROM s1 UNION ALL SELECT 1) LIMIT 1"));
        assertEquals("NUMBER(6,0)[SB4]", cells("SELECT SYSTEM$TYPEOF(k) FROM (SELECT 1 AS k UNION ALL SELECT 100000) LIMIT 1"));
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT n4_0 AS k, n4_0 + 1 AS k1, SUM(n4_0) OVER () AS s FROM st");
        assertEquals("NUMBER(4,0)[SB2]", typeOf("k", "vw"));
        assertEquals("NUMBER(5,0)[SB2]", typeOf("k1", "vw"));
        assertEquals("NUMBER(16,0)[SB8]", typeOf("s", "vw"));
        assertEquals("NUMBER(4,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(n4_0) FROM st GROUP BY n4_0 LIMIT 1"));
        assertEquals("NUMBER(22,2)[SB16]", cells("SELECT SYSTEM$TYPEOF(SUM(n10_2)) FROM st GROUP BY n4_0 LIMIT 1"));
        assertEquals("NUMBER(16,0)[SB8]",
            cells("SELECT SYSTEM$TYPEOF(SUM(n4_0)) FROM st GROUP BY n4_0 HAVING SUM(n4_0) > 0 LIMIT 1"));
        assertEquals("NUMBER(4,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(a.n4_0) FROM st a JOIN s1 b ON TRUE LIMIT 1"));
        assertEquals("NUMBER(4,0)[SB1]", cells("SELECT SYSTEM$TYPEOF(b.n4_0) FROM st a JOIN s1 b ON TRUE LIMIT 1"));
        assertEquals("NUMBER(5,0)[SB2]", cells("SELECT SYSTEM$TYPEOF(a.n4_0 + b.n4_0) FROM st a JOIN s1 b ON TRUE LIMIT 1"));
        assertEquals("NUMBER(16,0)[SB8]", cells("SELECT SYSTEM$TYPEOF(SUM(b.n4_0)) FROM st a JOIN s1 b ON TRUE LIMIT 1"));
        engine.execute("CREATE OR REPLACE TABLE ct AS SELECT n10_2 * 2 AS d, SUM(n10_2) AS s,"
            + " RATIO_TO_REPORT(n10_2) OVER () AS r FROM st GROUP BY n10_2");
        assertEquals("NUMBER(11,2)[SB8]", typeOf("d", "ct"), "a stored result is tagged by what it stores");
        assertEquals("NUMBER(22,2)[SB8]", typeOf("s", "ct"), "not by the SUM's trillion");
        assertEquals("NUMBER(18,8)[SB4]", typeOf("r", "ct"));
    }

    /** ★ RANDOM is a signed 64-bit integer, whatever its nineteen declared digits would need. */
    @Test
    public void randomIsASignedSixtyFourBitInteger() {
        engine.execute("CREATE OR REPLACE TABLE rt (n NUMBER(10,0))");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("RANDOM()"));
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("RANDOM(42)"));
        assertEquals("NUMBER(19,0)[SB8] | NUMBER(19,0)[SB8]", cells("SELECT SYSTEM$TYPEOF(RANDOM()) FROM rt"));
        assertEquals("NUMBER(20,0)[SB16]", typeOfLiteral("RANDOM() + 1"), "one past the 64-bit range");
        assertEquals("NUMBER(20,0)[SB16]", typeOfLiteral("RANDOM() * 2"));
        assertEquals("NUMBER(19,0)[SB16]", typeOfLiteral("-RANDOM()"));
        assertEquals("NUMBER(19,0)[SB16]", typeOfLiteral("ABS(RANDOM())"));
        assertEquals("NUMBER(25,6)[SB16]", typeOfLiteral("RANDOM() / 2"));
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("RANDOM() % 10"));
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("MOD(RANDOM(), 10)"));
        assertEquals("NUMBER(2,0)[SB8]", typeOfLiteral("SIGN(RANDOM())"), "SIGN keeps its argument's width");
        assertEquals("NUMBER(38,0)[SB8]", typeOfLiteral("RANDOM()::NUMBER(38,0)"));
        assertEquals("NUMBER(20,2)[SB16]", typeOfLiteral("RANDOM()::NUMBER(20,2)"));
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("IFF(TRUE, RANDOM(), 1)"));
        assertEquals("NUMBER(19,0)[SB8]", typeOfLiteral("COALESCE(RANDOM(), 1)"));
        assertEquals("NUMBER(19,0)[SB8]", cells("SELECT SYSTEM$TYPEOF(x) FROM (SELECT RANDOM() AS x)"));
    }

    /**
     * ★ A COALESCE stops at an argument the statistics prove never NULL — a literal, a column holding no
     * NULL, arithmetic and casts over those, an aggregate answered from them — and NVL2 and an IS [NOT]
     * NULL condition are settled the same way. Where a NULL may pass, every argument contributes, each at
     * the call's own scale; a pruned literal is read at that scale too, never at its own.
     */
    @Test
    public void aCoalesceStopsAtAnArgumentNeverNull() {
        for (final String name : List.of("vf", "vf2", "vf3")) {
            engine.execute("CREATE OR REPLACE TABLE " + name
                + " (n NUMBER(10,2), t VARCHAR(10), z NUMBER(10,2), n2010 NUMBER(20,10), f FLOAT)");
        }
        engine.execute("INSERT INTO vf VALUES (1.00, '1.5', 0, 1.0, 1.5)");
        engine.execute("INSERT INTO vf2 VALUES (0.50, '1.5', 0, 1.0, 1.5), (1.00, '2.5', 1.00, 2.0, 2.5)");
        engine.execute("""
            INSERT INTO vf3 VALUES (0.50, '1.5', 0, 1.0, 1.5), (NULL, NULL, NULL, NULL, NULL),
                (1.00, '2.5', 1.00, 2.0, 2.5)""");
        engine.execute("CREATE OR REPLACE TABLE vfn (n NUMBER(10,2) NOT NULL, z NUMBER(10,2))");
        engine.execute("INSERT INTO vfn VALUES (0.50, 0), (1.00, NULL)");

        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n, 1.5)", "vf"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n, 3000.5)", "vf"));
        assertEquals("NUMBER(18,5)[SB4]", typeOf("COALESCE(n, t)", "vf"), "n alone, at the call's scale");
        assertEquals("NUMBER(18,5)[SB8]", typeOf("COALESCE(t, n)", "vf"), "a text argument has no interval");
        assertEquals("NUMBER(14,6)[SB1]", typeOf("IFF(TRUE, z, 1/0)", "vf"));
        assertEquals("NUMBER(14,6)[SB4]", typeOf("COALESCE(n, 1/0)", "vf"));

        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(3000.5, n)", "vf2"));
        assertEquals("NUMBER(10,2)[SB2]", typeOf("COALESCE(1.5, n)", "vf2"), "the literal at the call's scale");
        assertEquals("NUMBER(18,5)[SB4]", typeOf("COALESCE(n, t)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("NVL(n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFNULL(n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(z, n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(NULL, n, 3000.5)", "vf2"));
        assertEquals("NUMBER(11,2)[SB2]", typeOf("COALESCE(n + 1, 3000.5)", "vf2"));
        assertEquals("NUMBER(12,4)[SB2]", typeOf("COALESCE(n::NUMBER(12,4), 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(ABS(n), 3000.5)", "vf2"));
        assertEquals("NUMBER(20,4)[SB2]", typeOf("COALESCE(n * z, 3000.5)", "vf2"));
        assertEquals("NUMBER(16,8)[SB4]", typeOf("COALESCE(n / 2, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n % 1, 3000.5)", "vf2"));
        assertEquals("NUMBER(11,1)[SB8]", typeOf("COALESCE(ROUND(n, 1), 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(NULLIF(n, 0), 3000.5)", "vf2"), "NULLIF may be NULL");
        assertEquals("NUMBER(10,2)[SB1]", typeOf("NVL2(n, n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("NVL2(n, 3000.5, n)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n IS NULL, 3000.5, n)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n IS NOT NULL, n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("CASE WHEN n IS NULL THEN 3000.5 ELSE n END", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n, 3000.5)", "vfn"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(z, 3000.5)", "vfn"));

        assertEquals("NUMBER(10,2)[SB2]", typeOf("COALESCE(n, 1.5)", "vf3"), "a NULL lets the 1.5 through");
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(n, 3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(z, n, 3000.5)", "vf3"));
        assertEquals("NUMBER(18,5)[SB8]", typeOf("COALESCE(n, t)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("NVL2(n, n, 3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(n IS NULL, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(11,2)[SB4]", typeOf("COALESCE(n, 3000.5) + 0", "vf3"));

        assertEquals("NUMBER(10,2)[SB1]", cells("SELECT SYSTEM$TYPEOF(COALESCE(MAX(n), 3000.5)) FROM vf3"),
            "MAX over a column holding a value is never NULL");
        assertEquals("NUMBER(10,2)[SB1]", cells("SELECT SYSTEM$TYPEOF(COALESCE(MIN(n), 3000.5)) FROM vf2"));
        assertEquals("NUMBER(19,1)[SB1]", cells("SELECT SYSTEM$TYPEOF(COALESCE(COUNT(n), 3000.5)) FROM vf2"));
        assertEquals("NUMBER(10,2)[SB4] | NUMBER(10,2)[SB4]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(MAX(n), 3000.5)) FROM vf2 GROUP BY z"), "a group may answer NULL");

        assertEquals("NUMBER(10,2)[SB1]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(x, 3000.5)) FROM (SELECT n AS x FROM vf2) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB1]",
            cells("WITH c AS (SELECT n AS x FROM vf2) SELECT SYSTEM$TYPEOF(COALESCE(x, 3000.5)) FROM c LIMIT 1"));
        assertEquals("NUMBER(11,2)[SB2]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(x, 3000.5)) FROM (SELECT n + 1 AS x FROM vf2) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB1]", cells("SELECT SYSTEM$TYPEOF(COALESCE(x, 3000.5)) FROM"
            + " (SELECT n AS x FROM vf2 UNION ALL SELECT n FROM vf2) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB4]", cells("SELECT SYSTEM$TYPEOF(COALESCE(x, 3000.5)) FROM"
            + " (SELECT n AS x FROM vf2 UNION ALL SELECT NULL) LIMIT 1"), "a NULL arm");
        assertEquals("NUMBER(10,2)[SB1]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(x, 3000.5)) FROM (SELECT COALESCE(n, 0) AS x FROM vf3) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB4]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(b.n, 3000.5)) FROM vf2 a LEFT JOIN vf3 b ON a.n = b.n LIMIT 1"),
            "an outer join's NULL-extended side");
    }
}
