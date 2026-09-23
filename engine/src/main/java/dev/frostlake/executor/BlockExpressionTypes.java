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

import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argument types of a block's own expression, judged as SQL judges them before the expression runs: an
 * operator or a call given arguments whose types it does not take is refused in SQL's words — {@code
 * TO_DATE('2024-01-01') + TRUE} is {@code Invalid argument types for function '+': (DATE, BOOLEAN)} and {@code
 * TO_DATE('2024-01-01') = TRUE} {@code Can not convert parameter 'TRUE' of type [BOOLEAN] into expected type
 * [DATE]} — where Frostlake used to answer or fail in its own words (all live-verified).
 *
 * <p>The account compiles the expression with every variable written out as its value's type, so a variable
 * standing before the refused operator on its line moves the place: a name {@code x} reads {@code (x)::TYPE},
 * a FOR counter {@code (:i)::TYPE} and a bind {@code :x} reads {@code ?::TYPE}, spelled as a refusal spells the
 * declared type, and a text takes no cast at all ({@code (x)}, {@code ?}); inside an expression that holds a
 * subquery a name reads {@code (:x)::TYPE}, and so does every name of a stored procedure's body and of a block
 * whose outermost DECLARE section declares a cursor or a RESULTSET with a query. A text variable's type is its
 * value's width, as a bound value's is. The place counts from the expression's own start, after a lead-in on its
 * first line: seven columns when the expression holds a subquery (the account compiles it as a query), and
 * eight more for a value given to a variable — a LET, an assignment, a DECLARE default; a later line keeps its
 * own columns (all live-verified).
 *
 * <p>A refusal that echoes the expression — a comparison's conversion, a declared type's cast — writes each
 * variable as the conversion of its bound value (see {@link BoundValueRendering}): {@code LET n := 5; LET x DATE
 * := n + 1} is {@code invalid type [CAST((TO_NUMBER('5')) + 1 AS DATE)]} (live-verified).
 *
 * <p>Where it is judged decides how it is reported. A RETURN's value, a condition and a typed or assigned
 * value are judged when they are reached, and the refusal is that expression's EXPRESSION_ERROR. An untyped
 * declaration's initialiser and a CASE's operand are judged while the block compiles, before any of it runs
 * and on a branch that never runs too, and the refusal comes bare, placed in the block: at the expression's
 * start when the expression itself is refused, at the refused operator or call when it sits inside it.
 */
public final class BlockExpressionTypes {

    private static final Pattern PLACE = Pattern.compile("^SQL compilation error: error line (\\d+) at position (\\d+)");

    /** The lead-in of an expression the account compiles as a query: one that holds a subquery. */
    private static final int QUERY_LEAD = 7;

    /** The further lead-in of a value given to a variable. */
    private static final int ASSIGNED_VALUE_LEAD = 8;

    /** What a comparison's conversion refusal reads before its echo. */
    private static final String CONVERSION_ECHO = "Can not convert parameter '";

    /** What a declared type's cast refusal reads before its echo. */
    private static final String CAST_ECHO = "invalid type [CAST(";

    private BlockExpressionTypes() {
    }

    /**
     * Judge a block expression as it is reached: its argument types, then, for a value given to a variable of a
     * declared type, its conversion to the type. The conversion is the plain cast's, so a pair the cast matrix
     * refuses is refused in the cast's own words — {@code LET x DATE := TRUE} is {@code invalid type [CAST(TRUE
     * AS DATE)] for parameter 'TO_DATE'}, and a BOOLEAN into a TIMESTAMP, a TIME, a BINARY or a FLOAT, a number
     * into a DATE and a DATE into a BOOLEAN alike — while a pair the matrix does not govern keeps the conversion
     * it had (all live-verified). Either refusal is that expression's EXPRESSION_ERROR.
     *
     * @param expression    the expression as parsed in the block
     * @param types         the types of the names in scope, keyed as the resolver folds them
     * @param counters      the names in scope that are FOR counters, keyed alike
     * @param namesBound    whether every name of the block is written out as a bind (see the class comment)
     * @param target        the declared type the value converts to, or null for none
     * @param values        the values of the names in scope, keyed upper-cased
     * @param queryExecutor the executor whose functions and catalog type the expression
     */
    public static void judge(final ParserRuleContext expression, final Map<String, DataType> types,
                             final Set<String> counters, final boolean namesBound, final DataType target,
                             final Map<String, Object> values, final QueryExecutor queryExecutor) {
        // A call refused for its shape outranks every argument type and the conversion: ABS(ALL n) + TRUE is the
        // 'all' sentence (live-verified). The expression is left to its evaluation, which refuses the call itself.
        if (callShapeRefusal(sqlOf(expression), types, queryExecutor) != null) {
            return;
        }
        final String refusal = argumentTypeRefusal(expression, sqlOf(expression), types, queryExecutor);
        if (refusal != null) {
            String echoed = refusal;
            if (refusal.contains(CONVERSION_ECHO)) {
                final String rendered = argumentTypeRefusal(expression,
                    rendered(expression, types, values, true), types, queryExecutor);
                if (rendered != null && rendered.contains(CONVERSION_ECHO)) {
                    echoed = rendered;
                }
            }
            throw new BlockExpressionTypeError(placedInExpression(echoed, expression, types, counters, namesBound));
        }
        if (target != null) {
            final String conversion = conversionRefusal(expression, types, target, values, queryExecutor);
            if (conversion != null) {
                throw new BlockExpressionTypeError(conversion);
            }
        }
    }

