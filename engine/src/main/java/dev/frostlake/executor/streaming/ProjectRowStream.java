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

/** Applies a per-row transform (the streaming projection) to its source. */
public class ProjectRowStream implements RowStream {

    private final RowStream source;
    private final RowMapper mapper;

    public ProjectRowStream(final RowStream source, final RowMapper mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public Row next() {
        final Row row = source.next();
        if (row == null) {
            return null;
        }
        return mapper.map(row);
    }

    @Override
    public void close() {
        source.close();
    }
}
