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

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.ExpressionPlace;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The point a change-tracking read starts or ends at — a stream's AT | BEFORE and a CHANGES clause's AT | BEFORE |
 * END. It may be any expression that answers the same each time it is asked: a text, a number, a variable, a
 * subquery, a call over them. A function whose value moves between two calls cannot anchor the changes, and is
 * refused where it is written, the sentence closing with "(inside a view)." when a query's CHANGES clause wrote it
 * (live-verified).
 *
 * <pre>
 *   AT (TIMESTAMP =&gt; DATEADD(minute, -30, CURRENT_TIMESTAMP()))   function 'CURRENT_TIMESTAMP', at the call
 *   AT (TIMESTAMP =&gt; CURRENT_DATE()::TIMESTAMP_LTZ)                function 'CURRENT_DATE'
 *   AT (OFFSET =&gt; -ABS(RANDOM()) % 2)                              function 'RANDOM'
 *   AT (STATEMENT =&gt; LAST_QUERY_ID())                              function 'LAST_QUERY_ID'
 *   AT (TIMESTAMP =&gt; SYSDATE()) | GETDATE() | SYSTIMESTAMP()       function 'CURRENT_TIMESTAMP', which each is
 *                                                                  written through, at that writing's own place
 *   AT (TIMESTAMP =&gt; TO_TIMESTAMP_LTZ('2020-01-01 00:00:00'))      read
 *   AT (OFFSET =&gt; -60 + 60), AT (OFFSET =&gt; (SELECT 0))             read
 * </pre>
 *
 * <p>A point reads no row. In a query a name it writes is no column of anything, and is refused where it stands:
 * {@code invalid identifier 'ID'}. A stream's point and a clone's are read over the table they are taken of, so a
 * column of that table is a column — and no constant, which the point must be: {@code argument TIMESTAMP to
 * function AT needs to be constant, found 'CAST(CT.ID AS TIMESTAMP_LTZ(0))'}, the argument named OFFSET for an
 * offset and JOBID for a statement, the function BEFORE for a BEFORE point (live-verified).
 */
final class ChangeTrackingPoints {

    /** The functions whose value moves, each under the name the refusal tells it by. */
    private static final Map<String, String> MOVING = new HashMap<>();

    /** The place the refusal tells for a function written through CURRENT_TIMESTAMP: its own, not the call's. */
    private static final Map<String, Integer> REWRITTEN_PLACE = new HashMap<>();

