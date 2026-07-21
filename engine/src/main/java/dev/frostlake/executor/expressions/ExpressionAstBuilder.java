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

import dev.frostlake.parser.FrostlakeBaseVisitor;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.RuleNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link Expression} AST nodes directly from the ANTLR parse tree.
 *
 * <p>This replaces the round-trip in which an expression sub-tree was flattened back to
 * source text (via {@code getOriginalText}) and then re-parsed, once per row, by the
 * hand-rolled {@code ExpressionParser}. The grammar already parses every expression into a
 * labeled parse tree; this visitor maps each labeled alternative of the {@code expression}
 * rule to the corresponding {@link Expression} node. It mirrors
 * {@link dev.frostlake.executor.SQLCommandVisitor} (which builds statement-level objects) but
 * is typed to produce {@link Expression}.
 *
 * <p>A few constructs intentionally fail loudly via {@link #notPorted} because they are not row
 * expressions that reach this builder — each is handled by another path: window {@code OVER}
 * (resolved by the window operator before row evaluation), function named arguments
 * ({@code f(x => 1)}, used by table functions via {@code TableFunctionOperator}), bind variables
 * ({@code :name}, substituted by the JDBC / procedural layer), and {@code EXECUTE IMMEDIATE}
 * (procedural). There is no longer a legacy-parser fallback, so if one of these ever does reach
 * row evaluation it throws — port it to a real node at that point. Any other unexpected node
 * reaches {@link #visitChildren(RuleNode)} and also fails loudly.
 */
public class ExpressionAstBuilder extends FrostlakeBaseVisitor<Expression> {

    /** Build an {@link Expression} AST from a parsed {@code booleanExpr} (top) context. */
    public Expression build(final FrostlakeParser.BooleanExprContext ctx) {
        return visit(ctx);
    }

    /** Boolean tier value alternative: drop straight through to the underlying value expression. */
    @Override
    public Expression visitValueExpr(final FrostlakeParser.ValueExprContext ctx) {
        return visit(ctx.expression());
    }

    // ------------------------------------------------------------------
    // Primaries: literals, references, variables
    // ------------------------------------------------------------------

    @Override
    public Expression visitParenExpr(final FrostlakeParser.ParenExprContext ctx) {
        return visit(ctx.booleanExpr());
    }

    @Override
    public Expression visitLiteralExpr(final FrostlakeParser.LiteralExprContext ctx) {
        final FrostlakeParser.LiteralContext lit = ctx.literal();
        if (lit.INTEGER_LITERAL() != null) {
            return new LiteralExpression(Long.parseLong(lit.getText()), LiteralType.INTEGER);
        }
        if (lit.FLOAT_LITERAL() != null) {
            return new LiteralExpression(new BigDecimal(lit.getText()), LiteralType.DECIMAL);
        }
        if (lit.STRING_LITERAL() != null) {
            return new LiteralExpression(unquoteString(lit.getText()), LiteralType.STRING);
        }
        if (lit.DOLLAR_QUOTED_STRING() != null) {
            return new LiteralExpression(unquoteDollar(lit.getText()), LiteralType.STRING);
        }
        if (lit.TRUE() != null) {
            return new LiteralExpression(Boolean.TRUE, LiteralType.BOOLEAN);
        }
        if (lit.FALSE() != null) {
            return new LiteralExpression(Boolean.FALSE, LiteralType.BOOLEAN);
        }
        return new LiteralExpression(null, LiteralType.NULL);
    }

    @Override
    public Expression visitQualifiedNameExpr(final FrostlakeParser.QualifiedNameExprContext ctx) {
        final List<FrostlakeParser.IdentifierContext> parts = ctx.qualifiedName().identifier();
        if (parts.size() == 1) {
            return new ColumnReferenceExpression(parts.get(0).getText());
        }
        final String column = parts.get(parts.size() - 1).getText();
        final StringBuilder table = new StringBuilder();
        for (int i = 0; i < parts.size() - 1; i++) {
            if (i > 0) {
                table.append('.');
            }
            table.append(parts.get(i).getText());
        }
        return new ColumnReferenceExpression(table.toString(), column);
    }

    @Override
    public Expression visitSessionVarExpr(final FrostlakeParser.SessionVarExprContext ctx) {
        return new SessionVarExpression(ctx.SESSION_VAR_REF().getText().substring(1));
    }

    @Override
    public Expression visitCurrentTimestampExpr(final FrostlakeParser.CurrentTimestampExprContext ctx) {
        return zeroArgFunction("CURRENT_TIMESTAMP");
    }

    @Override
    public Expression visitCurrentDateExpr(final FrostlakeParser.CurrentDateExprContext ctx) {
        return zeroArgFunction("CURRENT_DATE");
    }

    @Override
    public Expression visitCurrentTimeExpr(final FrostlakeParser.CurrentTimeExprContext ctx) {
        return zeroArgFunction("CURRENT_TIME");
    }

    @Override
    public Expression visitCurrentUserExpr(final FrostlakeParser.CurrentUserExprContext ctx) {
        return zeroArgFunction("CURRENT_USER");
    }

    // ------------------------------------------------------------------
    // Unary / binary operators
    // ------------------------------------------------------------------

    @Override
    public Expression visitNotExpr(final FrostlakeParser.NotExprContext ctx) {
        return new UnaryOperationExpression(
            UnaryOperator.NOT, visit(ctx.booleanExpr()));
    }

    @Override
    public Expression visitUnaryExpr(final FrostlakeParser.UnaryExprContext ctx) {
        final Expression operand = visit(ctx.expression());
        if (ctx.op.getType() == FrostlakeParser.MINUS) {
            return new UnaryOperationExpression(UnaryOperator.NEGATE, operand);
        }
        // Unary plus is the identity.
        return operand;
    }

    @Override
    public Expression visitMultiplicativeExpr(final FrostlakeParser.MultiplicativeExprContext ctx) {
        final BinaryOperator op;
        switch (ctx.op.getType()) {
            case FrostlakeParser.STAR:
                op = BinaryOperator.MULTIPLY;
                break;
            case FrostlakeParser.SLASH:
                op = BinaryOperator.DIVIDE;
                break;
            default: // PERCENT
                op = BinaryOperator.MODULO;
                break;
        }
        return binary(ctx.expression(0), op, ctx.expression(1));
    }

    @Override
    public Expression visitAdditiveExpr(final FrostlakeParser.AdditiveExprContext ctx) {
        final BinaryOperator op = ctx.op.getType() == FrostlakeParser.PLUS
            ? BinaryOperator.ADD
            : BinaryOperator.SUBTRACT;
        return binary(ctx.expression(0), op, ctx.expression(1));
    }

    @Override
    public Expression visitConcatExpr(final FrostlakeParser.ConcatExprContext ctx) {
        return binary(ctx.expression(0), BinaryOperator.CONCAT, ctx.expression(1));
    }

    @Override
    public Expression visitComparisonExpr(final FrostlakeParser.ComparisonExprContext ctx) {
        return binary(ctx.expression(0), comparisonOperator(ctx.op), ctx.expression(1));
    }

    @Override
    public Expression visitAndExpr(final FrostlakeParser.AndExprContext ctx) {
        return binary(ctx.booleanExpr(0), BinaryOperator.AND, ctx.booleanExpr(1));
    }

    @Override
    public Expression visitOrExpr(final FrostlakeParser.OrExprContext ctx) {
        return binary(ctx.booleanExpr(0), BinaryOperator.OR, ctx.booleanExpr(1));
    }

    // ------------------------------------------------------------------
    // Predicates: IS NULL, LIKE, BETWEEN, IN, quantified comparison
    // ------------------------------------------------------------------

    @Override
    public Expression visitIsNullExpr(final FrostlakeParser.IsNullExprContext ctx) {
        return new IsNullExpression(visit(ctx.expression()), ctx.NOT() != null);
    }

    @Override
    public Expression visitLikeExpr(final FrostlakeParser.LikeExprContext ctx) {
        final boolean not = ctx.NOT() != null;
        final BinaryOperator op;
        if (ctx.ILIKE() != null) {
            op = not ? BinaryOperator.NOT_ILIKE
                     : BinaryOperator.ILIKE;
        } else {
            op = not ? BinaryOperator.NOT_LIKE
                     : BinaryOperator.LIKE;
        }
        // Carry the optional ESCAPE <char> (ctx.expression(2)) so evaluation can honor a custom
        // escape character rather than always assuming the default backslash.
        final Expression escape = ctx.expression(2) != null ? visit(ctx.expression(2)) : null;
        return new BinaryOperationExpression(visit(ctx.expression(0)), op, visit(ctx.expression(1)), escape);
    }

    @Override
    public Expression visitBetweenExpr(final FrostlakeParser.BetweenExprContext ctx) {
        return new BetweenExpression(
            visit(ctx.expression(0)), visit(ctx.expression(1)), visit(ctx.expression(2)), ctx.NOT() != null);
    }

    @Override
    public Expression visitInListExpr(final FrostlakeParser.InListExprContext ctx) {
        return new InExpression(visit(ctx.expression()), argList(ctx.expressionList()), ctx.NOT() != null);
    }

    @Override
    public Expression visitInSubqueryExpr(final FrostlakeParser.InSubqueryExprContext ctx) {
        return new InExpression(visit(ctx.expression()),
            new SubqueryExpression(originalText(ctx.selectStatement())), ctx.NOT() != null);
    }

    @Override
    public Expression visitQuantifiedComparisonExpr(final FrostlakeParser.QuantifiedComparisonExprContext ctx) {
        return new QuantifiedComparisonExpression(
            visit(ctx.expression()),
            comparisonOperator(ctx.op),
            Quantifier.valueOf(ctx.quantifier().getText().toUpperCase()),
            new SubqueryExpression(originalText(ctx.selectStatement())));
    }

    // ------------------------------------------------------------------
    // CASE / CAST
    // ------------------------------------------------------------------

    @Override
    public Expression visitCaseExpr(final FrostlakeParser.CaseExprContext ctx) {
        return visit(ctx.caseExpression());
    }

    @Override
    public Expression visitSimpleCaseExpr(final FrostlakeParser.SimpleCaseExprContext ctx) {
        final Expression operand = visit(ctx.expression(0));
        final List<CaseExpression.WhenClause> whens = new ArrayList<>();
        for (final FrostlakeParser.WhenClauseContext when : ctx.whenClause()) {
            // CASE x WHEN v THEN r  ==>  condition (x = v)
            final Expression condition = new BinaryOperationExpression(
                operand, BinaryOperator.EQUAL, visit(when.booleanExpr()));
            whens.add(new CaseExpression.WhenClause(condition, visit(when.expression())));
        }
        final Expression elseExpr = ctx.expression().size() > 1 ? visit(ctx.expression(1)) : null;
        return new CaseExpression(whens, elseExpr);
    }

    @Override
    public Expression visitSearchedCaseExpr(final FrostlakeParser.SearchedCaseExprContext ctx) {
        final List<CaseExpression.WhenClause> whens = new ArrayList<>();
        for (final FrostlakeParser.WhenClauseContext when : ctx.whenClause()) {
            whens.add(new CaseExpression.WhenClause(visit(when.booleanExpr()), visit(when.expression())));
        }
        // SearchedCase has a single (optional) ELSE expression directly under the rule.
        final Expression elseExpr = ctx.expression() != null ? visit(ctx.expression()) : null;
        return new CaseExpression(whens, elseExpr);
    }

    @Override
    public Expression visitCastExpr(final FrostlakeParser.CastExprContext ctx) {
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()));
    }

    @Override
    public Expression visitCastExpr2(final FrostlakeParser.CastExpr2Context ctx) {
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()));
    }

    @Override
    public Expression visitTypedDateTimeLiteralExpr(final FrostlakeParser.TypedDateTimeLiteralExprContext ctx) {
        // A typed date/time literal — DATE '2020-01-15', TIMESTAMP '2020-01-15 10:30:00', TIMESTAMP_NTZ '…' —
        // is exactly '<string>'::<TYPE>; route it through CAST so it reuses the same conversion and value.
        final String value = unquoteString(ctx.STRING_LITERAL().getText());
        final String typeName = ctx.dateTimeLiteralType().getText().toUpperCase();
        return new CastExpression(
            new LiteralExpression(value, LiteralType.STRING), typeName);
    }

    // ------------------------------------------------------------------
    // Subqueries, access paths, JSON, interval, function calls, system funcs
    // ------------------------------------------------------------------

    @Override
    public Expression visitScalarSubqueryExpr(final FrostlakeParser.ScalarSubqueryExprContext ctx) {
        return new SubqueryExpression(originalText(ctx.selectStatement()));
    }

    @Override
    public Expression visitExistsExpr(final FrostlakeParser.ExistsExprContext ctx) {
        return new UnaryOperationExpression(
            UnaryOperator.EXISTS,
            new SubqueryExpression(originalText(ctx.selectStatement())));
    }

    @Override
    public Expression visitObjectAccessExpr(final FrostlakeParser.ObjectAccessExprContext ctx) {
        // Keep the path as its ordered identifier segments from the parse tree; no flatten-then-re-split.
        final List<String> pathParts = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext id : ctx.identifier()) {
            pathParts.add(id.getText());
        }
        return new ObjectAccessExpression(visit(ctx.expression()), pathParts);
    }

    @Override
    public Expression visitArrayAccessExpr(final FrostlakeParser.ArrayAccessExprContext ctx) {
        return new ArrayAccessExpression(visit(ctx.expression(0)), visit(ctx.expression(1)));
    }

    @Override
    public Expression visitFieldAccessExpr(final FrostlakeParser.FieldAccessExprContext ctx) {
        // A postfix `.field` on a semi-structured value (e.g. the object at c[0] in c[0].b): reuse
        // ObjectAccessExpression as a single-segment path so the same JSON property extraction that
        // powers colon paths applies. A bare column reference a.b stays a QualifiedNameExpr (the
        // greedy qualifiedName rule consumes it), so this only fires after a subscript/paren/etc.
        final List<String> pathParts = new ArrayList<>();
        pathParts.add(ctx.identifier().getText());
        return new ObjectAccessExpression(visit(ctx.expression()), pathParts);
    }

    @Override
    public Expression visitJsonObjectExpr(final FrostlakeParser.JsonObjectExprContext ctx) {
        final Map<String, Expression> props = new LinkedHashMap<>();
        for (final FrostlakeParser.JsonKeyValuePairContext pair : ctx.jsonObjectLiteral().jsonKeyValuePair()) {
            props.put(unquoteString(pair.STRING_LITERAL().getText()), visit(pair.expression()));
        }
        return new JsonObjectExpression(props);
    }

    @Override
    public Expression visitJsonArrayExpr(final FrostlakeParser.JsonArrayExprContext ctx) {
        final List<Expression> elements = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext element : ctx.jsonArrayLiteral().expression()) {
            elements.add(visit(element));
        }
        return new JsonArrayExpression(elements);
    }

    @Override
    public Expression visitIntervalExpr(final FrostlakeParser.IntervalExprContext ctx) {
        return new IntervalExpression(visit(ctx.expression()),
            IntervalUnit.valueOf(ctx.intervalUnit().getText().toUpperCase()));
    }

    @Override
    public Expression visitIntervalStringExpr(final FrostlakeParser.IntervalStringExprContext ctx) {
        // INTERVAL '<n> <unit>' — split the string literal into numeric value + unit (mirrors the
        // legacy parser). A non-integer value throws here and is caught as a fall back to legacy.
        final String inner = unquoteString(ctx.STRING_LITERAL().getText()).trim();
        final String upper = inner.toUpperCase();
        for (final IntervalUnit unit : IntervalUnit.values()) {
            final String name = unit.name();
            final boolean spaced = upper.endsWith(" " + name);
            if (spaced || upper.endsWith(name)) {
                final int cut = inner.length() - (spaced ? name.length() + 1 : name.length());
                final String numStr = inner.substring(0, cut).trim();
                return new IntervalExpression(
                    new LiteralExpression(Long.parseLong(numStr), LiteralType.INTEGER), unit);
            }
        }
        throw notPorted("INTERVAL string literal (unrecognized unit)", ctx);
    }

    @Override
    public Expression visitExtractFromExpr(final FrostlakeParser.ExtractFromExprContext ctx) {
        // ANSI EXTRACT(<part> FROM <expr>) — desugar to the two-argument function form the engine already
        // supports, EXTRACT('<part>', <expr>), with the date-part identifier carried as a string literal.
        final List<Expression> args = new ArrayList<>();
        args.add(new LiteralExpression(ctx.identifier().getText(), LiteralType.STRING));
        args.add(visit(ctx.expression()));
        return new FunctionCallExpression(ctx.functionName().getText().toUpperCase(), args);
    }

    @Override
    public Expression visitFunctionCallExpr(final FrostlakeParser.FunctionCallExprContext ctx) {
        if (ctx.overClause() != null) {
            // A window call nested in an expression stays in the AST as a node keyed by its source text;
            // the window stage precomputes its per-row value and supplies it through the result context.
            return new WindowFunctionExpression(originalText(ctx));
        }
        return new FunctionCallExpression(
            ctx.functionName().getText().toUpperCase(),
            argList(ctx.functionArgList()),
            ctx.DISTINCT() != null,
            false);
    }

    @Override
    public Expression visitFunctionCallStarExpr(final FrostlakeParser.FunctionCallStarExprContext ctx) {
        return new FunctionCallExpression(
            ctx.functionName().getText().toUpperCase(),
            new ArrayList<>(),
            ctx.DISTINCT() != null,
            true);
    }

    @Override
    public Expression visitSystemFuncExpr(final FrostlakeParser.SystemFuncExprContext ctx) {
        return new FunctionCallExpression(ctx.SYSTEM_FUNC().getText().toUpperCase(), argList(ctx.expressionList()));
    }

    @Override
    public Expression visitSystemStreamHasDataExpr(final FrostlakeParser.SystemStreamHasDataExprContext ctx) {
        return new SystemStreamHasDataExpression(visit(ctx.expression()));
    }

    @Override
    public Expression visitSystemUserTaskCancelExpr(final FrostlakeParser.SystemUserTaskCancelExprContext ctx) {
        return new SystemUserTaskCancelExpression(visit(ctx.expression()));
    }

    // ------------------------------------------------------------------
    // Not yet ported (richer than the current node model) — fail loudly
    // ------------------------------------------------------------------

    @Override
    public Expression visitFunctionCallNamedArgsExpr(final FrostlakeParser.FunctionCallNamedArgsExprContext ctx) {
        if (ctx.overClause() != null) {
            throw notPorted("window function (OVER) with named arguments", ctx);
        }
        final List<Expression> args = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.NamedArgumentContext na : ctx.namedArgumentList().namedArgument()) {
            names.add(na.identifier().getText());
            args.add(visit(na.expression()));
        }
        return new FunctionCallExpression(ctx.functionName().getText().toUpperCase(), args, names);
    }

    @Override
    public Expression visitFunctionCallMixedArgsExpr(final FrostlakeParser.FunctionCallMixedArgsExprContext ctx) {
        if (ctx.overClause() != null) {
            throw notPorted("window function (OVER) with named arguments", ctx);
        }
        // A single leading positional argument, then one or more named arguments.
        final List<Expression> args = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        args.add(visit(ctx.expression()));
        names.add(null);
        for (final FrostlakeParser.NamedArgumentContext na : ctx.namedArgument()) {
            names.add(na.identifier().getText());
            args.add(visit(na.expression()));
        }
        return new FunctionCallExpression(ctx.functionName().getText().toUpperCase(), args, names);
    }

    @Override
    public Expression visitTupleInListExpr(final FrostlakeParser.TupleInListExprContext ctx) {
        return new TupleInExpression(
            argList(ctx.expressionList(0)), argList(ctx.expressionList(1)), ctx.NOT() != null);
    }

    @Override
    public Expression visitTupleInSubqueryExpr(final FrostlakeParser.TupleInSubqueryExprContext ctx) {
        return new TupleInExpression(
            argList(ctx.expressionList()),
            new SubqueryExpression(originalText(ctx.selectStatement())),
            ctx.NOT() != null);
    }

    @Override
    public Expression visitBindVarExpr(final FrostlakeParser.BindVarExprContext ctx) {
        return new BindVariableExpression(ctx.identifier().getText());
    }

    @Override
    public Expression visitPositionalBindExpr(final FrostlakeParser.PositionalBindExprContext ctx) {
        // A bare positional bind '?' is substituted with its value in the query TEXT before the query is
        // parsed for execution (OPEN … USING / EXECUTE IMMEDIATE … USING). Reaching AST construction means
        // it was left unbound.
        throw new RuntimeException("Positional bind placeholder '?' has no value; bind it via OPEN ... USING");
    }

    @Override
    public Expression visitExecuteImmediateExpr(final FrostlakeParser.ExecuteImmediateExprContext ctx) {
        final List<Expression> bindings = ctx.expressionList() != null
            ? argList(ctx.expressionList()) : new ArrayList<>();
        return new ExecuteImmediateExpression(visit(ctx.expression()), bindings);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Expression binary(final ParserRuleContext left,
                              final BinaryOperator op,
                              final ParserRuleContext right) {
        return new BinaryOperationExpression(visit(left), op, visit(right));
    }

    private Expression zeroArgFunction(final String name) {
        return new FunctionCallExpression(name, new ArrayList<>());
    }

    private List<Expression> argList(final FrostlakeParser.ExpressionListContext list) {
        final List<Expression> args = new ArrayList<>();
        if (list != null) {
            for (final FrostlakeParser.ExpressionContext arg : list.expression()) {
                args.add(visit(arg));
            }
        }
        return args;
    }

    /** Function-call arguments (a {@code booleanExpr} list, so a bare AND/OR argument is accepted). */
    private List<Expression> argList(final FrostlakeParser.FunctionArgListContext list) {
        final List<Expression> args = new ArrayList<>();
        if (list != null) {
            for (final FrostlakeParser.BooleanExprContext arg : list.booleanExpr()) {
                args.add(visit(arg));
            }
        }
        return args;
    }

    private String typeText(final FrostlakeParser.DataTypeNameContext type, final FrostlakeParser.TypeParametersContext params) {
        final String base = type.getText();
        return params != null ? base + originalText(params) : base;
    }

    private BinaryOperator comparisonOperator(final Token op) {
        switch (op.getType()) {
            case FrostlakeParser.EQ:
                return BinaryOperator.EQUAL;
            case FrostlakeParser.NEQ:
                return BinaryOperator.NOT_EQUAL;
            case FrostlakeParser.LT:
                return BinaryOperator.LESS_THAN;
            case FrostlakeParser.LTE:
                return BinaryOperator.LESS_THAN_OR_EQUAL;
            case FrostlakeParser.GT:
                return BinaryOperator.GREATER_THAN;
            default: // GTE
                return BinaryOperator.GREATER_THAN_OR_EQUAL;
        }
    }

    private String unquoteString(final String raw) {
        // Strip the surrounding single quotes, then unescape. Mirrors the legacy ExpressionParser:
        // '' -> ', \' -> ', \\ -> \, \n/\t/\r -> control chars; other backslashes kept verbatim.
        final String inner = raw.substring(1, raw.length() - 1);
        final StringBuilder sb = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            final char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                final char next = inner.charAt(i + 1);
                if (next == '\'') { sb.append('\''); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 't') { sb.append('\t'); i++; }
                else if (next == 'r') { sb.append('\r'); i++; }
                else { sb.append(c); }
            } else if (c == '\'' && i + 1 < inner.length() && inner.charAt(i + 1) == '\'') {
                sb.append('\''); i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String unquoteDollar(final String raw) {
        // $$...$$ -> ...
        if (raw.length() >= 4 && raw.startsWith("$$") && raw.endsWith("$$")) {
            return raw.substring(2, raw.length() - 2);
        }
        return raw;
    }

    /** Reconstructs the original source text of a sub-tree (e.g. a subquery), whitespace preserved. */
    private String originalText(final ParserRuleContext ctx) {
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex()));
    }

    private UnsupportedOperationException notPorted(final String construct, final ParserRuleContext ctx) {
        return new UnsupportedOperationException(
            "Expression construct not yet ported to ExpressionAstBuilder: " + construct
            + " ['" + ctx.getText() + "']");
    }

    /**
     * Backstop for any {@code expression} alternative not explicitly handled above. Ported
     * overrides build their nodes directly and never reach this, so hitting it means an
     * unported (or unexpected) construct.
     */
    @Override
    public Expression visitChildren(final RuleNode node) {
        throw new UnsupportedOperationException(
            "Expression construct not yet ported to ExpressionAstBuilder: "
            + node.getClass().getSimpleName() + " ['" + node.getText() + "']");
    }
}
