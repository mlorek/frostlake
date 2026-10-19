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

/**
 * A row of a view that joins its tables, with its identity: the digest of the identities of the table rows it joins
 * (see {@code StreamRowIds.ofJoinedRows}), which outlives an UPDATE of either.
 */
final class JoinedViewRow {

    /** The row's id, as METADATA$ROW_ID spells it. */
    final String id;
    /** The view's values for the row. */
    final Row row;

    JoinedViewRow(final String id, final Row row) {
        this.id = id;
        this.row = row;
    }
}
