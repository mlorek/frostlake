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
import dev.frostlake.storage.Row;

import java.util.List;

// Helper class to hold table data with metadata
class TableData {
    Table table;
    List<Row> rows;
    String alias;

    TableData(final Table table, final List<Row> rows, final String alias) {
        this.table = table;
        this.rows = rows;
        this.alias = alias;
    }
}
