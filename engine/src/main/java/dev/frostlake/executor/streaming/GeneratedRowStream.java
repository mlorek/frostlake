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

import dev.frostlake.executor.StatementCancellation;
import dev.frostlake.storage.Row;

import java.util.ArrayList;

/**
 * The rows of a GENERATOR produced one at a time, as many as it stands for: each a row of no column, none held once
 * read. The statement's timeout or cancel ends the stream where it stands.
 */
public final class GeneratedRowStream implements RowStream {

    /** How many rows are produced between two looks at the statement's cancellation. */
    private static final long CHECK_EVERY = 4096;

    private final long rows;
    private long produced;

    /**
     * @param rows the rows the stream produces
     */
    public GeneratedRowStream(final long rows) {
        this.rows = rows;
    }

    @Override
    public Row next() {
        if (produced >= rows) {
            return null;
        }
        produced++;
        if (produced % CHECK_EVERY == 0) {
            StatementCancellation.check();
        }
        return new Row(new ArrayList<Object>());
    }

    @Override
    public void close() {
        // nothing to release
    }
}
