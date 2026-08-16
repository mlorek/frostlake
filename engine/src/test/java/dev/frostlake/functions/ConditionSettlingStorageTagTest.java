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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which conditions the planner settles from the statistics when it tags a conditional, and how a
 * COALESCE's own nullability reads — both stricter than the intervals alone would allow. Live-verified:
 *
 * <ul>
 *   <li>a comparison is settled only over operands that cannot be NULL: {@code IFF(n > 5, 3000.5, 1.5)}
 *       is tagged by the 1.5 alone over a column holding 0.50 and 1.00, and by both branches once the
 *       column holds a NULL too, though a NULL condition takes the ELSE;</li>
 *   <li>a COALESCE whose first argument cannot be NULL is that argument; any other the planner keeps as a
 *       call, which settles no condition however narrow its interval, may answer NULL unless its first or
 *       last argument cannot, and still has its interval for the tag, LEAST, GREATEST and DECODE;</li>
 *   <li>NOT, AND, OR, BETWEEN and an IN list combine settled parts as three-valued logic;</li>
 *   <li>a DECODE search and a simple CASE value are matched: never where the intervals are disjoint,
 *       whatever NULLs the subject holds, and always only where neither side can be NULL.</li>
 * </ul>
 *
 * <p>Frostlake used to settle every comparison its intervals decided, and read a COALESCE as never NULL
 * once any argument was.
 */
public class ConditionSettlingStorageTagTest extends BaseDatabaseTest {

    /** A conditional over the 1.5 and the 3000.5, settled on the 1.5: NUMBER(5,1) holding 15. */
    private static final String SETTLED = "NUMBER(5,1)[SB1]";

    /** The same conditional left open: both branches, the 3000.5 holding 30005. */
    private static final String OPEN = "NUMBER(5,1)[SB2]";

    @Override
    protected void setupTest() {
        for (final String name : new String[] {"vf2", "vf3"}) {
            engine.execute("CREATE OR REPLACE TABLE " + name + " (n NUMBER(10,2), t VARCHAR(10), z NUMBER(10,2))");
        }
        engine.execute("INSERT INTO vf2 VALUES (0.50, '1.5', 0), (1.00, '2.5', 1.00)");
        engine.execute("INSERT INTO vf3 VALUES (0.50, '1.5', 0), (NULL, NULL, NULL), (1.00, '2.5', 1.00)");
        engine.execute("CREATE OR REPLACE TABLE vfn (n NUMBER(10,2) NOT NULL, z NUMBER(10,2))");
        engine.execute("INSERT INTO vfn VALUES (0.50, 0), (1.00, NULL)");
        engine.execute("CREATE OR REPLACE TABLE v1n (n NUMBER(10,2))");
        engine.execute("INSERT INTO v1n VALUES (1.00), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE v1 (n NUMBER(10,2))");
        engine.execute("INSERT INTO v1 VALUES (1.00), (1.00)");
        engine.execute("CREATE OR REPLACE TABLE vw (n NUMBER(10,2), w NUMBER(10,2))");
        engine.execute("INSERT INTO vw VALUES (0.50, 3000.50), (1.00, NULL)");
    }

    /** Every row's first cell, joined, or the refusal on one line. */
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

    /** The tag of {@code expression} over {@code from}, on its first row. */
    private String typeOf(final String expression, final String from) {
        return cells("SELECT SYSTEM$TYPEOF(" + expression + ") FROM " + from + " LIMIT 1");
    }

