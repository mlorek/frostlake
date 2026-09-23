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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A grouped query's ORDER BY key may not hold a subquery of its own, but a subquery an aggregate call takes as
 * its argument is computed with the aggregate: an uncorrelated one is answered, and a correlated one is judged by
 * the correlation rules, refused as an unsupported subquery where they refuse it. A subquery outside every
 * aggregate keeps the not-a-valid-order-by refusal, which also outranks an aggregated one beside it. A subquery
 * that is a call's whole argument list is placed at its opening parenthesis, anywhere else at its SELECT (all
 * live-verified).
 */
public class GroupedOrderKeyAggregatedSubqueryTest extends BaseDatabaseTest {

    private static final String CORRELATED = "(SELECT MAX(fz.id + g.v) FROM g)";
    private static final String CORRELATED_ECHO =
        "[(SELECT MAX(CORRELATION(FZ.ID) + G.V) AS \"MAX(FZ.ID + G.V)\" FROM G AS G)]";
    private static final String UNCORRELATED_ECHO = "[(SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G)]";
    private static final String NOT_VALID = " is not a valid order by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    /** Each row's cells joined by commas, rows by bars, in the order answered. */
    private String rows(final String sql) {
        final List<String> lines = rowLines(sql);
        return String.join(" | ", lines);
    }

    /** The rows as {@link #rows} spells them, sorted: for a key that ties every row. */
    private String sortedRows(final String sql) {
        final List<String> lines = rowLines(sql);
        Collections.sort(lines);
        return String.join(" | ", lines);
    }

