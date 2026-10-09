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

import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Table;

/**
 * What a DML statement may write. Its target has to be a table: a view, a secure view or a stream named there is
 * refused with {@code INSERT statement's target must be a table} — the statement's own word in front, and
 * {@code INSERT (MULTI-TABLE)} for an INSERT ALL or FIRST — and a materialized view with {@code INSERT operations
 * are not allowed on materialized views}. A COPY INTO a table is an INSERT in these words. An event table takes
 * no INSERT, UPDATE or COPY and no MERGE that inserts or updates, in the same sentence; a DELETE, a TRUNCATE and
 * a MERGE that only deletes run. The target is judged before anything else of the statement — a column it does
 * not have is never reached (all live-verified).
 */
final class DmlTarget {

    private DmlTarget() {
    }

    /**
     * Refuse a target that names a relation which is no table.
     *
     * @param executor  the executor, for the catalog's relations
     * @param name      the target as the statement resolves it
     * @param statement the statement's word: INSERT, INSERT (MULTI-TABLE), UPDATE, DELETE or MERGE
     */
    static void requireTable(final QueryExecutor executor, final String name, final String statement) {
        if (executor.getCatalog().hasTableAsWritten(QualifiedName.parse(name))) {
            return;
        }
        final String kind = executor.storedRelationKind(name);
        if ("VIEW".equals(kind) || "STREAM".equals(kind)) {
            throw new RuntimeException(SqlCompilationError.of(statement + " statement's target must be a table"));
        }
        if ("MATERIALIZED_VIEW".equals(kind)
                && ("INSERT".equals(statement) || "UPDATE".equals(statement) || "DELETE".equals(statement))) {
            throw new RuntimeException(SqlCompilationError.of(
                statement + " operations are not allowed on materialized views"));
        }
    }

    /**
     * Refuse a statement that writes rows into an event table.
     *
     * @param table     the target
     * @param statement the statement's word: INSERT, INSERT (MULTI-TABLE), UPDATE or MERGE
     */
    static void refuseEventTable(final Table table, final String statement) {
        if (table != null && table.isEventTable()) {
            throw new RuntimeException(SqlCompilationError.of(statement + " statement's target must be a table"));
        }
    }
}
