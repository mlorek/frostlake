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
 * Over a join a column keeps the nullability its table's statistics prove unless an outer join extends
 * its side with NULLs, so a COALESCE over a kept column that holds no NULL stops at it and the storage tag
 * {@code SYSTEM$TYPEOF} prints is that column's width alone. Live-verified join kind by join kind:
 *
 * <ul>
 *   <li>an inner, comma, cross, NATURAL or USING join extends neither side;</li>
 *   <li>a LEFT join extends its right side, a RIGHT join every relation on its left, a FULL join both,
 *       and an ASOF join and a legacy {@code (+)} join their NULL-supplying side;</li>
 *   <li>an extended side stays extended through a later join and a WHERE, even ones rejecting its NULLs,
 *       and a USING or NATURAL key written bare reads the kept side;</li>
 *   <li>a join's own condition proves nothing: a column holding a NULL keeps it across {@code ON a.n = b.n}.</li>
 * </ul>
 *
 * <p>Frostlake used to treat every join as possibly NULL on both sides.
 */
public class JoinSideStorageTagTest extends BaseDatabaseTest {

    /** COALESCE(column, 3000.5) stopping at the column: its 0.50 to 1.00 alone. */
    private static final String NEVER_NULL = "NUMBER(10,2)[SB1]";

    /** COALESCE(column, 3000.5) reaching the 3000.5 too. */
    private static final String MAY_BE_NULL = "NUMBER(10,2)[SB4]";

