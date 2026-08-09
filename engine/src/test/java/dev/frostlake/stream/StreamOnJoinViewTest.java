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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDC over a stream ON an INNER-join view, measured mutation by mutation on a real account: the
 * stream is the NET DIFFERENCE of the join's result across the window. A base-row insert with no
 * join partner surfaces nothing; EITHER side completing a match surfaces the joined row as INSERT;
 * a second match surfaces only the new combination; an in-window insert+delete cancels; deleting
 * one side emits DELETE of the joined row; and a one-side UPDATE emits a DELETE+INSERT pair with
 * METADATA$ISUPDATE=true sharing a METADATA$ROW_ID. Consumption advances the offset exactly as for
 * any other stream.
 */
public class StreamOnJoinViewTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE t1 (k INTEGER, a VARCHAR)");
        engine.execute("CREATE TABLE t2 (k INTEGER, b VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a1')");
        engine.execute("INSERT INTO t2 VALUES (1, 'b1')");
        engine.execute("CREATE VIEW jv AS SELECT t1.k AS k, t1.a AS a, t2.b AS b"
            + " FROM t1 JOIN t2 ON t1.k = t2.k");
        engine.execute("CREATE STREAM js ON VIEW jv");
    }

    /** Rows of {@code SELECT k, a, b, METADATA$ACTION, METADATA$ISUPDATE FROM js} as one string. */
    private String changes() {
        final ResultSet rs = engine.executeQuery(
            "SELECT k, a, b, METADATA$ACTION, METADATA$ISUPDATE FROM js ORDER BY k, METADATA$ACTION");
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append('[').append(row.getValue(0)).append('|').append(row.getValue(1))
                .append('|').append(row.getValue(2)).append('|').append(row.getValue(3))
                .append('|').append(row.getValue(4)).append(']');
        }
        return out.toString();
    }

    @Test
    public void freshStreamIsEmptyAndNonMatchingInsertStaysInvisible() {
        assertEquals("", changes());
        engine.execute("INSERT INTO t1 VALUES (9, 'a9')");
        assertEquals("", changes());
    }

    @Test
    public void eitherSideCompletingAMatchSurfacesTheJoinedRow() {
        engine.execute("INSERT INTO t1 VALUES (2, 'a2')");
        engine.execute("INSERT INTO t2 VALUES (2, 'b2')");
        assertEquals("[2|a2|b2|INSERT|false]", changes());
        engine.execute("CREATE OR REPLACE STREAM js ON VIEW jv");
        engine.execute("INSERT INTO t2 VALUES (2, 'b2x')");
        assertEquals("[2|a2|b2x|INSERT|false]", changes());
    }

    @Test
    public void deletingOneSideEmitsDeleteOfTheJoinedRow() {
        engine.execute("DELETE FROM t2 WHERE k = 1");
        assertEquals("[1|a1|b1|DELETE|false]", changes());
    }

    @Test
    public void inWindowInsertThenDeleteCancels() {
        engine.execute("INSERT INTO t1 VALUES (4, 'a4')");
        engine.execute("INSERT INTO t2 VALUES (4, 'b4')");
        engine.execute("DELETE FROM t1 WHERE k = 4");
        assertEquals("", changes());
    }

    @Test
    public void oneSideUpdatePairsWithSharedRowIdAndIsUpdateTrue() {
        engine.execute("UPDATE t1 SET a = 'a1-upd' WHERE k = 1");
        assertEquals("[1|a1|b1|DELETE|true] [1|a1-upd|b1|INSERT|true]", changes());
        final ResultSet rs = engine.executeQuery(
            "SELECT METADATA$ROW_ID FROM js ORDER BY METADATA$ACTION");
        assertEquals(2, rs.getRowCount());
        assertEquals(String.valueOf(rs.getRows().get(0).getValue(0)),
            String.valueOf(rs.getRows().get(1).getValue(0)),
            "the DELETE and INSERT halves of an update share a row id");
    }

    @Test
    public void consumptionAdvancesTheJoinStream() {
        engine.execute("INSERT INTO t1 VALUES (5, 'a5')");
        engine.execute("INSERT INTO t2 VALUES (5, 'b5')");
        engine.execute("CREATE TABLE sink (k INTEGER, a VARCHAR, b VARCHAR)");
        engine.execute("INSERT INTO sink SELECT k, a, b FROM js");
        final ResultSet sink = engine.executeQuery("SELECT k FROM sink");
        assertEquals(1, sink.getRowCount());
        assertEquals("", changes());
    }

    @Test
    public void streamHasDataReflectsTheWindow() {
        engine.execute("INSERT INTO t1 VALUES (6, 'a6')");
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('js')");
        // A captured change is pending (Snowflake permits false positives here — the non-matching
        // insert produced no view delta, but the capture exists).
        assertTrue(String.valueOf(rs.getRows().get(0).getValue(0)).equalsIgnoreCase("true"));
    }
}