    private List<String> rowLines(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<>();
        for (int r = 0; r < result.getRowCount(); r++) {
            final StringBuilder line = new StringBuilder();
            for (int c = 0; c < result.getColumnCount(); c++) {
                line.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String unsupportedAt(final int position) {
        return "SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position " + position;
    }

    private static String plain(final String sentence) {
        return "SQL compilation error:\n" + sentence;
    }

    @Test
    public void aCorrelatedSubqueryAnAggregateTakesIsJudgedByTheCorrelationRules() {
        assertEquals(unsupportedAt(41), refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(" + CORRELATED + ")"));
        assertEquals(unsupportedAt(41), refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(" + CORRELATED + ") + 1"));
        assertEquals(unsupportedAt(41), refusal("SELECT b FROM fz GROUP BY b ORDER BY SUM(" + CORRELATED + ")"));
        assertEquals(unsupportedAt(43), refusal("SELECT b FROM fz GROUP BY b ORDER BY COUNT(" + CORRELATED + ")"));
        assertEquals(unsupportedAt(47), refusal("SELECT b FROM fz GROUP BY b ORDER BY ANY_VALUE(" + CORRELATED + ")"));
        assertEquals(unsupportedAt(41),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT g.v FROM g WHERE g.id = fz.id))"));
        assertEquals(unsupportedAt(41),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(" + CORRELATED + ") DESC NULLS LAST"));
        assertEquals(unsupportedAt(53),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(fz.id), MAX(" + CORRELATED + ")"));
        assertEquals(unsupportedAt(43), refusal("SELECT b FROM fz GROUP BY ALL ORDER BY MAX(" + CORRELATED + ")"));
    }

    @Test
    public void theRefusalIsPlacedWhereTheSubqueryArgumentBegins() {
        assertEquals(unsupportedAt(50), refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(fz.id + " + CORRELATED + ")"));
        assertEquals(unsupportedAt(52),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY COUNT(DISTINCT " + CORRELATED + ")"));
        assertEquals(unsupportedAt(45), refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(ABS(" + CORRELATED + "))"));
        assertEquals(unsupportedAt(41), refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX((" + CORRELATED + "))"));
        assertEquals(unsupportedAt(42), refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(" + CORRELATED + " + 1)"));
        assertEquals(unsupportedAt(41),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(SELECT MAX(fz.id + g.v) FROM g)"));
        assertEquals(unsupportedAt(14), refusal("SELECT b, MAX(" + CORRELATED + ") FROM fz GROUP BY b"));
        assertEquals(unsupportedAt(14), refusal("SELECT b, MAX(SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY b"));
    }

    @Test
    public void anUncorrelatedOrSupportedSubqueryInAnAggregateIsAnswered() {
        assertEquals("false | true",
            rows("SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id))"));
        assertEquals("true | false",
            rows("SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) DESC"));
        assertEquals("false | true", rows("SELECT b FROM fz GROUP BY b "
            + "ORDER BY MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) + MAX(fz.id)"));
        assertEquals("true, 1 | false, 1", rows("SELECT b, COUNT(*) FROM fz GROUP BY b "
            + "ORDER BY MAX((SELECT MAX(g.v) FROM g WHERE g.id < fz.id)) NULLS FIRST"));
        assertEquals("false | true", sortedRows("SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g))"));
        assertEquals("false | true",
            sortedRows("SELECT b FROM fz GROUP BY b ORDER BY ABS(MAX((SELECT MAX(g.v) FROM g)))"));
        assertEquals("false | true", sortedRows("SELECT b FROM fz GROUP BY b ORDER BY MAX(SELECT MAX(g.v) FROM g)"));
        assertEquals("false | true",
            sortedRows("SELECT b FROM fz GROUP BY b ORDER BY ARRAY_AGG((SELECT MAX(g.v) FROM g))::VARCHAR"));
    }

    @Test
    public void aSubqueryOutsideEveryAggregateIsNoValidKey() {
        assertEquals(plain(CORRELATED_ECHO + NOT_VALID), refusal("SELECT b FROM fz GROUP BY b ORDER BY " + CORRELATED));
        assertEquals(plain(CORRELATED_ECHO + NOT_VALID),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY ABS(" + CORRELATED + ")"));
        assertEquals(plain(CORRELATED_ECHO + NOT_VALID),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY " + CORRELATED + " + 1"));
        assertEquals(plain(UNCORRELATED_ECHO + NOT_VALID),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY ABS((SELECT MAX(g.v) FROM g))"));
        assertEquals(plain(UNCORRELATED_ECHO + NOT_VALID),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY ABS(SELECT MAX(g.v) FROM g)"));
        assertEquals(plain(CORRELATED_ECHO + NOT_VALID),
            refusal("SELECT b FROM fz GROUP BY b ORDER BY ABS(SELECT MAX(fz.id + g.v) FROM g)"));
        final String constantEcho = plain("[(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)]" + NOT_VALID);
        assertEquals(constantEcho,
            refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g)) + (SELECT 1)"));
        assertEquals(constantEcho, refusal("SELECT b FROM fz GROUP BY b ORDER BY (SELECT 1) + MAX(" + CORRELATED + ")"));
        assertEquals(constantEcho, refusal("SELECT b FROM fz GROUP BY b ORDER BY MAX(" + CORRELATED + ") + (SELECT 1)"));
    }

    @Test
    public void anUngroupedQueryAggregatingInItsOrderByIsGroupedImplicitly() {
        assertEquals(plain("[FZ.B] is not a valid group by expression"),
            refusal("SELECT b FROM fz ORDER BY MAX(" + CORRELATED + ")"));
        assertEquals(plain("[FZ.B] is not a valid group by expression"),
            refusal("SELECT DISTINCT b FROM fz ORDER BY MAX((SELECT MAX(g.v) FROM g))"));
    }

    /** Outside ORDER BY too: a subquery that is a call's whole argument list is placed at its parenthesis. */
    @Test
    public void aCallsWholeSubqueryArgumentIsPlacedAtItsParenthesis() {
        assertEquals(unsupportedAt(11), refusal("SELECT ABS(" + CORRELATED + ") FROM fz"));
        assertEquals(unsupportedAt(11), refusal("SELECT ABS((" + CORRELATED + ")) FROM fz"));
        assertEquals(unsupportedAt(12), refusal("SELECT ABS( " + CORRELATED + " ) FROM fz"));
        assertEquals(unsupportedAt(23), refusal("SELECT ARRAY_CONSTRUCT(" + CORRELATED + ") FROM fz"));
        assertEquals(unsupportedAt(11), refusal("SELECT MAX(" + CORRELATED + ") OVER () FROM fz"));
        assertEquals(unsupportedAt(28), refusal("SELECT id FROM fz WHERE ABS(" + CORRELATED + ") > 0"));
        assertEquals(unsupportedAt(31), refusal("SELECT id FROM fz ORDER BY ABS(" + CORRELATED + ")"));
        assertEquals(unsupportedAt(11), refusal("SELECT ABS((SELECT g.v FROM g WHERE g.id = fz.id)) FROM fz"));
        assertEquals(unsupportedAt(11),
            refusal("SELECT ABS((SELECT MAX(fz.id + g.v) FROM g UNION ALL SELECT 1)) FROM fz"));
        assertEquals(unsupportedAt(11),
            refusal("SELECT ABS((WITH w AS (SELECT 1 AS x) SELECT MAX(fz.id + w.x) FROM w)) FROM fz"));
        assertEquals(unsupportedAt(11), refusal("SELECT ABS(" + CORRELATED + ") FROM fz WHERE 1 = 0"));
    }

    /** Beside another argument, as an operand, under CAST or unparenthesised, it is placed at its SELECT. */
    @Test
    public void anyOtherSubqueryIsPlacedAtItsSelect() {
        assertEquals(unsupportedAt(11), refusal("SELECT ABS(SELECT MAX(fz.id + g.v) FROM g) FROM fz"));
        assertEquals(unsupportedAt(11), refusal("SELECT ABS(SELECT MAX(fz.id + g.v) FROM g) FROM fz WHERE 1 = 0"));
        assertEquals(unsupportedAt(12), refusal("SELECT ABS(" + CORRELATED + " + 1) FROM fz"));
        assertEquals(unsupportedAt(20), refusal("SELECT GREATEST(1, " + CORRELATED + ") FROM fz"));
        assertEquals(unsupportedAt(17), refusal("SELECT GREATEST(" + CORRELATED + ", 1) FROM fz"));
        assertEquals(unsupportedAt(12), refusal("SELECT NVL(" + CORRELATED + ", 0) FROM fz"));
        assertEquals(unsupportedAt(13), refusal("SELECT CAST(" + CORRELATED + " AS INT) FROM fz"));
        assertEquals(unsupportedAt(8), refusal("SELECT " + CORRELATED + "::INT FROM fz"));
        assertEquals(unsupportedAt(8), refusal("SELECT (" + CORRELATED + ") FROM fz"));
        assertEquals(unsupportedAt(12), refusal("SELECT 1 + (" + CORRELATED + ") FROM fz"));
        assertEquals(unsupportedAt(18), refusal("SELECT IFF(TRUE, (SELECT g.v FROM g WHERE g.id = fz.id), 0) FROM fz"));
    }
}