    /** ★ A comparison over an operand that may be NULL is left to the rows. */
    @Test
    public void aConditionOverAPossiblyNullOperandStaysOpen() {
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(n > 5, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n > 5, 3000.5, n)", "vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n > 5, 3000.5, n)", "vfn"), "a NOT NULL column settles it");
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(n < 5, n, 3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n < 5, n, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(n + 1 > 5, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n + 1 > 5, 3000.5, n)", "vf2"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(NULLIF(n, 0) > 5, 3000.5, n)", "vf2"), "NULLIF may answer NULL");
        assertEquals(OPEN, typeOf("IFF(n > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(n > 5, 3000.5, 1.5)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(5 < n, 3000.5, 1.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(5 < n, 3000.5, 1.5)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(n <> 7, 1.5, 3000.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(n <> 7, 1.5, 3000.5)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(NULLIFZERO(n) > 5, 3000.5, 1.5)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(GREATEST(n, 0) > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(z > 5, 3000.5, 1.5)", "vfn"));
        assertEquals(OPEN, typeOf("IFF(n = 1, 1.5, 3000.5)", "v1n"));
        assertEquals(OPEN, typeOf("IFF(n = 7, 3000.5, 1.5)", "v1n"), "even where no value could match");
        assertEquals(OPEN, typeOf("CASE WHEN n > 5 THEN 3000.5 ELSE 1.5 END", "vf3"));
        assertEquals(SETTLED, typeOf("CASE WHEN n > 5 THEN 3000.5 ELSE 1.5 END", "vf2"));
        assertEquals(OPEN, typeOf("CASE WHEN n > 5 THEN 3000.5 WHEN n < 6 THEN 1.5 END", "vf3"));
        assertEquals(OPEN, typeOf("CASE WHEN n < 6 THEN 1.5 ELSE 3000.5 END", "vf3"));
        assertEquals(SETTLED, typeOf("CASE WHEN n = 1 THEN 1.5 ELSE 3000.5 END", "v1"));
        assertEquals(OPEN, typeOf("CASE WHEN n = 1 THEN 1.5 ELSE 3000.5 END", "v1n"));
        assertEquals(OPEN, typeOf("IFF(n IS NULL, 3000.5, 1.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(n IS NULL, 3000.5, 1.5)", "vfn"));
        assertEquals(OPEN, typeOf("IFF(z IS NULL, 3000.5, 1.5)", "vfn"));
        assertEquals(OPEN, typeOf("IFF(x > 5, 3000.5, 1.5)", "(SELECT n AS x FROM vf3)"));
        assertEquals(SETTLED, typeOf("IFF(x > 5, 3000.5, 1.5)", "(SELECT n AS x FROM vf2)"));
        assertEquals(SETTLED, cells("SELECT SYSTEM$TYPEOF(IFF(MAX(n) > 5, 3000.5, 1.5)) FROM vf3"));
        assertEquals(SETTLED, cells("SELECT SYSTEM$TYPEOF(IFF(MIN(n) > 5, 3000.5, 1.5)) FROM vf3"));
        assertEquals(SETTLED, cells("SELECT SYSTEM$TYPEOF(IFF(COUNT(n) > 5, 3000.5, 1.5)) FROM vf3"));
        assertEquals(SETTLED, cells("SELECT SYSTEM$TYPEOF(IFF(MAX(n) IS NULL, 3000.5, 1.5)) FROM vf3"));
    }

    /** ★ A COALESCE the planner keeps as a call settles no condition, however narrow its interval. */
    @Test
    public void aCoalesceKeptAsACallSettlesNoCondition() {
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(COALESCE(n, 0) > 5, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(COALESCE(n, 0) > 5, 3000.5, n)", "vf2"),
            "over a column holding no NULL the call is n itself");
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(COALESCE(n, 0) < 5, n, 3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(NVL(n, 0) > 5, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(IFNULL(n, 0) > 5, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(ZEROIFNULL(n) > 5, 3000.5, n)", "vf3"), "ZEROIFNULL is no COALESCE");
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(COALESCE(n, 0) IS NULL, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(COALESCE(n, 0) = 7, 3000.5, n)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("CASE WHEN COALESCE(n, 0) > 5 THEN 3000.5 ELSE n END", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("IFF(x > 5, 3000.5, n)", "(SELECT COALESCE(n, 0) AS x, n FROM vf3)"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(COALESCE(n, z) > 5, 3000.5, n)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(COALESCE(n, 0) > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(COALESCE(n, 0) > 5, 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(COALESCE(n, 0) > 5, 3000.5, 1.5)", "vfn"));
        assertEquals(OPEN, typeOf("IFF(COALESCE(n, 0) + 0 > 5, 3000.5, 1.5)", "vf3"), "nor a value computed from one");
        assertEquals(OPEN, typeOf("IFF(ABS(COALESCE(n, 0)) > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(LEAST(COALESCE(n, 0), 3000.5) > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(COALESCE(n, 0) BETWEEN 5 AND 6, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(COALESCE(n, 0) IS NOT NULL, 1.5, 3000.5)", "vf3"));
        assertEquals(OPEN, typeOf("NVL2(COALESCE(n, 0), 1.5, 3000.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(x > 5, 3000.5, 1.5)", "(SELECT COALESCE(n, 0) AS x FROM vf3)"));
        assertEquals(SETTLED, typeOf("IFF(x > 5, 3000.5, 1.5)", "(SELECT COALESCE(n, 0) AS x FROM vf2)"));
        assertEquals(SETTLED, typeOf("IFF(COALESCE(0, n) > 5, 3000.5, 1.5)", "vf3"), "a first argument never NULL");
        assertEquals(SETTLED, typeOf("IFF(COALESCE(1.5, n) > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(COALESCE(ZEROIFNULL(n), 0) > 5, 3000.5, 1.5)", "vf3"));
        assertEquals(SETTLED, typeOf("IFF(COALESCE(n + 0, 0) > 5, 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(COALESCE(z, 0) > 5, 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(ZEROIFNULL(n) IS NOT NULL, 1.5, 3000.5)", "vf3"));
        assertEquals(SETTLED, typeOf("NVL2(ZEROIFNULL(n), 1.5, 3000.5)", "vf3"));
        assertEquals(SETTLED, typeOf("NVL2(n, 1.5, 3000.5)", "vf2"));
        assertEquals(OPEN, cells("SELECT SYSTEM$TYPEOF(IFF(COALESCE(MAX(n), 0) > 5, 3000.5, 1.5)) FROM vf2"),
            "an aggregate other than COUNT never reduces the call");
        assertEquals(OPEN, cells("SELECT SYSTEM$TYPEOF(IFF(COALESCE(MIN(n), 0) > 5, 3000.5, 1.5)) FROM vf2"));
        assertEquals(OPEN, cells("SELECT SYSTEM$TYPEOF(IFF(NVL(MAX(n), 0) > 5, 3000.5, 1.5)) FROM vf2"));
        assertEquals(OPEN, cells("SELECT SYSTEM$TYPEOF(IFF(COALESCE(MAX(n), 0) > 5, 3000.5, 1.5)) FROM vf3"));
        assertEquals("NUMBER(5,1)[SB2]", cells("SELECT SYSTEM$TYPEOF(IFF(COALESCE(MAX(n), 0) > 5, 3000.5, 1)) FROM vf3"));
        assertEquals(SETTLED, cells("SELECT SYSTEM$TYPEOF(IFF(COALESCE(COUNT(n), 0) > 5, 3000.5, 1.5)) FROM vf2"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("LEAST(COALESCE(n, 0), 3000.5)", "vf3"), "its interval still stands");
        assertEquals("NUMBER(10,2)[SB1]", typeOf("GREATEST(COALESCE(n, 0), -3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("DECODE(COALESCE(n, 0), 7, 3000.5, n)", "vf3"));
        assertEquals(SETTLED, typeOf("CASE COALESCE(n, 0) WHEN 7 THEN 3000.5 ELSE 1.5 END", "vf3"));
    }

    /** ★ A COALESCE kept as a call may answer NULL unless its first or its last argument cannot. */
    @Test
    public void aCoalesceMayBeNullUnlessItsFirstOrLastArgumentCannot() {
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(n, 0) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(0, n) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB4]", typeOf("COALESCE(COALESCE(n, 0.5, z) + 0, 3000.5)", "vf3"),
            "the 0.5 reached first does not make it never NULL");
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(n, 0.5, 0) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(n, z, 0) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(0.5, n, z) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB4]", typeOf("COALESCE(COALESCE(n, 0.5, NULL) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB4]", typeOf("COALESCE(COALESCE(n, z) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(n, 0.5, z, 0) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(NULL, 0.5, z) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(COALESCE(n, 0.5, z) + 0, 3000.5)", "vf2"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(COALESCE(n, 0.5, z), 3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(COALESCE(n, 0), 3000.5)", "vf3"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(x, 3000.5)", "(SELECT COALESCE(n, 0.5, z) AS x FROM vf3)"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(x, 3000.5)", "(SELECT COALESCE(n, 0) AS x FROM vf3)"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(x + 0, 3000.5)", "(SELECT COALESCE(n, 0) AS x FROM vf3)"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(NVL(n, 0.5) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", typeOf("COALESCE(ZEROIFNULL(n) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB4]", typeOf("COALESCE(IFF(n IS NULL, 0, n) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB4]", typeOf("COALESCE(NVL2(n, n, 0) + 0, 3000.5)", "vf3"));
        assertEquals("NUMBER(11,2)[SB1]", cells("SELECT SYSTEM$TYPEOF(COALESCE(MAX(n) + 0, 3000.5)) FROM vf3"));
        assertEquals("NUMBER(11,2)[SB1]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(COALESCE(MAX(n), 0) + 0, 3000.5)) FROM vf3"));
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n, 0.5, w)", "vw"), "the interval stops at the 0.5");
        assertEquals("NUMBER(10,2)[SB1]", typeOf("COALESCE(n, w)", "vw"));
        assertEquals("NUMBER(10,2)[SB4]", typeOf("COALESCE(w, n)", "vw"));
    }

    /** NOT, AND, OR, BETWEEN and an IN list settle as three-valued logic: one FALSE settles an AND. */
    @Test
    public void connectivesSettleAsThreeValuedLogic() {
        assertEquals("NUMBER(10,2)[SB1]", typeOf("IFF(n > 5 AND n > 6, 3000.5, n)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n > 5 OR n > 6, 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n > 5 OR n < 6, 1.5, 3000.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(NOT (n > 5), 1.5, 3000.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n > 5 AND n = z, 3000.5, 1.5)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(n < 5 AND n = z, 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n < 5 OR n = z, 1.5, 3000.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n BETWEEN 5 AND 6, 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n IN (5, 6), 3000.5, 1.5)", "vf2"));
        assertEquals(SETTLED, typeOf("IFF(n > z + 5, 3000.5, 1.5)", "vf2"));
        assertEquals(OPEN, typeOf("IFF(n > 5 AND n > 6, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(n > 5 OR n > 6, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("IFF(n < 5 AND n < 6, 1.5, 3000.5)", "vf3"));
    }

    /** ★ A simple CASE value and a DECODE search are matched rather than decided. */
    @Test
    public void aSimpleCaseAndDecodeMatchRatherThanDecide() {
        assertEquals(SETTLED, typeOf("CASE n WHEN 7 THEN 3000.5 ELSE 1.5 END", "vf3"), "a NULL subject matches no value");
        assertEquals(SETTLED, typeOf("CASE n WHEN 7 THEN 3000.5 ELSE 1.5 END", "vf2"));
        assertEquals(SETTLED, typeOf("DECODE(n, 7, 3000.5, 1.5)", "vf3"));
        assertEquals(OPEN, typeOf("CASE n WHEN 1 THEN 1.5 ELSE 3000.5 END", "v1n"), "a NULL row takes the ELSE");
        assertEquals(SETTLED, typeOf("CASE n WHEN 1 THEN 1.5 ELSE 3000.5 END", "v1"));
        assertEquals(OPEN, typeOf("DECODE(n, 1, 1.5, 3000.5)", "v1n"));
        assertEquals(SETTLED, typeOf("DECODE(n, 1, 1.5, 3000.5)", "v1"));
        assertEquals(SETTLED, typeOf("DECODE(n, 7, 3000.5, NULL, 3000.5, 1.5)", "vf2"),
            "a NULL search matches no row of a column holding none");
        assertEquals(OPEN, typeOf("DECODE(n, 7, 3000.5, NULL, 3000.5, 1.5)", "vf3"));
    }

    /**
     * A string literal projected through a derived table beside a column holding no NULL: the COALESCE
     * stops at the column, and the literal, read as text, carries no interval of its own where it is reached.
     */
    @Test
    public void aDerivedLiteralBesideANeverNullColumnIsTaggedByTheColumn() {
        engine.execute("CREATE OR REPLACE TABLE cf (n NUMBER(10,2))");
        engine.execute("INSERT INTO cf VALUES (1.5)");
        engine.execute("CREATE OR REPLACE TABLE cf2 (n NUMBER(10,2))");
        engine.execute("INSERT INTO cf2 VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE cfn (n NUMBER(10,2))");
        engine.execute("INSERT INTO cfn VALUES (1.5), (NULL)");
        assertEquals("NUMBER(10,2)[SB2]", cells("SELECT SYSTEM$TYPEOF(COALESCE(n, t)) FROM (SELECT '12.5' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB2]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(n, t)) FROM (SELECT '12.5' AS t, n FROM cf2) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB2]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(n, t)) FROM (SELECT '3000.5' AS t, n FROM cf2) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB8]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(n, t)) FROM (SELECT '3000.5' AS t, n FROM cfn) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB8]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(n, t)) FROM (SELECT '12.5' AS t, n FROM cfn) LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB8]", cells("SELECT SYSTEM$TYPEOF(COALESCE(t, n)) FROM (SELECT '12.5' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB2]", cells("SELECT SYSTEM$TYPEOF(NVL(n, t)) FROM (SELECT '12.5' AS t, n FROM cf)"));
        assertEquals("NUMBER(10,2)[SB2]", cells("WITH c AS (SELECT '12.5' AS t, n FROM cf) SELECT SYSTEM$TYPEOF(COALESCE(n, t)) FROM c"));
        assertEquals("NUMBER(10,2)[SB2]", cells("SELECT SYSTEM$TYPEOF(COALESCE(n, '12.5')) FROM cf"));
        assertEquals("NUMBER(10,2)[SB4]", cells("SELECT SYSTEM$TYPEOF(COALESCE(n, '3000.5')) FROM cfn LIMIT 1"),
            "a literal written in place is its own value");
        assertEquals("1.50", cells("SELECT COALESCE(n, t) FROM (SELECT '12.5' AS t, n FROM cf)"));
    }
}
