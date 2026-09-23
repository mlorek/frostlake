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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Map;

/**
 * The bare names a block compile cannot resolve, held back until everything else the compile judges has
 * been judged, and then reported in the order the account reports them.
 *
 * <p>The account resolves a block's scripting names first — every {@code :bind}, every assignment target,
 * every cursor and exception name — and the type an untyped declaration infers, all in source order over
 * the whole block, nested blocks, branches, loops and handlers included. Only then does it resolve the bare
 * names of the block's expressions, one statement list at a time: the declarations of a list first (its
 * DECLARE items and its LETs, typed or not, in source order), then its other statements in source order,
 * a statement's own parts in the order written too — except a REPEAT, whose UNTIL condition comes before its
 * body (all live-verified): {@code RETURN missing; LET y NUMBER := :nosuch} reports 'nosuch', {@code RETURN
 * missing; LET x NUMBER := missing2} reports 'MISSING2', {@code IF (TRUE) THEN RETURN missing; ELSEIF
 * (missing2) …} reports 'MISSING', and {@code REPEAT RETURN missing; UNTIL (missing2) END REPEAT} reports
 * 'MISSING2'.
 */
final class DeferredNameRefusals {

    /** The held-back names of the block compile in progress on this thread, or null outside one. */
    private static final ThreadLocal<DeferredNameRefusals> CURRENT = new ThreadLocal<DeferredNameRefusals>();

    /** The unresolved names of the block being compiled, each with its refusal. */
    private final Map<ParseTree, String> unresolved = new HashMap<ParseTree, String>();

    /**
     * Start holding back the names of a block compile.
     *
     * @return the compile this one displaced, for {@link #end}
     */
    static DeferredNameRefusals begin() {
        final DeferredNameRefusals previous = CURRENT.get();
        CURRENT.set(new DeferredNameRefusals());
        return previous;
    }

    /**
     * The names held back by the block compile in progress.
     *
     * @return the holder, or null when no block compile is in progress
     */
    static DeferredNameRefusals current() {
        return CURRENT.get();
    }

    /**
     * Stop holding back names, restoring the compile {@link #begin} displaced.
     *
     * @param previous what {@link #begin} returned
     */
    static void end(final DeferredNameRefusals previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /**
     * Hold back the refusal of a name the scope cannot resolve.
     *
     * @param name    the name as parsed
     * @param refusal the refusal to raise if nothing else is reported first
     */
    void defer(final ParseTree name, final String refusal) {
        unresolved.put(name, refusal);
    }

    /**
     * Refuse the first held-back name in the account's order, or return when none was held back.
     *
     * @param block the block that was compiled
     */
    void raiseFirst(final ParseTree block) {
        if (unresolved.isEmpty()) {
            return;
        }
        final String first = firstIn(block);
        if (first != null) {
            throw new RuntimeException(first);
        }
    }

    private String firstIn(final ParseTree node) {
        final String refusal = unresolved.get(node);
        if (refusal != null) {
            return refusal;
        }
        if (node instanceof FrostlakeParser.StatementListContext) {
            final FrostlakeParser.StatementListContext list = (FrostlakeParser.StatementListContext) node;
            for (final FrostlakeParser.StatementContext statement : list.statement()) {
                if (declares(statement)) {
                    final String found = firstIn(statement);
                    if (found != null) {
                        return found;
                    }
                }
            }
            for (final FrostlakeParser.StatementContext statement : list.statement()) {
                if (!declares(statement)) {
                    final String found = firstIn(statement);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
        if (node instanceof FrostlakeParser.RepeatStatementContext) {
            // A REPEAT's condition is resolved ahead of its body.
            final String condition = firstIn(((FrostlakeParser.RepeatStatementContext) node).booleanExpr());
            if (condition != null) {
                return condition;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final String found = firstIn(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether a statement of a list is a LET, whose names the account resolves ahead of the list's others. */
    private static boolean declares(final FrostlakeParser.StatementContext statement) {
        return statement.proceduralStatement() != null && statement.proceduralStatement().letStatement() != null;
    }
}
