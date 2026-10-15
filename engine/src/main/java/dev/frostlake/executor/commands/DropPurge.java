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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Locale;

/**
 * The PURGE a DROP may end in where CASCADE or RESTRICT stand. The account reads the word after a DROP of any kind
 * and refuses it for nearly all of them, in one sentence that speaks of a table whatever the statement drops:
 * {@code DROP VIEW v PURGE} and {@code DROP SEQUENCE IF EXISTS s PURGE} both answer {@code DROP TABLE ... PURGE is
 * only supported for unmanaged Iceberg tables in Catalog-Linked Databases. Table: 'V'.} The name is the one written,
 * qualified as far as it was written. The sentence comes once the name has resolved: a missing database or schema is
 * refused first, and so is a missing object unless IF EXISTS forgives it — the sentence then stands in place of the
 * skipped drop. So it is for the kinds of the account — a warehouse, a compute pool, a user, a role — and for a
 * database role. A file format, a contact, a secret, a network rule and a network policy take the word and drop as
 * if it were not there (live-verified).
 */
final class DropPurge {

    private DropPurge() {
    }

    /**
     * Whether a DROP ends in PURGE.
     *
     * @param behavior the word after the object's name, or null
     * @return true for PURGE
     */
    static boolean written(final FrostlakeParser.DropBehaviorContext behavior) {
        return behavior != null && behavior.PURGE() != null;
    }

    /**
     * The account's refusal of a PURGE.
     *
     * @param nameParts the object's name as written, part by part
     * @return the refusal
     */
    static RuntimeException refusal(final String[] nameParts) {
        return new RuntimeException("SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg"
            + " tables in Catalog-Linked Databases. Table: '"
            + SqlIdentifiers.spellAlreadyCanonicalPath(QualifiedName.join(nameParts)) + "'.");
    }

    /**
     * Whether the object a DROP names is there, so that its PURGE is what the statement is refused for.
     *
     * @param ctx      the DROP
     * @param parts    the object's name, part by part
     * @param executor the executor resolving names
     * @param catalog  the catalog
     * @return false when the object is missing, and the statement's own refusal of that stands
     */
    static boolean holds(final FrostlakeParser.DropStatementContext ctx, final String[] parts,
                         final QueryExecutor executor, final Catalog catalog) {
        try {
            if (ctx.CORTEX() != null) {
                return owner(parts, catalog).hasCortexSearchServiceExact(parts[parts.length - 1]);
            }
            if (ctx.JOIN() != null) {
                return owner(parts, catalog).hasJoinPolicy(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            }
            if (ctx.AGGREGATION() != null) {
                return owner(parts, catalog).hasAggregationPolicy(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            }
            if (ctx.PROJECTION() != null) {
                return owner(parts, catalog).hasProjectionPolicy(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            }
            final Integer arity = ctx.FUNCTION() == null && ctx.PROCEDURE() == null ? null
                : Integer.valueOf(ctx.dataTypeList() == null ? 0 : ctx.dataTypeList().COMMA().size() + 1);
            return executor.grantedObject(kindOf(ctx), parts, arity) != null;
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    private static Schema owner(final String[] parts, final Catalog catalog) {
        return catalog.requireOwningSchema(QualifiedName.of(parts));
    }

    /** The kind a DROP names, as the object lookup spells it. */
    private static String kindOf(final FrostlakeParser.DropStatementContext ctx) {
        if (ctx.DATABASE() != null) {
            return "DATABASE";
        }
        if (ctx.SCHEMA() != null) {
            return "SCHEMA";
        }
        if (ctx.TABLE() != null) {
            return "TABLE";
        }
        if (ctx.VIEW() != null) {
            return "VIEW";
        }
        if (ctx.STREAM() != null) {
            return "STREAM";
        }
        if (ctx.TASK() != null) {
            return "TASK";
        }
        if (ctx.PIPE() != null) {
            return "PIPE";
        }
        if (ctx.SEQUENCE() != null) {
            return "SEQUENCE";
        }
        if (ctx.STAGE() != null) {
            return "STAGE";
        }
        if (ctx.TAG() != null) {
            return "TAG";
        }
        if (ctx.FUNCTION() != null) {
            return "FUNCTION";
        }
        if (ctx.PROCEDURE() != null) {
            return "PROCEDURE";
        }
        return ctx.MASKING() != null ? "MASKING_POLICY" : "ROW_ACCESS_POLICY";
    }
}