    /**
     * The cast's refusal of the value's conversion to the declared type, or null when the matrix takes it. A value
     * that cannot be typed at all — an unknown function, a subquery that does not compile — is left to its
     * evaluation, which refuses it in its own words as the value's EXPRESSION_ERROR.
     */
    private static String conversionRefusal(final ParserRuleContext expression, final Map<String, DataType> types,
                                            final DataType target, final Map<String, Object> values,
                                            final QueryExecutor queryExecutor) {
        final Expression sql = sqlOf(expression);
        if (sql == null || queryExecutor == null) {
            return null;
        }
        final SourcePosition displaced = ExpressionSource.begin(new SourcePosition(1, 0));
        try {
            final ExpressionEvaluator typer = DeclarationTypes.scriptTyper(types, queryExecutor);
            if (!ExpressionEvaluatorVisitor.castMatrixRefuses(typer.inferStaticType(sql), target)) {
                return null;
            }
            final Expression rendered = rendered(expression, types, values, false);
            final String echoed = castRefusal(typer, rendered, target);
            return echoed != null ? echoed : castRefusal(typer, sql, target);
        } catch (final RuntimeException untyped) {
            final String message = untyped.getMessage();
            return SqlCompilationError.isCompilationError(message) && message.contains(CAST_ECHO) ? message : null;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** The plain cast's refusal of {@code operand} into {@code target}, or null when it takes it. */
    private static String castRefusal(final ExpressionEvaluator typer, final Expression operand, final DataType target) {
        if (operand == null) {
            return null;
        }
        try {
            typer.validateStrict(new CastExpression(operand, SqlTypeNames.refusalSpelling(target)));
            return null;
        } catch (final RuntimeException refused) {
            final String message = refused.getMessage();
            return SqlCompilationError.isCompilationError(message) && message.contains(CAST_ECHO) ? message : null;
        }
    }

    private static Expression sqlOf(final ParserRuleContext expression) {
        if (expression instanceof FrostlakeParser.BooleanExprContext) {
            return DeclarationTypes.sqlExpression((FrostlakeParser.BooleanExprContext) expression);
        }
        return expression instanceof FrostlakeParser.ExpressionContext
            ? DeclarationTypes.sqlExpression((FrostlakeParser.ExpressionContext) expression) : null;
    }

    /**
     * The expression as SQL with each variable it reads, by name or as a bind, written out as its bound value
     * (see {@link BoundValueRendering}); a variable whose rendering is not known keeps its name. Null when the
     * expression cannot be read as SQL.
     */
    private static Expression rendered(final ParserRuleContext expression, final Map<String, DataType> types,
                                       final Map<String, Object> values, final boolean planned) {
        if (expression == null || values == null) {
            return null;
        }
        try {
            return new ExpressionAstBuilder() {
                @Override
                public Expression visitQualifiedNameExpr(final FrostlakeParser.QualifiedNameExprContext ctx) {
                    final FrostlakeParser.QualifiedNameContext name = ctx.qualifiedName();
                    final Expression value = name.nameStartPart() != null && name.namePart().isEmpty()
                        ? renderingOf(name.getText(), types, values, planned) : null;
                    return value != null ? value : super.visitQualifiedNameExpr(ctx);
                }

                @Override
                public Expression visitBindVarExpr(final FrostlakeParser.BindVarExprContext ctx) {
                    final Expression value = ctx.identifier() == null ? null
                        : renderingOf(ctx.identifier().getText(), types, values, planned);
                    return value != null ? value : super.visitBindVarExpr(ctx);
                }
            }.visit(expression);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** The bound value of the variable {@code written} names, written out, or null when it is no variable. */
    private static Expression renderingOf(final String written, final Map<String, DataType> types,
                                          final Map<String, Object> values, final boolean planned) {
        final String key = SqlIdentifiers.canonicalText(written);
        final DataType type = types.get(key);
        final String held = key.toUpperCase(Locale.ROOT);
        if (type == null || "SQLROWCOUNT".equals(key) || !values.containsKey(held)) {
            return null;
        }
        return BoundValueRendering.of(type, values.get(held), planned);
    }

    /**
     * Judge an expression while the block compiles, refusing it bare, placed in the block.
     *
     * @param expression    the expression as parsed in the block
     * @param types         the types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     */
    public static void judgeCompiled(final ParserRuleContext expression, final Map<String, DataType> types,
                                     final QueryExecutor queryExecutor) {
        // A call refused for its shape outranks every argument type, on a branch that never runs too:
        // LET a := ABS(ALL 1) + TRUE is the bare 'all' sentence (live-verified).
        final String shape = callShapeRefusal(sqlOf(expression), types, queryExecutor);
        final String refusal = shape != null ? shape
            : argumentTypeRefusal(expression, sqlOf(expression), types, queryExecutor);
        if (refusal == null) {
            return;
        }
        final Matcher place = PLACE.matcher(refusal);
        if (place.find() && failsAtTheRoot(expression, Integer.parseInt(place.group(1)),
                Integer.parseInt(place.group(2)))) {
            final Token start = expression.getStart();
            throw new RuntimeException(placed(refusal, place, start.getLine(), start.getCharPositionInLine()));
        }
        throw new RuntimeException(refusal);
    }

    /**
     * SQL's refusal of a call the expression writes with a shape it does not take — a quantifier, a WITHIN GROUP or
     * named arguments (see {@link ExpressionEvaluator#validateCallShapes}) — placed in the block, or null for none.
     */
    private static String callShapeRefusal(final Expression sql, final Map<String, DataType> types,
                                           final QueryExecutor queryExecutor) {
        if (queryExecutor == null || sql == null) {
            return null;
        }
        final SourcePosition displaced = ExpressionSource.begin(new SourcePosition(1, 0));
        try {
            DeclarationTypes.scriptTyper(types, queryExecutor).validateCallShapes(sql);
            return null;
        } catch (final RuntimeException refused) {
            final String message = refused.getMessage();
            return SqlCompilationError.isCompilationError(message) ? message : null;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** SQL's refusal of the expression's argument types, placed in the block, or null when SQL takes them. */
    private static String argumentTypeRefusal(final ParserRuleContext expression, final Expression sql,
                                              final Map<String, DataType> types, final QueryExecutor queryExecutor) {
        if (expression == null || queryExecutor == null || sql == null) {
            return null;
        }
        // The parse tree is the block's own, so an origin at the block's start places each refusal in the block.
        final SourcePosition displaced = ExpressionSource.begin(new SourcePosition(1, 0));
        try {
            final ExpressionEvaluator typer = DeclarationTypes.scriptTyper(types, queryExecutor);
            typer.validateBooleanPositions(sql);
            typer.validateStrict(sql);
            // Typing the whole expression judges what the walks leave to it, a NOT's operand among them.
            typer.inferStaticType(sql);
            return null;
        } catch (final RuntimeException refused) {
            final String message = refused.getMessage();
            return SqlCompilationError.isCompilationError(message)
                && (message.contains("Invalid argument types for function '")
                    || message.contains(CONVERSION_ECHO)) ? message : null;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** A refusal placed in the block, moved into the expression as the account counts it. */
    private static String placedInExpression(final String refusal, final ParserRuleContext expression,
                                             final Map<String, DataType> types, final Set<String> counters,
                                             final boolean namesBound) {
        final Matcher place = PLACE.matcher(refusal);
        if (!place.find()) {
            return refusal;
        }
        final int line = Integer.parseInt(place.group(1));
        final int column = Integer.parseInt(place.group(2));
        final Token start = expression.getStart();
        final boolean query = holdsASubquery(expression);
        int moved = column + writtenOutShift(expression, line, column, types, counters, query || namesBound);
        if (line == start.getLine()) {
            moved += -start.getCharPositionInLine() + (query ? QUERY_LEAD : 0)
                + (assignsAValue(expression) ? ASSIGNED_VALUE_LEAD : 0);
        }
        return placed(refusal, place, line - start.getLine() + 1, moved);
    }

    private static String placed(final String refusal, final Matcher place, final int line, final int column) {
        return "SQL compilation error: error line " + line + " at position " + column + refusal.substring(place.end());
    }

    /** The columns the variables written out ahead of the place, on its line, add to it. */
    private static int writtenOutShift(final ParseTree node, final int line, final int column,
                                       final Map<String, DataType> types, final Set<String> counters,
                                       final boolean namesBound) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return 0;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name = ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            if (name.nameStartPart() == null || !name.namePart().isEmpty()) {
                return 0;
            }
            final String written = name.getText();
            final String key = SqlIdentifiers.canonicalText(written);
            final boolean bound = namesBound || counters.contains(key);
            return shiftOf(((ParserRuleContext) node).getStart(), line, column, types.get(key),
                written.length(), (bound ? "(:" : "(") + written + ")");
        }
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            if (bind.identifier() == null) {
                return 0;
            }
            final String written = bind.identifier().getText();
            return shiftOf(bind.getStart(), line, column, types.get(SqlIdentifiers.canonicalText(written)),
                written.length() + 1, "?");
        }
        int shift = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            shift += writtenOutShift(node.getChild(i), line, column, types, counters, namesBound);
        }
        return shift;
    }

    private static int shiftOf(final Token at, final int line, final int column, final DataType type,
                               final int writtenWidth, final String writtenOut) {
        if (type == null || at.getLine() != line || at.getCharPositionInLine() >= column) {
            return 0;
        }
        final String cast = type instanceof StringType ? "" : "::" + SqlTypeNames.refusalSpelling(type);
        return writtenOut.length() + cast.length() - writtenWidth;
    }

    private static boolean holdsASubquery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsASubquery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the expression is the value a LET, an assignment or a DECLARE default gives a variable. */
    private static boolean assignsAValue(final ParserRuleContext expression) {
        ParseTree holder = expression.getParent();
        while (holder instanceof FrostlakeParser.BooleanExprContext || holder instanceof FrostlakeParser.ExpressionContext) {
            holder = holder.getParent();
        }
        return holder instanceof FrostlakeParser.LetStatementContext
            || holder instanceof FrostlakeParser.AssignmentStatementContext
            || holder instanceof FrostlakeParser.DeclarationItemContext
            || holder instanceof FrostlakeParser.UntypedDeclarationItemContext;
    }

    /**
     * Whether the refused place is the expression's own operator or call rather than one inside it: no
     * expression nested in it, parentheses aside, spans the place.
     */
    private static boolean failsAtTheRoot(final ParserRuleContext expression, final int line, final int column) {
        ParserRuleContext root = expression;
        while (true) {
            if (root instanceof FrostlakeParser.ValueExprContext) {
                root = ((FrostlakeParser.ValueExprContext) root).expression();
            } else if (root instanceof FrostlakeParser.ParenExprContext) {
                root = ((FrostlakeParser.ParenExprContext) root).booleanExpr();
            } else {
                break;
            }
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (spansANestedExpression(root.getChild(i), line, column)) {
                return false;
            }
        }
        return true;
    }

    private static boolean spansANestedExpression(final ParseTree node, final int line, final int column) {
        if (node instanceof FrostlakeParser.BooleanExprContext || node instanceof FrostlakeParser.ExpressionContext) {
            final ParserRuleContext nested = (ParserRuleContext) node;
            return spans(nested, line, column);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (spansANestedExpression(node.getChild(i), line, column)) {
                return true;
            }
        }
        return false;
    }

    private static boolean spans(final ParserRuleContext node, final int line, final int column) {
        final Token start = node.getStart();
        final Token stop = node.getStop();
        if (start == null || stop == null) {
            return false;
        }
        final boolean afterStart = line > start.getLine()
            || line == start.getLine() && column >= start.getCharPositionInLine();
        final int stopEnd = stop.getCharPositionInLine() + Math.max(stop.getText().length() - 1, 0);
        final boolean beforeEnd = line < stop.getLine() || line == stop.getLine() && column <= stopEnd;
        return afterStart && beforeEnd;
    }
}
