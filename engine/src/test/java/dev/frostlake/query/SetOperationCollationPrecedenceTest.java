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
 * The collation a set operation's column compares under is settled branch by branch as a comparison settles its
 * two sides: a collation a COLLATE names outranks a column's declared one, a collated branch decides over one
 * without, and two that disagree at one level are refused, naming the later branch's first. A named collation
 * keeps its rank through a derived table, a CTE, a view and the set operation itself; a table created from the
 * query declares it as its own (all live-verified).
 */
public class SetOperationCollationPrecedenceTest extends BaseDatabaseTest {

    /**
     * Operator, the two branches' items, then either the refusal or the values in the column's own order with
     * their count, and the collation the column reports — {@code (no row)} where the operation keeps none.
     */
    private static final String CELLS = """
        UNION ; c ; c COLLATE 'en-cs' ; a|A|b|B|4 ; en-cs
        UNION ; c COLLATE 'en-cs' ; c ; a|A|b|B|4 ; en-cs
        UNION ; c ; d ; Incompatible collations: 'en-cs' and 'en-ci'
        UNION ; d ; c ; Incompatible collations: 'en-ci' and 'en-cs'
        UNION ; c ; s ; A|B|2 ; en-ci
        UNION ; s ; c ; A|B|2 ; en-ci
        UNION ; c ; s COLLATE 'en-cs' ; a|A|b|B|4 ; en-cs
        UNION ; s COLLATE 'en-cs' ; c ; a|A|b|B|4 ; en-cs
        UNION ; c COLLATE 'en-cs' ; c COLLATE 'en-ci' ; Incompatible collations: 'en-ci' and 'en-cs'
        UNION ; c COLLATE 'en-ci' ; c COLLATE 'en-cs' ; Incompatible collations: 'en-cs' and 'en-ci'
        UNION ; s COLLATE 'en-ci' ; s COLLATE 'en-cs' ; Incompatible collations: 'en-cs' and 'en-ci'
        UNION ; c ; c COLLATE '' ; A|B|2 ; en-ci
        UNION ; c COLLATE '' ; c ; A|B|2 ; en-ci
        UNION ; c ; e ; Incompatible collations: 'de-ci' and 'en-ci'
        UNION ; e ; c ; Incompatible collations: 'en-ci' and 'de-ci'
        UNION ; c ; 'a' ; A|B|2 ; en-ci
        UNION ; 'a' ; c ; A|B|2 ; en-ci
        UNION ; c ; 'a' COLLATE 'en-cs' ; a|A|b|B|4 ; en-cs
        UNION ; s ; s COLLATE 'en-ci' ; A|B|2 ; en-ci
        UNION ; s COLLATE 'en-ci' ; s ; A|B|2 ; en-ci
        UNION ; c ; UPPER(c) ; A|B|2 ; en-ci
        UNION ; c ; c || 'x' ; A|Ax|B|Bx|4 ; en-ci
        UNION ; c ; d COLLATE 'en-ci' ; A|B|2 ; en-ci
        UNION ; c COLLATE 'en-cs' ; d ; a|A|b|B|4 ; en-cs
        UNION ; c ; c ; A|B|2 ; en-ci
        UNION ; d ; d ; a|A|b|B|4 ; en-cs
        UNION ; c ; NULL ; A|B|3 ; en-ci
        UNION ; NULL ; c ; A|B|3 ; en-ci
        UNION ; s COLLATE 'en-ci' ; c ; A|B|2 ; en-ci
        UNION ; c COLLATE 'de-ci' ; c ; A|B|2 ; de-ci
        UNION ; e COLLATE 'en-ci' ; c COLLATE 'en-ci' ; A|B|2 ; en-ci
        UNION ALL ; c ; c COLLATE 'en-cs' ; a|a|A|A|b|b|B|B|8 ; en-cs
        UNION ALL ; c COLLATE 'en-cs' ; c ; a|a|A|A|b|b|B|B|8 ; en-cs
        UNION ALL ; c ; d ; Incompatible collations: 'en-cs' and 'en-ci'
        UNION ALL ; c ; s ; a|A|a|A|b|B|b|B|8 ; en-ci
        UNION ALL ; c ; s COLLATE 'en-cs' ; a|a|A|A|b|b|B|B|8 ; en-cs
        UNION ALL ; c COLLATE 'en-cs' ; c COLLATE 'en-ci' ; Incompatible collations: 'en-ci' and 'en-cs'
        UNION ALL ; c ; c COLLATE '' ; a|A|a|A|b|B|b|B|8 ; en-ci
        UNION ALL ; c ; 'a' COLLATE 'en-cs' ; a|a|a|a|a|A|b|B|8 ; en-cs
        UNION ALL ; c COLLATE 'en-cs' ; d ; a|a|A|A|b|b|B|B|8 ; en-cs
        INTERSECT ; c ; c COLLATE 'en-cs' ; a|A|b|B|4 ; en-cs
        INTERSECT ; c COLLATE 'en-cs' ; c ; a|A|b|B|4 ; en-cs
        INTERSECT ; c ; d ; Incompatible collations: 'en-cs' and 'en-ci'
        INTERSECT ; c ; s ; A|B|2 ; en-ci
        INTERSECT ; c ; s COLLATE 'en-cs' ; a|A|b|B|4 ; en-cs
        INTERSECT ; c COLLATE 'en-cs' ; c COLLATE 'en-ci' ; Incompatible collations: 'en-ci' and 'en-cs'
        INTERSECT ; c ; c COLLATE '' ; A|B|2 ; en-ci
        INTERSECT ; c ; 'a' COLLATE 'en-cs' ; a|1 ; en-cs
        INTERSECT ; c COLLATE 'en-cs' ; d ; a|A|b|B|4 ; en-cs
        EXCEPT ; c ; c COLLATE 'en-cs' ; |0 ; (no row)
        EXCEPT ; c COLLATE 'en-cs' ; c ; |0 ; (no row)
        EXCEPT ; c ; d ; Incompatible collations: 'en-cs' and 'en-ci'
        EXCEPT ; c ; s ; |0 ; (no row)
        EXCEPT ; c ; s COLLATE 'en-cs' ; |0 ; (no row)
        EXCEPT ; c COLLATE 'en-cs' ; c COLLATE 'en-ci' ; Incompatible collations: 'en-ci' and 'en-cs'
        EXCEPT ; c ; c COLLATE '' ; |0 ; (no row)
        EXCEPT ; c ; 'a' COLLATE 'en-cs' ; A|b|B|3 ; en-cs
        EXCEPT ; c COLLATE 'en-cs' ; d ; |0 ; (no row)
        """;

