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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A window call written in the ORDER BY of a query with an explicit GROUP BY is refused, and refused
 * whatever its keys name: a group key, a SELECT alias, an aggregate and an ungrouped column all get the
 * same answer, so this is a rule about the CLAUSE rather than about scope.
 *
 * <pre>
 *   GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a)   [ROW_NUMBER() OVER (…)] is not a valid
 *   GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY s)   order by expression
 * </pre>
 *
 * <p>Two neighbours stay legal and are asserted here so the rule cannot widen by accident: ordering by
 * a window the SELECT LIST carries (an ordinary reference to a projected column), and a window in the
 * ORDER BY of an IMPLICITLY aggregated query, which live runs — that query has exactly one row, so no
 * ordering of it can change anything.
 *
 * <p>Live re-prints the call canonicalised where this prints it as written, so the cases assert the
 * sentence around the brackets.
 */
public class GroupedOrderByWindowTest extends BaseDatabaseTest {

    private static final String NOT_ORDERABLE = "is not a valid order by expression";
    private static final String GROUPED = "SELECT a, SUM(b) s FROM g GROUP BY a ORDER BY ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT, c INT)");
        engine.execute("INSERT INTO g VALUES (1, 10, 100), (1, 20, 200), (2, 30, 300)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private String order(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(",");
            }
            text.append(String.valueOf(rs.getValue(0)));
        }
        return text.toString();
    }

    /** Whatever the window's keys name, a grouped ORDER BY refuses it. */
    @Test
    public void aGroupedOrderByRefusesAWindowWhateverItNames() {
        assertTrue(refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY c)").contains(NOT_ORDERABLE),
            refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY c)"));
        assertTrue(refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY s)").contains(NOT_ORDERABLE),
            refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY s)"));
        assertTrue(refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY a)").contains(NOT_ORDERABLE),
            refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY a)"));
        assertTrue(refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY MAX(c))").contains(NOT_ORDERABLE),
            refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY MAX(c))"));
    }

    /** Including one nested in a larger key expression — the CALL is what is refused. */
    @Test
    public void aWindowInsideALargerKeyIsRefusedToo() {
        assertTrue(refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY a) + 1").contains(NOT_ORDERABLE),
            refusal(GROUPED + "ROW_NUMBER() OVER (ORDER BY a) + 1"));
    }

    /** Ordering by a window the SELECT LIST carries is an ordinary column reference, and reads. */
    @Test
    public void aSelectedWindowIsOrderableByItsAlias() {
        assertEquals("1,2",
            order("SELECT a, ROW_NUMBER() OVER (ORDER BY a) r FROM g GROUP BY a ORDER BY r"));
    }

    /** An IMPLICITLY aggregated query answers one row, and live orders it by a window quite happily. */
    @Test
    public void implicitAggregationAcceptsAWindowKey() {
        assertEquals("60", order("SELECT SUM(b) FROM g ORDER BY ROW_NUMBER() OVER (ORDER BY 1)"));
    }

    /** And the ordinary grouped keys are untouched. */
    @Test
    public void theOrdinaryGroupedKeysStillOrder() {
        assertEquals("1,2", order(GROUPED + "a"));
        assertEquals("1,2", order(GROUPED + "SUM(b)"));
    }
}
