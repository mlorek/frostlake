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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

/**
 * The schema or table a {@code SHOW PRIMARY | UNIQUE | IMPORTED KEYS} scope names, judged before anything is listed
 * (live-verified):
 *
 * <pre>
 *   IN SCHEMA s, IN SCHEMA "s"        Must specify the full search path starting from database for S (for s) —
 *                                     a one-part schema name, even an existing schema's
 *   IN nosuch, IN TABLE nosuch        Table 'NOSUCH' does not exist or not authorized.
 *   IN "nosuch", IN IDENTIFIER('x')   Table '"nosuch"' …, Table 'X' … — a one-part name as written
 *   IN public.nosuch                  Table 'DB.PUBLIC.NOSUCH' … — a path is named in full
 *   IN v, IN dt                       no rows: a view, a materialized view or a dynamic table holds no key
 *   IN s                              show [constraint] command doesn't support this domain. (a stream)
 * </pre>
 *
 * <p>A name held by another kind outside the relations' name space, a sequence or a stage, is a missing table.
 */
final class ShowKeysScope {

    private ShowKeysScope() {
    }

    /**
     * Refuse a scope that names no listable schema or table, or tell whether it lists nothing.
     *
     * @param ctx           the statement
     * @param scopeKind     the scope as the listing reads it: ACCOUNT, DATABASE, SCHEMA or TABLE
     * @param named         whether the scope names its object (a TABLE scope may name none, or a lone VIEW)
     * @param catalog       the catalog the names resolve in
     * @param queryExecutor the executor that resolves an IDENTIFIER() name
     * @return whether the scope names a relation that holds no key, so that nothing is listed
     */
    static boolean listsNothing(final FrostlakeParser.ShowStatementContext ctx, final String scopeKind,
                                final boolean named, final Catalog catalog, final QueryExecutor queryExecutor) {
        if (!named || ctx.objectName() == null) {
            return false;
        }
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        if ("SCHEMA".equals(scopeKind)) {
            if (parts.length == 1) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Must specify the full search path starting from database for " + parts[0]));
            }
            return false;
        }
        if (!"TABLE".equals(scopeKind)) {
            return false;
        }
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final RelationKind kind = owner.relationKindOf(name);
        if (kind == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table",
                parts.length == 1 ? QualifiedName.join(name) : owner.qualifiedName(name)));
        }
        if (kind == RelationKind.STREAM) {
            throw new RuntimeException(SqlCompilationError.inline(
                "show [constraint] command doesn't support this domain."));
        }
        return kind != RelationKind.TABLE;
    }
}
