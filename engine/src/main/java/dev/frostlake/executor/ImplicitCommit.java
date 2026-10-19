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

import dev.frostlake.parser.FrostlakeParser;

/**
 * Which DDL statements end the transaction they are issued in (live-verified). Creating a table or a view does —
 * whatever kind of table, from a query, LIKE another or as its clone — and so do ALTER, DROP and COMMENT of any
 * kind of object, a DROP … IF EXISTS that finds nothing included. Creating a sequence, a function, a procedure, a
 * schema, a stage, a file format, a stream, a tag, a masking or a row access policy, a task or a pipe does not:
 * the transaction is still open afterwards, as it is after ALTER STAGE, ALTER FILE FORMAT, ALTER SESSION, a
 * rename of a table, of a table's column or of a view, TRUNCATE and USE.
 *
 * <p>A statement that ends the transaction commits it where the transaction was begun, and is refused in a block
 * or a procedure called while its caller's transaction is open; one that does not runs in both places.
 */
final class ImplicitCommit {

    private ImplicitCommit() {
    }

    /**
     * Whether the statement ends an open transaction.
     *
     * @param ctx the DDL statement
     * @return true when it commits the transaction before it runs
     */
    static boolean ends(final FrostlakeParser.DdlStatementContext ctx) {
        if (ctx.truncateStatement() != null || ctx.useStatement() != null) {
            return false;
        }
        final FrostlakeParser.AlterStatementContext alter = ctx.alterStatement();
        if (alter != null) {
            return alter.SESSION() == null && alter.STAGE() == null && alter.FORMAT() == null && !renames(alter);
        }
        final FrostlakeParser.CreateStatementContext create = ctx.createStatement();
        return create == null || !leavesTheTransactionOpen(create);
    }

    /**
     * A rename of a table, of a table's column or of a view, which leaves the transaction open whether it renames or
     * is refused.
     */
    private static boolean renames(final FrostlakeParser.AlterStatementContext alter) {
        final FrostlakeParser.TableActionContext table = alter.TABLE() == null ? null : alter.tableAction();
        if (table != null) {
            return table.RENAME() != null && table.CONSTRAINT() == null;
        }
        final FrostlakeParser.ViewActionContext view = alter.VIEW() == null || alter.MATERIALIZED() != null ? null
            : alter.viewAction();
        return view != null && view.RENAME() != null;
    }

    /** A CREATE of one of the kinds that leave the transaction as it was. */
    private static boolean leavesTheTransactionOpen(final FrostlakeParser.CreateStatementContext create) {
        return create.SEQUENCE() != null || create.FUNCTION() != null || create.PROCEDURE() != null
            || create.SCHEMA() != null || create.STAGE() != null || create.FORMAT() != null
            || create.STREAM() != null || create.TAG() != null || create.MASKING() != null
            || create.ACCESS() != null || create.TASK() != null || create.PIPE() != null;
    }
}
