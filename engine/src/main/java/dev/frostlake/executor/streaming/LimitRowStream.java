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

package dev.frostlake.executor.streaming;

import dev.frostlake.storage.Row;

/**
 * Skips {@code offset} rows then yields at most {@code limit} rows, returning {@code null} — and not
 * pulling any further from its source — once the limit is reached. This early stop is what lets a
 * streaming pipeline avoid materializing the full upstream for a {@code LIMIT} query.
 */
public class LimitRowStream implements RowStream {

    private final RowStream source;
    private final long limit;
    private final long offset;
    private long produced;
    private boolean offsetSkipped;

    public LimitRowStream(final RowStream source, final long limit, final long offset) {
        this.source = source;
        this.limit = limit;
        this.offset = offset;
    }

    @Override
    public Row next() {
        if (!offsetSkipped) {
            for (long i = 0; i < offset; i++) {
                if (source.next() == null) {
                    offsetSkipped = true;
                    return null;
                }
            }
            offsetSkipped = true;
        }
        if (produced >= limit) {
            return null;   // limit reached — stop pulling from the source (short-circuit)
        }
        final Row row = source.next();
        if (row == null) {
            return null;
        }
        produced++;
        return row;
    }

    @Override
    public void close() {
        source.close();
    }
}
