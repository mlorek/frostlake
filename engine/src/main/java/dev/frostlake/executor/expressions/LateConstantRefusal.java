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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.SqlCompilationError;

/**
 * The refusal live raises last of all while it compiles a statement: GETVARIABLE's "argument 0 … needs to be
 * constant". Every other compile-time refusal of the statement comes first — an argument count or an argument type
 * anywhere in it, a generator's constant argument, a predicate type, a position past the select list, an aggregate
 * or a window where none may stand, the grouped select list, a QUALIFY without a window, a subquery's own refusal of
 * any kind, and a later set-operation arm's — so the refusal is recorded where the walk meets it and raised once
 * the outermost query of the statement has been judged (live-verified). Of two, the one written first speaks.
 *
 * <p>A thread-local scope: {@link #begin} and {@link #end} bracket the outermost query, nested queries share it,
 * and {@link #raise} raises what was recorded when called by that outermost query. Outside any scope a refusal is
 * not recorded, and its caller raises it at once.
 */
public final class LateConstantRefusal {
    private static final ThreadLocal<LateConstantRefusal> CURRENT = new ThreadLocal<LateConstantRefusal>();

    private int depth;
    private int rowValueDepth;
    private int probeDepth;
    private RuntimeException refusal;
    private int line;
    private int column;

    private LateConstantRefusal() {
    }

    /** Enter a query; the first entry opens the statement's scope. */
    public static void begin() {
        LateConstantRefusal scope = CURRENT.get();
        if (scope == null) {
            scope = new LateConstantRefusal();
            CURRENT.set(scope);
        }
        scope.depth++;
    }

    /** Leave a query; leaving the outermost one closes the scope and forgets what it recorded. */
    public static void end() {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope == null) {
            return;
        }
        scope.depth--;
        if (scope.depth <= 0) {
            CURRENT.remove();
        }
    }

    /**
     * Enter a subquery's computation of its value for a row. The statement's compilation already walked it, so
     * what it records here only counts when nothing was recorded before: a row's walk places the call less surely.
     */
    public static void beginRowValue() {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope != null) {
            scope.rowValueDepth++;
        }
    }

    /**
     * Enter a plan made only to read a subquery's shape — its type, its column count — whose own refusals are
     * dropped by whoever asked: nothing it meets is recorded, since the statement's compilation meets it again.
     */
    public static void beginProbe() {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope != null) {
            scope.probeDepth++;
        }
    }

    /** Leave what {@link #beginProbe} entered. */
    public static void endProbe() {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope != null && scope.probeDepth > 0) {
            scope.probeDepth--;
        }
    }

    /** Leave what {@link #beginRowValue} entered. */
    public static void endRowValue() {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope != null && scope.rowValueDepth > 0) {
            scope.rowValueDepth--;
        }
    }

    /** Raise the recorded refusal, when the caller is the statement's outermost query and one was recorded. */
    public static void raise() {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope != null && scope.depth == 1 && scope.refusal != null) {
            final RuntimeException recorded = scope.refusal;
            scope.refusal = null;
            throw recorded;
        }
    }

    /**
     * The refusal the statement answers with when {@code refused} stopped its outermost query: the recorded one when
     * {@code refused} is no compilation error — a value's fault while the rows were computed comes after everything
     * the statement's compilation refuses — and {@code refused} otherwise.
     */
    public static RuntimeException ahead(final RuntimeException refused) {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope == null || scope.depth != 1 || scope.refusal == null
                || SqlCompilationError.isCompilationError(refused.getMessage())) {
            return refused;
        }
        final RuntimeException recorded = scope.refusal;
        scope.refusal = null;
        return recorded;
    }

    /**
     * Record a refusal to raise when the statement has been judged, keeping the one written first.
     *
     * @param refused the refusal
     * @param at      where the refused call stands in the statement, or null when unknown
     * @return false when no statement scope is open, and the caller must raise the refusal itself
     */
    static boolean record(final RuntimeException refused, final SourcePosition at) {
        final LateConstantRefusal scope = CURRENT.get();
        if (scope == null) {
            return false;
        }
        if (scope.probeDepth > 0 || scope.rowValueDepth > 0 && scope.refusal != null) {
            return true;
        }
        final int atLine = at != null ? at.getLine() : Integer.MAX_VALUE;
        final int atColumn = at != null ? at.getCharPositionInLine() : Integer.MAX_VALUE;
        if (scope.refusal == null || atLine < scope.line || atLine == scope.line && atColumn < scope.column) {
            scope.refusal = refused;
            scope.line = atLine;
            scope.column = atColumn;
        }
        return true;
    }
}
