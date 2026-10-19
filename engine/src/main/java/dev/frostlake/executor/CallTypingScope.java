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

import java.util.Map;

/**
 * The relations a clause reads, for typing a call's arguments while the clause is judged (see
 * {@code QueryExecutor.callChoosesAggregate}).
 */
final class CallTypingScope {

    private final Table table;
    private final Map<String, Table> aliasToTable;

    /**
     * @param table        the relation the clause reads
     * @param aliasToTable the clause's relations by alias, or null
     */
    CallTypingScope(final Table table, final Map<String, Table> aliasToTable) {
        this.table = table;
        this.aliasToTable = aliasToTable;
    }

    /** The relation the clause reads. */
    Table table() {
        return table;
    }

    /** The clause's relations by alias, or null. */
    Map<String, Table> aliases() {
        return aliasToTable;
    }
}
