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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Which DDL statements end the transaction they are issued in (live-verified). Creating a table or a view does —
 * whatever kind of table, from a query, LIKE another or as its clone — and so does creating a contact; ALTER, DROP
 * and COMMENT of any kind of object do too, a DROP … IF EXISTS that finds nothing included. Creating a sequence, a
 * function, a procedure, a schema, a stage, a file format, a stream, a tag, a policy of any kind, a task, a pipe, an
 * alert, a notebook or a Streamlit does not: the transaction is still open afterwards, as it is after ALTER STAGE,
 * ALTER FILE FORMAT, ALTER SESSION, an ALTER or a COMMENT of a contact, a rename of any kind — of an object, of a
 * table's column or of a constraint, renamed or refused — TRUNCATE and USE. A session with AUTOCOMMIT off ends the
 * transaction it is in by the same statements.
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
            return alter.SESSION() == null && alter.STAGE() == null && alter.FORMAT() == null
                && alter.CONTACT() == null && !renames(alter);
        }
        if (ctx.commentStatement() != null) {
            return ctx.commentStatement().CONTACT() == null;
        }
        if (ctx.notebookStatement() != null) {
            return ctx.notebookStatement().CREATE() == null && ctx.notebookStatement().RENAME() == null;
        }
        if (ctx.streamlitStatement() != null) {
            return ctx.streamlitStatement().CREATE() == null && ctx.streamlitStatement().RENAME() == null;
        }
        final FrostlakeParser.CreateStatementContext create = ctx.createStatement();
        return create == null || !leavesTheTransactionOpen(create);
    }

    /**
     * A rename, which leaves the transaction open whether it renames or is refused: the action the statement ends
     * with, whatever the kind of object, holds the word RENAME among its own — RENAME TO, a table's RENAME COLUMN
     * and RENAME CONSTRAINT.
     */
    private static boolean renames(final FrostlakeParser.AlterStatementContext alter) {
        ParserRuleContext action = null;
        for (final ParseTree child : alter.children) {
            if (child instanceof ParserRuleContext) {
                action = (ParserRuleContext) child;
            }
        }
        return action != null && !(action instanceof FrostlakeParser.IdentifierContext)
            && action.getToken(FrostlakeParser.RENAME, 0) != null;
    }

    /** A CREATE of one of the kinds that leave the transaction as it was. */
    private static boolean leavesTheTransactionOpen(final FrostlakeParser.CreateStatementContext create) {
        return create.SEQUENCE() != null || create.FUNCTION() != null || create.PROCEDURE() != null
            || create.SCHEMA() != null || create.STAGE() != null || create.FORMAT() != null
            || create.STREAM() != null || create.TAG() != null || create.POLICY() != null
            || create.TASK() != null || create.PIPE() != null || create.ALERT() != null;
    }
}