    static {
        for (final String name : new String[] {"CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "LOCALTIMESTAMP", "LOCALTIME", "RANDOM", "UUID_STRING", "LAST_QUERY_ID"}) {
            MOVING.put(name, name);
        }
        MOVING.put("SYSDATE", "CURRENT_TIMESTAMP");
        MOVING.put("GETDATE", "CURRENT_TIMESTAMP");
        MOVING.put("SYSTIMESTAMP", "CURRENT_TIMESTAMP");
        REWRITTEN_PLACE.put("SYSDATE", Integer.valueOf(35));
        REWRITTEN_PLACE.put("GETDATE", Integer.valueOf(1));
        REWRITTEN_PLACE.put("SYSTIMESTAMP", Integer.valueOf(1));
    }

    private ChangeTrackingPoints() {
    }

    /**
     * Refuses a point that reads a function whose value moves between two calls.
     *
     * @param point   the point
     * @param inQuery whether a query's CHANGES clause wrote it, rather than a stream's definition
     */
    static void requireRepeatable(final FrostlakeParser.TimeTravelPointContext point, final boolean inQuery) {
        refuseMoving(point, inQuery ? " (inside a view)." : "");
    }

    /**
     * Refuses a point of a CHANGES clause over a view that reads a function whose value moves, the sentence closing
     * with the view's name: {@code (inside view 'DB.S.V').} (live-verified).
     *
     * @param point    the point
     * @param viewName the view's name in full
     */
    static void requireRepeatable(final FrostlakeParser.TimeTravelPointContext point, final String viewName) {
        refuseMoving(point, " (inside view '" + viewName + "').");
    }

    /** Refuses a point that reads a function whose value moves, the sentence closed by {@code closing}. */
    private static void refuseMoving(final FrostlakeParser.TimeTravelPointContext point, final String closing) {
        if (point == null || point.STREAM() != null || point.expression() == null) {
            return;
        }
        final Token moving = firstMoving(point.expression());
        if (moving == null) {
            return;
        }
        final String written = moving.getText().toUpperCase(Locale.ROOT);
        final String detail = "Query contains time travel to a version containing function '" + MOVING.get(written)
            + "', but change tracking is not supported on queries with non-deterministic functions in versions."
            + closing;
        final Integer place = REWRITTEN_PLACE.get(written);
        if (place != null) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " line 1 at position " + place + ":\n" + detail);
        }
        throw new RuntimeException(SqlCompilationError.atLine(moving.getLine(), moving.getCharPositionInLine(),
            detail));
    }

    /**
     * Refuses a stream's or a clone's point that reads a column of the table it is taken of: the point must be a
     * constant. A point that reads none is left to its evaluation, which refuses a name where it stands.
     *
     * @param point     the point
     * @param before    whether it is a BEFORE point
     * @param source    the table the stream or the clone is taken of
     * @param functions the function registry, for printing the point as the plan holds it
     * @param catalog   the catalog
     */
    static void requireConstant(final FrostlakeParser.TimeTravelPointContext point, final boolean before,
                                final Table source, final FunctionRegistry functions, final Catalog catalog) {
        if (point == null || point.STREAM() != null || point.expression() == null || source == null) {
            return;
        }
        final Expression parsed;
        try {
            parsed = AntlrExpressionParser.parse(ParseTreeText.getOriginalText(point.expression()));
        } catch (final RuntimeException unreadable) {
            // What cannot be read is refused where it is evaluated.
            return;
        }
        boolean readsSource = false;
        for (final ColumnReferenceExpression reference : references(parsed)) {
            readsSource |= (!reference.isQualified() || source.getName().equalsIgnoreCase(reference.getTableName()))
                && source.hasColumn(reference.getColumnName());
        }
        if (!readsSource) {
            // Nothing of the table is read: the point is evaluated as any point is, a name refused where it stands.
            return;
        }
        // Every name is resolved over the table before the point is judged: one that is no column is refused first.
        final Token first = point.expression().getStart();
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(first.getLine(), first.getCharPositionInLine()));
        try {
            new ExpressionEvaluator(source, functions, catalog).validateColumnScope(parsed);
        } finally {
            ExpressionSource.end(displaced);
        }
        final String argument = point.TIMESTAMP() != null ? "TIMESTAMP" : point.OFFSET() != null ? "OFFSET" : "JOBID";
        final String found = new ExpressionEvaluatorVisitor(source, null, functions, catalog).strictPlanText(parsed);
        throw new RuntimeException(SqlCompilationError.of("argument " + argument + " to function "
            + (before ? "BEFORE" : "AT") + " needs to be constant, found '" + found + "'"));
    }

    /**
     * A query's point that could not be read because it writes a name: the refusal told where the name stands.
     * Any other failure is handed back as it is.
     *
     * @param expression the point's expression
     * @param unreadable what reading it threw
     * @return the refusal to throw
     */
    static RuntimeException placed(final FrostlakeParser.ExpressionContext expression,
                                   final RuntimeException unreadable) {
        final String told = unreadable.getMessage();
        if (expression == null || told == null || !told.equals(SqlCompilationError.invalidIdentifier(named(told)))) {
            return unreadable;
        }
        final Expression parsed;
        try {
            parsed = AntlrExpressionParser.parse(ParseTreeText.getOriginalText(expression));
        } catch (final RuntimeException unparsed) {
            return unreadable;
        }
        final List<ColumnReferenceExpression> references = references(parsed);
        for (final ColumnReferenceExpression reference : references) {
            if (spelled(reference).equals(named(told))) {
                return placedAt(expression, reference);
            }
        }
        return references.isEmpty() ? unreadable : placedAt(expression, references.get(0));
    }

    /** The name an invalid-identifier sentence tells, or the empty text. */
    private static String named(final String told) {
        final int open = told.indexOf('\'');
        final int close = told.lastIndexOf('\'');
        return open >= 0 && close > open ? told.substring(open + 1, close) : "";
    }

    private static List<ColumnReferenceExpression> references(final Expression parsed) {
        final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
        parsed.accept(walk);
        return walk.references();
    }

    private static String spelled(final ColumnReferenceExpression reference) {
        return (reference.isQualified() ? reference.getTableName() + "." : "").toUpperCase(Locale.ROOT)
            + reference.getColumnName().toUpperCase(Locale.ROOT);
    }

    /** The invalid-identifier refusal of a name, placed where it stands in the statement. */
    private static RuntimeException placedAt(final FrostlakeParser.ExpressionContext expression,
                                             final ColumnReferenceExpression reference) {
        final Token first = expression.getStart();
        final SourcePosition origin = ExpressionSource.place(first.getLine(), first.getCharPositionInLine());
        final SourcePosition within = ExpressionPlace.of(reference);
        final int line = within == null ? origin.getLine() : origin.getLine() + within.getLine() - 1;
        final int column = within == null ? origin.getCharPositionInLine()
            : within.getLine() == 1 ? origin.getCharPositionInLine() + within.getCharPositionInLine()
            : within.getCharPositionInLine();
        return new RuntimeException(SqlCompilationError.invalidIdentifier(line, column, spelled(reference)));
    }

    /** The first word under a tree that names a moving function, or null. */
    private static Token firstMoving(final ParseTree tree) {
        if (tree instanceof TerminalNode) {
            final Token token = ((TerminalNode) tree).getSymbol();
            return MOVING.containsKey(token.getText().toUpperCase(Locale.ROOT)) ? token : null;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            final Token found = firstMoving(tree.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