    @Override
    protected void setupTest() {
        for (final String name : new String[] {"vf2", "vf2b", "vf2c"}) {
            engine.execute("CREATE OR REPLACE TABLE " + name + " (n NUMBER(10,2), t VARCHAR(10), z NUMBER(10,2))");
            engine.execute("INSERT INTO " + name + " VALUES (0.50, '1.5', 0), (1.00, '2.5', 1.00)");
        }
        engine.execute("CREATE OR REPLACE TABLE vf3 (n NUMBER(10,2), t VARCHAR(10), z NUMBER(10,2))");
        engine.execute("INSERT INTO vf3 VALUES (0.50, '1.5', 0), (NULL, NULL, NULL), (1.00, '2.5', 1.00)");
        engine.execute("CREATE OR REPLACE TABLE vfn (n NUMBER(10,2) NOT NULL, z NUMBER(10,2))");
        engine.execute("INSERT INTO vfn VALUES (0.50, 0), (1.00, NULL)");
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

    /** The tag of {@code COALESCE(column, 3000.5)} over {@code from}, on its first row. */
    private String coalesced(final String column, final String from) {
        return cells("SELECT SYSTEM$TYPEOF(COALESCE(" + column + ", 3000.5)) FROM " + from + " LIMIT 1");
    }

    /** ★ An inner join, in every spelling, extends neither side. */
    @Test
    public void anInnerJoinExtendsNeitherSide() {
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("b.n", "vf2 a JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a INNER JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a, vf2b b"));
        assertEquals(NEVER_NULL, coalesced("b.n", "vf2 a, vf2b b"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a CROSS JOIN vf2b b"));
        assertEquals(NEVER_NULL, coalesced("b.n", "vf2 a CROSS JOIN vf2b b"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a JOIN vf3 b ON a.n = b.n"), "the other side's NULL is its own");
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a, vf3 b"));
        assertEquals(NEVER_NULL, coalesced("b.n", "vf2 a JOIN vfn b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("a.z", "vf2 a NATURAL JOIN vf2b b"));
        assertEquals(NEVER_NULL, coalesced("n", "vf2 a NATURAL JOIN vf2b b"));
        assertEquals(NEVER_NULL, coalesced("a.z", "vf2 a JOIN vf2b b USING (n)"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a JOIN vf2b b ON a.n = b.n JOIN vf2c c ON c.n = b.n"));
        assertEquals("NUMBER(11,2)[SB2]", coalesced("a.n + b.n", "vf2 a JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL,
            cells("SELECT SYSTEM$TYPEOF(NVL2(a.n, a.n, 3000.5)) FROM vf2 a JOIN vf2b b ON a.n = b.n LIMIT 1"));
        assertEquals(NEVER_NULL,
            cells("SELECT SYSTEM$TYPEOF(IFF(a.n IS NULL, 3000.5, a.n)) FROM vf2 a JOIN vf2b b ON a.n = b.n LIMIT 1"));
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf3 a JOIN vf2 b ON a.n = b.n"),
            "a join condition proves no column never NULL");
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf3 a, vf2 b"));
    }

    /** ★ An outer join extends the side it supplies NULLs for, and only that side. */
    @Test
    public void anOuterJoinExtendsTheSideItSuppliesNullsFor() {
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a LEFT JOIN vf2b b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a LEFT JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a LEFT OUTER JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a LEFT JOIN vf3 b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf2 a RIGHT JOIN vf2b b ON a.n = b.n"));
        assertEquals(NEVER_NULL, coalesced("b.n", "vf2 a RIGHT JOIN vf2b b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf2 a FULL JOIN vf2b b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a FULL JOIN vf2b b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf2 a FULL OUTER JOIN vf2b b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a LEFT JOIN vfn b ON a.n = b.n"),
            "a NOT NULL column is extended like any other");
        assertEquals("NUMBER(11,2)[SB4]", coalesced("a.n + b.n", "vf2 a LEFT JOIN vf2b b ON a.n = b.n"));
        assertEquals(MAY_BE_NULL,
            cells("SELECT SYSTEM$TYPEOF(IFF(b.n IS NULL, 3000.5, b.n)) FROM vf2 a LEFT JOIN vf2b b ON a.n = b.n LIMIT 1"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a, vf2b b WHERE a.n = b.n(+)"), "a legacy (+) join");
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a, vf2b b WHERE a.n = b.n(+)"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a ASOF JOIN vf2b b MATCH_CONDITION (a.n >= b.n)"));
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a ASOF JOIN vf2b b MATCH_CONDITION (a.n >= b.n)"));
        assertEquals("3000.50",
            cells("SELECT COALESCE(b.n, 3000.5) FROM vf2 a LEFT JOIN vf2b b ON a.n = b.n + 1 LIMIT 1"),
            "a NULL-extended row takes the fallback");
    }

    /** ★ An extended side stays extended through every later join and filter, a kept side stays kept. */
    @Test
    public void anExtendedSideStaysExtendedThroughLaterJoins() {
        final String leftThenInner = "vf2 a LEFT JOIN vf2b b ON a.n = b.n JOIN vf2c c ON a.n = c.n";
        assertEquals(NEVER_NULL, coalesced("a.n", leftThenInner));
        assertEquals(MAY_BE_NULL, coalesced("b.n", leftThenInner));
        assertEquals(NEVER_NULL, coalesced("c.n", leftThenInner));
        final String innerThenRight = "vf2 a JOIN vf2b b ON a.n = b.n RIGHT JOIN vf2c c ON a.n = c.n";
        assertEquals(MAY_BE_NULL, coalesced("a.n", innerThenRight), "a RIGHT join extends every relation on its left");
        assertEquals(MAY_BE_NULL, coalesced("b.n", innerThenRight));
        assertEquals(NEVER_NULL, coalesced("c.n", innerThenRight));
        final String leftThenComma = "vf2 a LEFT JOIN vf2b b ON a.n = b.n, vf2c c";
        assertEquals(NEVER_NULL, coalesced("a.n", leftThenComma));
        assertEquals(MAY_BE_NULL, coalesced("b.n", leftThenComma));
        assertEquals(NEVER_NULL, coalesced("c.n", leftThenComma));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a LEFT JOIN vf2b b ON a.n = b.n LEFT JOIN vf2c c ON b.n = c.n"));
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf2 a FULL JOIN vf2b b ON a.n = b.n JOIN vf2c c ON a.n = c.n"));
        assertEquals(NEVER_NULL, coalesced("c.n", "vf2 a FULL JOIN vf2b b ON a.n = b.n JOIN vf2c c ON a.n = c.n"));
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a LEFT JOIN vf2b b ON a.n = b.n WHERE b.n > 0"),
            "a filter rejecting the NULLs does not undo the extension");
        assertEquals(MAY_BE_NULL, coalesced("b.n", "vf2 a LEFT JOIN vf2b b ON a.n = b.n JOIN vf2c c ON b.n = c.n"));
    }

    /** A parenthesized join group is extended as a whole by the outer join around it. */
    @Test
    public void aJoinGroupIsExtendedAsAWhole() {
        final String leftOfGroup = "vf2 a LEFT JOIN (vf2b b JOIN vf2c c ON b.n = c.n) ON a.n = b.n";
        assertEquals(NEVER_NULL, coalesced("a.n", leftOfGroup));
        assertEquals(MAY_BE_NULL, coalesced("b.n", leftOfGroup));
        assertEquals(MAY_BE_NULL, coalesced("c.n", leftOfGroup));
        final String outerInGroup = "vf2 a JOIN (vf2b b LEFT JOIN vf2c c ON b.n = c.n) ON a.n = b.n";
        assertEquals(NEVER_NULL, coalesced("a.n", outerInGroup));
        assertEquals(NEVER_NULL, coalesced("b.n", outerInGroup));
        assertEquals(MAY_BE_NULL, coalesced("c.n", outerInGroup));
        final String rightOfGroup = "vf2 a RIGHT JOIN (vf2b b JOIN vf2c c ON b.n = c.n) ON a.n = b.n";
        assertEquals(MAY_BE_NULL, coalesced("a.n", rightOfGroup));
        assertEquals(NEVER_NULL, coalesced("b.n", rightOfGroup));
    }

    /** ★ A USING or NATURAL key written bare reads the side no outer join extends. */
    @Test
    public void aMergedKeyReadsTheKeptSide() {
        assertEquals(NEVER_NULL, coalesced("n", "vf2 a LEFT JOIN vf2b b USING (n)"));
        assertEquals(NEVER_NULL, coalesced("a.z", "vf2 a LEFT JOIN vf2b b USING (n)"));
        assertEquals(MAY_BE_NULL, coalesced("b.z", "vf2 a LEFT JOIN vf2b b USING (n)"));
        assertEquals(NEVER_NULL, coalesced("n", "vf2 a RIGHT JOIN vf2b b USING (n)"), "a RIGHT join's key is its right side's");
        assertEquals(MAY_BE_NULL, coalesced("a.n", "vf2 a RIGHT JOIN vf2b b USING (n)"));
        assertEquals(NEVER_NULL, coalesced("b.n", "vf2 a RIGHT JOIN vf2b b USING (n)"));
        assertEquals(MAY_BE_NULL, coalesced("n", "vf2 a FULL JOIN vf2b b USING (n)"));
        assertEquals(NEVER_NULL, coalesced("a.z", "vf2 a NATURAL LEFT JOIN vf2b b"));
        assertEquals(MAY_BE_NULL, coalesced("b.z", "vf2 a NATURAL LEFT JOIN vf2b b"));
        assertEquals(NEVER_NULL, coalesced("n", "vf2 a NATURAL RIGHT JOIN vf2b b"));
        assertEquals(MAY_BE_NULL, coalesced("n", "vf2 a NATURAL FULL JOIN vf2b b"));
    }

    /** A derived table, a CTE, a view and a LATERAL join by their side and carry what their own join knew. */
    @Test
    public void derivedRelationsJoinByTheirSide() {
        assertEquals(NEVER_NULL, coalesced("a.x", "(SELECT n AS x FROM vf2) a JOIN vf2b b ON a.x = b.n"));
        assertEquals(NEVER_NULL, coalesced("a.x", "(SELECT n AS x FROM vf2) a LEFT JOIN vf2b b ON a.x = b.n"));
        assertEquals(NEVER_NULL, coalesced("b.x", "vf2 a JOIN (SELECT n AS x FROM vf2b) b ON a.n = b.x"));
        assertEquals(MAY_BE_NULL, coalesced("b.x", "vf2 a LEFT JOIN (SELECT n AS x FROM vf2b) b ON a.n = b.x"));
        assertEquals(NEVER_NULL, coalesced("n", "vf2 a LEFT JOIN (SELECT z AS zz FROM vf2b) b ON a.z = b.zz"));
        assertEquals(MAY_BE_NULL, coalesced("zz", "vf2 a LEFT JOIN (SELECT z AS zz FROM vf2b) b ON a.z = b.zz"));
        assertEquals(NEVER_NULL, coalesced("x", "(SELECT a.n AS x FROM vf2 a LEFT JOIN vf2b b ON a.n = b.n)"));
        assertEquals(MAY_BE_NULL, coalesced("x", "(SELECT b.n AS x FROM vf2 a LEFT JOIN vf2b b ON a.n = b.n)"));
        assertEquals(NEVER_NULL, coalesced("x", "(SELECT a.n AS x FROM vf2 a JOIN vf2b b ON a.n = b.n)"));
        assertEquals(NEVER_NULL, cells("WITH c AS (SELECT n FROM vf2) SELECT SYSTEM$TYPEOF(COALESCE(x.n, 3000.5))"
            + " FROM c x JOIN c y ON x.n = y.n LIMIT 1"));
        engine.execute("CREATE OR REPLACE VIEW vw2 AS SELECT n FROM vf2");
        assertEquals(NEVER_NULL, coalesced("x.n", "vw2 x JOIN vf2b y ON x.n = y.n"));
        assertEquals(MAY_BE_NULL, coalesced("y.n", "vw2 x LEFT JOIN vw2 y ON x.n = y.n"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(1, 2)) f"));
        assertEquals(NEVER_NULL,
            coalesced("a.n", "vf2 a, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(1, 2), OUTER => TRUE) f"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a, TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1, 2))) f"));
        assertEquals(NEVER_NULL, coalesced("a.n", "vf2 a JOIN LATERAL FLATTEN(input => ARRAY_CONSTRUCT(1, 2)) f"));
    }

    /** ★ The kept side stays kept whatever clause reads it, and no condition is settled over an extended one. */
    @Test
    public void theKeptSideHoldsInEveryClause() {
        final String join = " FROM vf2 a JOIN vf2b b ON a.n = b.n";
        final String item = "SELECT SYSTEM$TYPEOF(COALESCE(a.n, 3000.5))";
        assertEquals(NEVER_NULL, cells(item + join + " GROUP BY a.n LIMIT 1"));
        assertEquals(MAY_BE_NULL, cells("SELECT SYSTEM$TYPEOF(COALESCE(b.n, 3000.5)) FROM vf2 a LEFT JOIN vf2b b"
            + " ON a.n = b.n GROUP BY b.n LIMIT 1"));
        assertEquals(NEVER_NULL, cells(item + join + " WHERE a.n > 0 LIMIT 1"));
        assertEquals(NEVER_NULL, cells(item + join + " ORDER BY 1 LIMIT 1"));
        assertEquals(NEVER_NULL, cells("SELECT DISTINCT SYSTEM$TYPEOF(COALESCE(a.n, 3000.5))" + join));
        assertEquals(NEVER_NULL, cells(item + ", SUM(a.n) OVER ()" + join + " LIMIT 1"));
        assertEquals(NEVER_NULL, cells(item + join + " QUALIFY ROW_NUMBER() OVER (ORDER BY a.n) = 1"));
        assertEquals("NUMBER(10,2)[SB4] | NUMBER(10,2)[SB4]",
            cells("SELECT SYSTEM$TYPEOF(COALESCE(MAX(a.n), 3000.5))" + join), "an aggregate over a join is past the statistics");
        assertEquals("NUMBER(5,1)[SB1]", cells("SELECT SYSTEM$TYPEOF(IFF(a.n > 5, 3000.5, 1.5))" + join + " LIMIT 1"));
        assertEquals("NUMBER(5,1)[SB2]", cells("SELECT SYSTEM$TYPEOF(IFF(b.n > 5, 3000.5, 1.5)) FROM vf2 a"
            + " LEFT JOIN vf2b b ON a.n = b.n LIMIT 1"));
    }
}
