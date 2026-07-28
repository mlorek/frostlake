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

import dev.frostlake.metastore.model.StreamRecord;

import java.util.List;

/**
 * What a consuming DML's stream read actually SAW, captured at read time so the commit can advance the
 * stream past exactly that — and nothing more. {@code committedCut} is the stream's record count at the
 * read (every committed record before it was visible); {@code seenTransient} are the transaction's own
 * buffered changes folded into the read (synthesized by
 * {@link TransactionWriteSet#bufferedChangeRecords}), which the commit will re-emit as real records.
 * Changes the transaction buffers AFTER the read are not in either part, so they stay unconsumed —
 * mirroring Snowflake, where the flush-then-load pattern runs each statement in its own transaction and
 * a stream read never consumes changes made after it.
 */
public final class StreamReadScope {

    private final long committedCut;
    private final List<StreamRecord> seenTransient;

    public StreamReadScope(final long committedCut, final List<StreamRecord> seenTransient) {
        this.committedCut = committedCut;
        this.seenTransient = seenTransient;
    }

    public long getCommittedCut() {
        return committedCut;
    }

    public List<StreamRecord> getSeenTransient() {
        return seenTransient;
    }
}
