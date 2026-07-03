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

/** Yields only the rows from its source that satisfy a predicate (the streaming WHERE). */
public class FilterRowStream implements RowStream {

    private final RowStream source;
    private final RowPredicate predicate;

    public FilterRowStream(final RowStream source, final RowPredicate predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    @Override
    public Row next() {
        Row row = source.next();
        while (row != null) {
            if (predicate.test(row)) {
                return row;
            }
            row = source.next();
        }
        return null;
    }

    @Override
    public void close() {
        source.close();
    }
}
