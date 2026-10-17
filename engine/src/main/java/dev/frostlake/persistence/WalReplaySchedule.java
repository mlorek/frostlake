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


package dev.frostlake.persistence;

import dev.frostlake.transaction.WalStatementContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Where the write-ahead log's replay runs each logged statement. A transaction's record is appended when it commits,
 * after the records other sessions committed while it was open, and each of its statements keeps how many records
 * came between it and the commit (see {@link WalStatementContext#getLag}). The replay runs such a statement just
 * before the first record that came after it, inside its transaction, which stays open until the replay reaches the
 * transaction's own record; so the statement reads what it read when it first ran. A statement whose place lies
 * before the first record replayed, behind a checkpoint, runs before that record. Statements placed before the same
 * record run in the order they first ran, by their instants.
 */
public final class WalReplaySchedule {

    private final List<List<int[]>> before;
    private final boolean[][] early;

    private WalReplaySchedule(final List<List<int[]>> before, final boolean[][] early) {
        this.before = before;
        this.early = early;
    }

    /**
     * Places the statements of the transaction records a replay runs.
     *
     * @param records the transaction records, in log order
     * @return their schedule
     */
    public static WalReplaySchedule of(final List<WalRecord> records) {
        final List<List<int[]>> before = new ArrayList<List<int[]>>(records.size());
        final boolean[][] early = new boolean[records.size()][];
        for (int i = 0; i < records.size(); i++) {
            before.add(new ArrayList<int[]>());
            early[i] = new boolean[records.get(i).getStatements().size()];
        }
        for (int i = 0; i < records.size(); i++) {
            final WalRecord record = records.get(i);
            for (int j = 0; j < record.getStatements().size(); j++) {
                final WalStatementContext context = record.getStatementContext(j);
                if (context != null && context.getLag() > 0) {
                    early[i][j] = true;
                    before.get(Math.max(0, i - context.getLag())).add(new int[] {i, j});
                }
            }
        }
        for (final List<int[]> placed : before) {
            Collections.sort(placed, new Comparator<int[]>() {
                @Override
                public int compare(final int[] a, final int[] b) {
                    final Instant first = records.get(a[0]).getStatementInstant(a[1]);
                    final Instant second = records.get(b[0]).getStatementInstant(b[1]);
                    if (first != null && second != null && !first.equals(second)) {
                        return first.compareTo(second);
                    }
                    return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
                }
            });
        }
        return new WalReplaySchedule(before, early);
    }

    /**
     * The statements of later records that run before the record at a position, each as its record's position and
     * its own index there, in the order they first ran.
     *
     * @param position the record's position among the records replayed
     * @return the statements placed before it
     */
    public List<int[]> runBefore(final int position) {
        return before.get(position);
    }

    /**
     * Whether a statement runs before its own record, in the place it first ran.
     *
     * @param position  its record's position
     * @param statement its index in the record
     * @return true when it runs earlier
     */
    public boolean runsEarly(final int position, final int statement) {
        return early[position][statement];
    }

    /**
     * Whether any statement of the record at a position runs before it, so its transaction is open while the records
     * between replay.
     *
     * @param position the record's position
     * @return true when the record's transaction is interleaved with others
     */
    public boolean isInterleaved(final int position) {
        for (final boolean placedEarlier : early[position]) {
            if (placedEarlier) {
                return true;
            }
        }
        return false;
    }
}
