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

package dev.frostlake.executor;

import dev.frostlake.storage.Row;

import java.util.Map;

/**
 * First-occurrence 1-based position, RANK and DENSE_RANK of every row of ONE sorted window
 * partition, built in a single pass over the partition. Keys are ROW VALUES with first-equal-wins
 * puts, which reproduces the former per-output-row linear scans exactly: duplicate rows all read
 * the FIRST duplicate's numbers.
 */
public final class WindowPartitionOrder {

    private final Map<Row, Long> positionByRow;
    private final Map<Row, Long> rankByRow;
    private final Map<Row, Long> denseRankByRow;
    // Peer-group bounds per partition INDEX: firstPeerByIndex[i]/lastPeerByIndex[i] are the first
    // and last index sharing index i's full ORDER BY key tuple (the whole partition when the OVER
    // has no ORDER BY) — the per-row peer scans collapse to array reads.
    private final int[] firstPeerByIndex;
    private final int[] lastPeerByIndex;

    public WindowPartitionOrder(final Map<Row, Long> positionByRow, final Map<Row, Long> rankByRow,
                                final Map<Row, Long> denseRankByRow,
                                final int[] firstPeerByIndex, final int[] lastPeerByIndex) {
        this.positionByRow = positionByRow;
        this.rankByRow = rankByRow;
        this.denseRankByRow = denseRankByRow;
        this.firstPeerByIndex = firstPeerByIndex;
        this.lastPeerByIndex = lastPeerByIndex;
    }

    /** 1-based position of the first row equal to {@code row}, or null when no row equals it. */
    public Long position(final Row row) {
        return positionByRow.get(row);
    }

    public Long rank(final Row row) {
        return rankByRow.get(row);
    }

    public Long denseRank(final Row row) {
        return denseRankByRow.get(row);
    }

    public int firstPeer(final int index) {
        return firstPeerByIndex[index];
    }

    public int lastPeer(final int index) {
        return lastPeerByIndex[index];
    }
}
