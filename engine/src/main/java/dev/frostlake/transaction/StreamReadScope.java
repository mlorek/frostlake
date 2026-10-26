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

package dev.frostlake.transaction;

/**
 * Where a consuming DML's stream read ended, so the commit can advance the stream to exactly that point and no
 * further. A read inside a transaction lists the changes captured before the transaction began: {@code cut} counts
 * the stream's records up to there and {@code sequence} is the transaction's start among the snapshot numbers. The
 * changes committed after it, the transaction's own among them, stay unconsumed (live-verified).
 */
public final class StreamReadScope {

    private final long cut;
    private final long sequence;

    /**
     * @param cut      the stream's records the read ended at
     * @param sequence the snapshot number the reading transaction began at
     */
    public StreamReadScope(final long cut, final long sequence) {
        this.cut = cut;
        this.sequence = sequence;
    }

    public long getCut() {
        return cut;
    }

    public long getSequence() {
        return sequence;
    }
}
