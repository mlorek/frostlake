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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A correlated reference whose alias-qualified name collides with a column of the INNER table:
 * {@code WHERE EXISTS (SELECT 1 FROM stage t WHERE t.id = s.id)} where BOTH tables have an {@code id}.
 *
 * <p>The subquery's outer-row context was keyed only by TABLE name ({@code SRC.ID}), so the alias-qualified
 * {@code s.id} missed it and the strip-qualifier fallback bound it to the inner table's own column — turning
 * the correlation into {@code t.id = t.id} (always true): EXISTS matched every outer row, NOT EXISTS none, and
 * the ubiquitous dedup-guarded INSERT ({@code INSERT … SELECT … WHERE NOT EXISTS (…)}) either inserted nothing
 * or, in loaders whose guard erred the other way, inserted duplicates.
 *
 * <p>Also covers a SELECT-list alias referenced in a JOIN's ON condition ({@code … HASH(…) AS _chk … JOIN r ON
 * r.chk != _chk}), which failed to resolve and silently matched no rows.
 */
public class CorrelatedExistsShadowTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE stage (id VARCHAR, tag VARCHAR)");
        engine.execute("INSERT INTO stage VALUES ('x', 'seeded')");
        engine.execute("CREATE TABLE src (id VARCHAR, tag VARCHAR)");
        engine.execute("INSERT INTO src VALUES ('x', 'src-x'), ('y', 'src-y')");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    @Test
    public void notExistsKeepsOnlyTheUnmatchedOuterRow() {
        final ResultSet rs = q("SELECT s.id FROM src s WHERE NOT EXISTS "
            + "(SELECT 1 FROM stage t WHERE t.id = s.id)");
        assertEquals(1, rs.getRowCount());
        assertEquals("y", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void existsKeepsOnlyTheMatchedOuterRow() {
        final ResultSet rs = q("SELECT s.id FROM src s WHERE EXISTS "
            + "(SELECT 1 FROM stage t WHERE t.id = s.id)");
        assertEquals(1, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aBareInnerReferenceStillBindsToTheInnerTable() {
        // Proper SQL scoping: with no qualifier, `id` is the INNER table's column, so the predicate is
        // t.id = t.id — true for every stage row — and EXISTS is true for every outer row.
        assertEquals(2, q("SELECT s.id FROM src s WHERE EXISTS "
            + "(SELECT 1 FROM stage t WHERE t.id = id)").getRowCount());
    }

    @Test
    public void theDedupGuardedInsertInsertsExactlyTheMissingRows() {
        // The loader staging idiom: INSERT … SELECT … WHERE NOT EXISTS (already staged).
        engine.execute("INSERT INTO stage (id, tag) SELECT s.id, s.tag FROM src s "
            + "WHERE NOT EXISTS (SELECT 1 FROM stage t WHERE t.id = s.id)");
        final ResultSet rs = q("SELECT id, tag FROM stage ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals("seeded", rs.getRows().get(0).getValue(1));
        assertEquals("src-y", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void theGuardSeesRowsBufferedByTheSameTransaction() {
        engine.execute("CREATE TABLE stage2 (id VARCHAR, tag VARCHAR)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO stage2 VALUES ('x', 'from-01a')");
        engine.execute("INSERT INTO stage2 (id, tag) SELECT s.id, s.tag FROM src s "
            + "WHERE NOT EXISTS (SELECT 1 FROM stage2 t WHERE t.id = s.id)");
        engine.execute("COMMIT");
        assertEquals(1, ((Number) q("SELECT COUNT(*) FROM stage2 WHERE id = 'x'")
            .getRows().get(0).getValue(0)).longValue());
        assertEquals(2, q("SELECT id FROM stage2").getRowCount());
    }

    @Test
    public void aSelectListAliasResolvesInsideAJoinOnCondition() {
        engine.execute("CREATE TABLE l (k VARCHAR, v INTEGER)");
        engine.execute("CREATE TABLE r (k VARCHAR, chk INTEGER)");
        engine.execute("INSERT INTO l VALUES ('a', 5)");
        engine.execute("INSERT INTO r VALUES ('a', 5), ('a', 6)");
        // _chk is a select-list alias; the ON references it alongside real columns of both sides.
        final ResultSet rs = q("SELECT l.k, HASH(l.v) AS _chk FROM l "
            + "INNER JOIN r ON r.k = l.k AND r.chk != _chk");
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void aRealColumnWinsOverAnAliasOfTheSameNameInOn() {
        engine.execute("CREATE TABLE l2 (k VARCHAR, chk INTEGER)");
        engine.execute("CREATE TABLE r2 (k VARCHAR, chk INTEGER)");
        engine.execute("INSERT INTO l2 VALUES ('a', 5)");
        engine.execute("INSERT INTO r2 VALUES ('a', 5), ('a', 6)");
        // CHK names a real column of both sides AND an alias; the ON's l2.chk/r2.chk must bind to the
        // COLUMNS, so exactly one pair matches (5 = 5) regardless of the alias value.
        final ResultSet rs = q("SELECT l2.k, 999 AS chk FROM l2 "
            + "INNER JOIN r2 ON r2.k = l2.k AND r2.chk = l2.chk");
        assertEquals(1, rs.getRowCount());
    }
}
