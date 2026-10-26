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

import dev.frostlake.metastore.model.Table;

import java.util.List;

/**
 * The way one table's changes reach a stream on a view: the queries they pass through, the view's own query first,
 * and the table the last of them reads.
 */
final class ViewStreamChain {

    /** The queries, the view's own first and the one reading the table last. */
    final List<ViewStreamLevel> levels;
    /** The table. */
    final Table table;
    /** The table's name as the stream records it: as the view's query writes it, or in full below another query. */
    final String tableName;

    ViewStreamChain(final List<ViewStreamLevel> levels, final Table table, final String tableName) {
        this.levels = levels;
        this.table = table;
        this.tableName = tableName;
    }
}
