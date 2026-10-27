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
import dev.frostlake.executor.expressions.RowExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnknownUnitWord;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Arrays;
import java.util.Collections;
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
 * <p>The account compiles the expression with every variable written out as its value's type, so a variable standing
 * before the refused operator on its line moves the place: a name {@code x} reads {@code (x)::TYPE}, a FOR counter
 * {@code (:i)::TYPE} and a bind {@code :x} reads {@code ?::TYPE}, spelled as a refusal spells the declared type, and a
 * text takes no cast at all ({@code (x)}, {@code ?}); inside an expression that holds a subquery a name reads {@code
 * (:x)::TYPE}, and so does every name of a stored procedure's body and of a block whose outermost DECLARE section
 * declares a cursor or a RESULTSET with a query; an IF's condition places a call given the wrong number of arguments so
 * too, where its argument types keep {@code (x)::TYPE}. A text variable's type is its value's width, as a bound value's
 * is. The place counts from the expression's own start, after a lead-in on its first line: seven columns when the
 * expression holds a subquery or a call of a user-defined function (the account compiles it as a query), and eight more
 * for a value given to a variable — a LET, an assignment, a DECLARE default, the bound a range FOR's counter starts
 * from (the upper one of a REVERSE loop); a later line keeps its own columns (all live-verified).
 *
 * <p>A call given more or fewer arguments than it takes is judged with the types and placed the same way:
 * {@code RETURN 1 + ABS(1, 2)} is {@code too many arguments for function [ABS(1, 2)] expected 1, got 2} at
 * position 4, {@code RETURN 1 + f(1)} over a function that takes none at position 11, and {@code x := ABS(1, 2)}
 * at position 8 (live-verified).
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

    /** What a call given more arguments than it takes is refused with, before its echo. */
    private static final String TOO_MANY = "too many arguments for function [";

    /** What a call given fewer arguments than it takes is refused with, before its echo. */
    private static final String NOT_ENOUGH = "not enough arguments for function [";

    /** What a unit outside its function's vocabulary is refused with, after the unit. */
    private static final String UNKNOWN_UNIT = "] is not a valid date/time component for function ";

    private BlockExpressionTypes() {
    }

    /**
     * Judge a block expression as it is reached: its argument types, then, for a value given to a variable of a
     * declared type, its conversion to the type. The conversion is the plain cast's, so a pair the cast matrix
     * refuses is refused in the cast's own words — {@code LET x DATE := TRUE} is {@code invalid type [CAST(TRUE
     * AS DATE)] for parameter 'TO_DATE'}, and a BOOLEAN into a TIMESTAMP, a TIME, a BINARY or a FLOAT, a number
     * into a DATE and a DATE into a BOOLEAN alike — while a pair the matrix does not govern keeps the conversion
     * it had. A ROW converts to no declared type: {@code LET x INT := (1, 2)} is {@code invalid type [CAST(ROW(1, 2)
     * AS NUMBER(38,0))] for parameter 'TO_NUMBER'}, into a text, a DATE and a VARIANT alike (all live-verified).
     * Either refusal is that expression's EXPRESSION_ERROR.
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
            // A refusal that echoes the expression writes each variable as its bound value's conversion: the plan's
            // for a comparison's conversion, and a miscounted call's as the block binds it, ABS(x, 2) over a number
            // holding 1 as [ABS(TO_NUMBER('1'), 2)].
            final String echo = refusal.contains(CONVERSION_ECHO) ? CONVERSION_ECHO
                : refusal.contains(TOO_MANY) ? TOO_MANY : refusal.contains(NOT_ENOUGH) ? NOT_ENOUGH : null;
            if (echo != null) {
                final String rendered = argumentTypeRefusal(expression,
                    rendered(expression, types, values, CONVERSION_ECHO.equals(echo)), types, queryExecutor);
                if (rendered != null && rendered.contains(echo)) {
                    echoed = rendered;
                }
            }
            // An IF's or an ELSEIF's condition places a miscounted call with each name written out as a bind, and its
            // argument types with the name as it stands: IF (x + ABS(1, 2) > 0) over a NUMBER is refused at 21, IF (x
            // + TRUE = 1) at 18 (live-verified).
            final boolean bound = namesBound || (TOO_MANY.equals(echo) || NOT_ENOUGH.equals(echo))
                && expression.getParent() instanceof FrostlakeParser.IfStatementContext;
            throw new BlockExpressionTypeError(placedInExpression(echoed, expression, types, counters, bound,
                compilesAsAQuery(expression, queryExecutor == null ? null
                    : DeclarationTypes.scriptTyper(types, queryExecutor))));
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
            final DataType valueType = sql instanceof RowExpression ? null : typer.inferStaticType(sql);
            if (!(sql instanceof RowExpression) && !ExpressionEvaluatorVisitor.castMatrixRefuses(valueType, target)
                    && !textIntoObject(valueType, target)) {
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

    /**
     * Whether a text is given to an OBJECT, which TO_OBJECT refuses whatever the text holds: {@code o := '{"a": 1}'}
     * is {@code invalid type [CAST('{"a": 1}' AS OBJECT)] for parameter 'TO_OBJECT'}, and a text variable, a
     * concatenation, a DEFAULT and a typed LET alike (live-verified).
     */
    private static boolean textIntoObject(final DataType value, final DataType target) {
        return value instanceof StringType && target instanceof ObjectType && !(target instanceof StructuredObjectType);
    }

    /**
     * The static type of a block expression over the names in scope, or null when it cannot be typed.
     *
     * @param expression    the expression as parsed in the block
     * @param types         the types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the type, or null
     */
    public static DataType staticType(final ParserRuleContext expression, final Map<String, DataType> types,
                                      final QueryExecutor queryExecutor) {
        final Expression sql = sqlOf(expression);
        if (sql == null || queryExecutor == null) {
            return null;
        }
        final SourcePosition displaced = ExpressionSource.begin(new SourcePosition(1, 0));
        try {
            return DeclarationTypes.scriptTyper(types, queryExecutor).inferStaticType(sql);
        } catch (final RuntimeException untyped) {
            return null;
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
                    final Expression value = ctx.variableName() == null ? null
                        : renderingOf(ctx.variableName().getText(), types, values, planned);
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
     * Judge an expression while the block compiles, refusing it bare, placed in the block. A unit written as a text
     * outside its function's vocabulary is refused then too: {@code IF (FALSE) THEN LET x := DATEADD('nosuchpart', 1,
     * CURRENT_DATE()); END IF;} is {@code ['nosuchpart'] is not a valid date/time component for function DATEADD.}
     * (live-verified).
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
            : argumentTypeRefusal(expression, sqlOf(expression), types, queryExecutor, true);
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
        return argumentTypeRefusal(expression, sql, types, queryExecutor, false);
    }

    /**
     * SQL's refusal of the expression's argument types, placed in the block, or null when SQL takes them; as the block
     * compiles, the refusal of a unit written as a text outside its function's vocabulary too (see {@link
     * #judgeCompiled}). A bare word in the unit's place is left to the expression's own judging.
     */
    private static String argumentTypeRefusal(final ParserRuleContext expression, final Expression sql,
                                              final Map<String, DataType> types, final QueryExecutor queryExecutor,
                                              final boolean compiled) {
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
                    || message.contains(CONVERSION_ECHO) || message.contains(TOO_MANY)
                    || message.contains(NOT_ENOUGH)
                    || compiled && !(refused instanceof UnknownUnitWord) && message.contains(UNKNOWN_UNIT))
                ? message : null;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** A refusal placed in the block, moved into the expression as the account counts it. */
    private static String placedInExpression(final String refusal, final ParserRuleContext expression,
                                             final Map<String, DataType> types, final Set<String> counters,
                                             final boolean namesBound, final boolean query) {
        final Matcher place = PLACE.matcher(refusal);
        if (!place.find()) {
            return refusal;
        }
        final int line = Integer.parseInt(place.group(1));
        final int column = Integer.parseInt(place.group(2));
        final Token start = expression.getStart();
        int moved = column + writtenOutShift(expression, line, column, types, counters, query || namesBound);
        if (line == start.getLine()) {
            moved += -start.getCharPositionInLine() + (query ? QUERY_LEAD : 0)
                + (assignsAValue(expression) ? ASSIGNED_VALUE_LEAD : 0);
        }
        return placed(refusal, place, line - start.getLine() + 1, moved);
    }

    /**
     * The columns the names of an expression that holds a subquery add to a place on their line, each written out as a
     * bind: {@code RETURN x + (SELECT ABS(1, 2))} over a NUMBER is refused seventeen columns on, {@code x} read as
     * {@code (:x)::NUMBER(38,0)} (live-verified). A name inside the subquery adds none.
     *
     * @param expression the expression as parsed in the block
     * @param line       the place's line in the block
     * @param column     the place's column
     * @param types      the types of the names in scope, keyed as the resolver folds them
     * @return the columns
     */
    public static int boundNamesShift(final ParserRuleContext expression, final int line, final int column,
                                      final Map<String, DataType> types) {
        return writtenOutShift(expression, line, column, types, Collections.emptySet(), true);
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
            if (bind.variableName() == null) {
                return 0;
            }
            final String written = bind.variableName().getText();
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
        return writtenOut.length() + jsonReading(type).length() + cast.length() - writtenWidth;
    }

    /**
     * The call a VARIANT, OBJECT or ARRAY variable is read back through where it is written out, its JSON text bound:
     * {@code RETURN IS_DOUBLE(v) || ' ' || TRUE} over a VARIANT is refused twelve columns further on than the
     * variable's cast alone would place it, over an OBJECT and an ARRAY too, whatever the value holds, where a
     * NUMBER's and a VARCHAR's places take the cast alone (live-verified).
     */
    private static String jsonReading(final DataType type) {
        final boolean semiStructured = type instanceof VariantType
            || type instanceof ObjectType && !(type instanceof StructuredObjectType)
            || type instanceof ArrayType && !(type instanceof StructuredArrayType);
        return semiStructured ? "PARSE_JSON()" : "";
    }

    /**
     * Whether the account compiles the expression as a query: it holds a subquery, or a call of a function
     * someone created, in whatever language.
     */
    private static boolean compilesAsAQuery(final ParseTree node, final ExpressionEvaluator typer) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        final String[] called = typer == null ? null : calledName(node);
        if (called != null && typer.callsUserFunction(Arrays.asList(called))) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (compilesAsAQuery(node.getChild(i), typer)) {
                return true;
            }
        }
        return false;
    }

    /** The canonical parts of the name a call writes, or null for a node that is no call by a plain name. */
    private static String[] calledName(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            return ParseTreeText.functionNameParts(((FrostlakeParser.FunctionCallExprContext) node).functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ParseTreeText.functionNameParts(
                ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ParseTreeText.functionNameParts(
                ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).functionName());
        }
        return null;
    }

    /**
     * Whether the expression is the value a LET, an assignment or a DECLARE default gives a variable, or the bound a
     * range FOR's counter starts from: the first, or the second of a REVERSE loop.
     */
    private static boolean assignsAValue(final ParserRuleContext expression) {
        ParseTree held = expression;
        ParseTree holder = expression.getParent();
        while (holder instanceof FrostlakeParser.BooleanExprContext || holder instanceof FrostlakeParser.ExpressionContext) {
            held = holder;
            holder = holder.getParent();
        }
        if (holder instanceof FrostlakeParser.ForStatementContext) {
            final FrostlakeParser.ForStatementContext loop = (FrostlakeParser.ForStatementContext) holder;
            return loop.TO() != null && held == loop.expression(loop.REVERSE() != null ? 1 : 0);
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