    /** Three branches' items, then the refusal or the values with their count, and the collation. */
    private static final String THREE = """
        c ; d ; c COLLATE 'en-cs' ; Incompatible collations: 'en-cs' and 'en-ci'
        c ; c COLLATE 'en-cs' ; d ; a|A|b|B|4 ; en-cs
        s ; c ; d ; Incompatible collations: 'en-cs' and 'en-ci'
        c ; s ; c COLLATE 'en-cs' ; a|A|b|B|4 ; en-cs
        c COLLATE 'en-cs' ; s ; c ; a|A|b|B|4 ; en-cs
        """;

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs',"
            + " e VARCHAR COLLATE 'de-ci')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a', 'a'), ('A', 'A', 'A', 'A'), ('b', 'b', 'b', 'b'),"
            + " ('B', 'B', 'B', 'B')");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private Object first(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.next(), sql);
        return rs.getValue(0);
    }

    /** The body's values in the column's own order with their count, and the collation the column reports. */
    private void judge(final String body, final String expected, final String collation) {
        final String listed = "SELECT LISTAGG(x, '|') WITHIN GROUP (ORDER BY x), COUNT(*) FROM (" + body + ")";
        final String collated = "SELECT DISTINCT COLLATION(x) FROM (" + body + ")";
        if (expected.startsWith("Incompatible")) {
            assertEquals("SQL compilation error: " + expected, refusal(listed), body);
            assertEquals("SQL compilation error: " + expected, refusal(collated), body);
            return;
        }
        final ResultSet rs = engine.executeQuery(listed);
        assertTrue(rs.next(), body);
        assertEquals(expected, rs.getValue(0) + "|" + rs.getValue(1), body);
        final ResultSet reported = engine.executeQuery(collated);
        assertEquals(collation, reported.next() ? String.valueOf(reported.getValue(0)) : "(no row)", body);
    }

    @Test
    public void twoBranchesSettleAsAComparisonDoes() {
        for (final String line : CELLS.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            final String[] cell = line.trim().split(" ; ", -1);
            judge("SELECT " + cell[1] + " AS x FROM ct " + cell[0] + " SELECT " + cell[2] + " FROM ct", cell[3],
                cell.length > 4 ? cell[4] : "");
        }
    }

    @Test
    public void aNamedCollationHoldsThroughTheNextBranch() {
        for (final String line : THREE.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            final String[] cell = line.trim().split(" ; ", -1);
            judge("SELECT " + cell[0] + " AS x FROM ct UNION SELECT " + cell[1] + " FROM ct UNION SELECT " + cell[2]
                + " FROM ct", cell[3], cell.length > 4 ? cell[4] : "");
        }
    }

    @Test
    public void aNamedCollationKeepsItsRankOutsideTheQueryThatWroteIt() {
        final String incompatible = "SQL compilation error: Incompatible collations: ";
        assertEquals(4L, ((Number) first("SELECT COUNT(*) FROM (SELECT c COLLATE 'en-cs' AS x, c FROM ct) WHERE x = c"))
            .longValue());
        assertEquals(4L, ((Number) first("SELECT COUNT(*) FROM (SELECT s COLLATE 'en-ci' AS x, d FROM ct) WHERE x = d"))
            .longValue());
        assertEquals(incompatible + "'en-cs' and 'en-ci'",
            refusal("SELECT COUNT(*) FROM (SELECT c AS x, d FROM ct) WHERE x = d"));
        assertEquals("en-cs", first("SELECT DISTINCT COLLATION(y) FROM (SELECT x AS y FROM (SELECT c COLLATE 'en-cs' AS x"
            + " FROM ct) UNION SELECT c FROM ct)"));
        assertEquals(incompatible + "'en-ci' and 'en-cs'", refusal("SELECT DISTINCT COLLATION(y) FROM (SELECT x AS y FROM"
            + " (SELECT c COLLATE 'en-cs' AS x FROM ct) UNION SELECT c COLLATE 'en-ci' FROM ct)"));
        // The set operation's own column: en-cs by name, so it meets a column's en-ci without a refusal.
        assertEquals(4L, ((Number) first("SELECT COUNT(*) FROM (SELECT c AS x FROM ct UNION SELECT c COLLATE 'en-cs'"
            + " FROM ct) u JOIN ct ON u.x = ct.c")).longValue());
        assertEquals(incompatible + "'en-cs' and 'en-ci'", refusal("SELECT COUNT(*) FROM (SELECT c AS x FROM ct UNION"
            + " SELECT c FROM ct) u JOIN ct ON u.x = ct.d"));
        assertEquals("en-cs", first("SELECT DISTINCT COLLATION(x) FROM ((SELECT c AS x FROM ct UNION SELECT c COLLATE"
            + " 'en-cs' FROM ct) UNION SELECT c FROM ct)"));
        assertEquals(4L, ((Number) first("WITH w AS (SELECT c COLLATE 'en-cs' AS x, c FROM ct) SELECT COUNT(*) FROM w"
            + " WHERE x = c")).longValue());
        assertEquals(4L, ((Number) first("WITH w (x, y) AS (SELECT c COLLATE 'en-cs', c FROM ct) SELECT COUNT(*) FROM w"
            + " WHERE x = y")).longValue());
        engine.execute("CREATE OR REPLACE VIEW cv AS SELECT c COLLATE 'en-cs' AS x, c FROM ct");
        assertEquals(4L, ((Number) first("SELECT COUNT(*) FROM cv WHERE x = c")).longValue());
        assertEquals("en-cs", first("SELECT DISTINCT COLLATION(x) FROM (SELECT x FROM cv UNION SELECT c FROM ct)"));
        // A table created from the query declares the collation as its own, at a column's rank.
        engine.execute("CREATE OR REPLACE TABLE cta AS SELECT c COLLATE 'en-cs' AS x, c FROM ct");
        assertEquals(incompatible + "'en-ci' and 'en-cs'", refusal("SELECT COUNT(*) FROM cta WHERE x = c"));
        assertEquals(incompatible + "'en-cs' and 'en-ci'",
            refusal("SELECT COUNT(*) FROM (SELECT c, s FROM ct UNION SELECT d, s FROM ct)"));
    }

    @Test
    public void theSettledCollationOrdersTheResult() {
        final ResultSet rs = engine.executeQuery("SELECT c AS x FROM ct UNION SELECT c COLLATE 'en-cs' FROM ct ORDER BY x");
        final StringBuilder order = new StringBuilder();
        while (rs.next()) {
            order.append(rs.getValue(0));
        }
        assertEquals("aAbB", order.toString());
        assertEquals("A|b|B", first("SELECT LISTAGG(x, '|') WITHIN GROUP (ORDER BY x) FROM (SELECT c AS x FROM ct MINUS"
            + " SELECT 'a' COLLATE 'en-cs')"));
    }
}
