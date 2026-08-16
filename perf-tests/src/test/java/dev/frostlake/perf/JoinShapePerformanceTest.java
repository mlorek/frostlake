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

package dev.frostlake.perf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which join SHAPES reach the hash path, measured at 10k rows a side.
 *
 * <p>JoinOperator has two paths: executeHashJoin, which partitions the right side by the equi-join
 * keys, and a nested-loop fallback for everything the key extraction does not recognise. The fallback
 * is O(left x right) — 100 million comparisons here — so which path a query lands on is worth three
 * orders of magnitude, and it is decided by the SHAPE of the condition rather than by its size.
 *
 * <p>The assertions here are deliberately loose. They exist to catch a shape SILENTLY falling off the
 * hash path, which shows up as seconds rather than milliseconds, and the ceiling is far enough above
 * the measured numbers (single-digit ms) that it cannot fail on a slow or loaded machine. Timings are
 * logged so the numbers are tracked rather than anecdotal.
 */
@Tag("perf")
public class JoinShapePerformanceTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(JoinShapePerformanceTest.class);
    private static final int ROWS = 10_000;
    /** Generous: the hash shapes measure in single-digit milliseconds, the fallback in seconds. */
    private static final long HASH_PATH_CEILING_MS = 5_000;

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE js_left (id NUMBER, k NUMBER, k2 NUMBER, name VARCHAR(20))");
        engine.execute("CREATE TABLE js_right (id NUMBER, k NUMBER, k2 NUMBER, label VARCHAR(20))");
        fill("js_left", "name");
        fill("js_right", "label");
    }

    private void fill(final String table, final String textColumn) {
        final int batch = 500;
        for (int b = 0; b < ROWS / batch; b++) {
            final StringBuilder sql = new StringBuilder("INSERT INTO " + table
                + " (id, k, k2, " + textColumn + ") VALUES ");
            for (int i = 0; i < batch; i++) {
                final int n = b * batch + i;
                sql.append(i == 0 ? "" : ", ")
                   .append("(").append(n).append(", ").append(n).append(", ")
                   .append(n % 100).append(", 'v").append(n).append("')");
            }
            engine.execute(sql.toString());
        }
    }

    /** Runs the query, logs what it cost, and returns the milliseconds it took. */
    private long timed(final String label, final String sql, final int expectedRows) {
        final long start = System.currentTimeMillis();
        final ResultSet rs = engine.executeQuery(sql);
        final long took = System.currentTimeMillis() - start;
        logger.info("join shape {} took {} ms ({} rows)", label, took, rs.getRowCount());
        assertEquals(expectedRows, rs.getRowCount(), label + " returned the wrong number of rows");
        return took;
    }

    private void onTheHashPath(final String label, final String sql, final int expectedRows) {
        final long took = timed(label, sql, expectedRows);
        assertTrue(took < HASH_PATH_CEILING_MS, label + " took " + took
            + " ms, which is nested-loop territory — the condition stopped being recognised as an "
            + "equi-join and the query fell off the hash path");
    }

    /** The shapes the key extraction recognises today. Each must stay on the hash path. */
    @Test
    public void everyRecognisedEquiJoinShapeStaysOnTheHashPath() {
        onTheHashPath("inner/one-key",
            "SELECT l.id, r.id FROM js_left l INNER JOIN js_right r ON l.k = r.k", ROWS);
        onTheHashPath("inner/select-star",
            "SELECT * FROM js_left l INNER JOIN js_right r ON l.k = r.k", ROWS);
        onTheHashPath("inner/two-keys",
            "SELECT l.id FROM js_left l INNER JOIN js_right r ON l.k = r.k AND l.k2 = r.k2", ROWS);
        // The operand order of the ON condition must not matter.
        onTheHashPath("inner/reversed-operands",
            "SELECT l.id FROM js_left l INNER JOIN js_right r ON r.k = l.k", ROWS);
        onTheHashPath("inner/fully-qualified",
            "SELECT l.id FROM test_db.test_schema.js_left l "
                + "INNER JOIN test_db.test_schema.js_right r ON l.k = r.k", ROWS);
        onTheHashPath("inner/using", "SELECT l.id FROM js_left l INNER JOIN js_right r USING (k)", ROWS);
        onTheHashPath("inner/natural", "SELECT l.id FROM js_left l NATURAL JOIN js_right r", ROWS);
    }

    /** The outer joins walk extra null-extension state but must stay on the hash path too. */
    @Test
    public void outerJoinsStayOnTheHashPath() {
        onTheHashPath("left", "SELECT l.id, r.id FROM js_left l LEFT JOIN js_right r ON l.k = r.k", ROWS);
        onTheHashPath("full",
            "SELECT l.id, r.id FROM js_left l FULL OUTER JOIN js_right r ON l.k = r.k", ROWS);
    }

    /** A join key with only 100 distinct values puts 100 rows in every bucket; still not a scan. */
    @Test
    public void aLowCardinalityKeyStaysOnTheHashPath() {
        onTheHashPath("inner/low-cardinality",
            "SELECT COUNT(*) FROM js_left l INNER JOIN js_right r ON l.k2 = r.k2", 1);
    }

    /** What surrounds the join must not push it off the hash path either. */
    @Test
    public void theJoinStaysFastUnderAWhereGroupByOrOrderBy() {
        onTheHashPath("inner/where",
            "SELECT l.id FROM js_left l INNER JOIN js_right r ON l.k = r.k WHERE l.id < 100", 100);
        onTheHashPath("inner/group-by",
            "SELECT l.k2, COUNT(*) FROM js_left l INNER JOIN js_right r ON l.k = r.k GROUP BY l.k2", 100);
        onTheHashPath("inner/order-by-limit",
            "SELECT l.id FROM js_left l INNER JOIN js_right r ON l.k = r.k ORDER BY l.id LIMIT 10", 10);
    }

    /**
     * The comma spelling of the SAME query must cost the same as the ON spelling. Its equality sits in
     * WHERE, so the join itself is handed no condition and would build the whole cartesian product for
     * the WHERE to throw away — 100 million rows to keep 10 thousand. Lifting the equi-join conjuncts
     * out of the WHERE lets the hash path run.
     *
     * <p>Asserted as a RATIO rather than a duration, which is what makes it machine-independent: both
     * spellings run on the same box in the same JVM, so the comparison holds on a slow or loaded
     * machine where a millisecond ceiling would not. The gap was 800x before the pushdown.
     */
    @Test
    public void theCommaSpellingCostsTheSameAsTheOnSpelling() {
        // Warm the paths first — a first-run JIT cost on either side would distort the ratio.
        timed("warmup/on", "SELECT l.id FROM js_left l INNER JOIN js_right r ON l.k = r.k", ROWS);
        timed("warmup/comma", "SELECT l.id FROM js_left l, js_right r WHERE l.k = r.k", ROWS);

        final long onSpelling = timed("inner/on-clause",
            "SELECT l.id, r.id FROM js_left l INNER JOIN js_right r ON l.k = r.k", ROWS);
        final long commaSpelling = timed("inner/comma-and-where",
            "SELECT l.id, r.id FROM js_left l, js_right r WHERE l.k = r.k", ROWS);
        logger.info("comma {} ms vs ON {} ms", commaSpelling, onSpelling);
        assertTrue(commaSpelling <= Math.max(50, onSpelling * 10),
            "the comma spelling took " + commaSpelling + " ms against the ON spelling's " + onSpelling
                + " ms — it has fallen back off the hash path onto the cartesian product");
    }

    /** The equality still pre-filters correctly when the WHERE says more than the join key. */
    @Test
    public void aPushedDownEqualityComposesWithTheRestOfTheWhere() {
        onTheHashPath("comma/with-extra-predicate",
            "SELECT l.id FROM js_left l, js_right r WHERE l.k = r.k AND l.id < 50", 50);
    }
}
