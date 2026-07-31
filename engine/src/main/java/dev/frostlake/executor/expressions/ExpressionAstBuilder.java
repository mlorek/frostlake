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

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
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
            final String intText = lit.getText();
            try {
                return new LiteralExpression(Long.parseLong(intText), LiteralType.INTEGER);
            } catch (final NumberFormatException tooWide) {
                // Integer literal wider than a Java long — Snowflake NUMBER(38,0) allows up to 38 digits,
                // so represent it exactly as a BigDecimal (e.g. 20+ digit synthetic row IDs) rather than
                // overflowing with "For input string".
                return new LiteralExpression(new BigDecimal(intText), LiteralType.DECIMAL);
            }
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
        if (lit.HEX_LITERAL() != null) {
            // x'a1b2' — the engine's BINARY representation is the uppercase hex string.
            final String hex = lit.getText();
            return new LiteralExpression(hex.substring(2, hex.length() - 1).toUpperCase(), LiteralType.STRING);
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
        return buildColumnReference(ctx.qualifiedName());
    }

    /** {@code column(+)} — the Oracle legacy outer-join marker. The {@code (+)} is a JOIN directive handled
     *  by the executor (which detects it at the parse-tree level and rewrites the comma-join into an outer
     *  join); as an expression the marked reference is just its plain column, so evaluation drops the marker. */
    @Override
    public Expression visitOuterJoinColumnExpr(final FrostlakeParser.OuterJoinColumnExprContext ctx) {
        return buildColumnReference(ctx.qualifiedName());
    }

    private Expression buildColumnReference(final FrostlakeParser.QualifiedNameContext qn) {
        final List<FrostlakeParser.IdentifierContext> parts = qn.identifier();
        if (parts.size() == 1) {
            return new ColumnReferenceExpression(SqlIdentifiers.canonical(parts.get(0)));
        }
        final String column = SqlIdentifiers.canonical(parts.get(parts.size() - 1));
        final StringBuilder table = new StringBuilder();
        for (int i = 0; i < parts.size() - 1; i++) {
            if (i > 0) {
                table.append('.');
            }
            table.append(SqlIdentifiers.canonical(parts.get(i)));
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
    public Expression visitIsDistinctExpr(final FrostlakeParser.IsDistinctExprContext ctx) {
        // a IS [NOT] DISTINCT FROM b — NULL-safe (in)equality, expressed via EQUAL_NULL(a, b):
        //   IS NOT DISTINCT FROM => EQUAL_NULL(a, b);  IS DISTINCT FROM => NOT EQUAL_NULL(a, b).
        final List<Expression> args = new ArrayList<>();
        args.add(visit(ctx.expression(0)));
        args.add(visit(ctx.expression(1)));
        final Expression equalNull = new FunctionCallExpression("EQUAL_NULL", args);
        return ctx.NOT() != null ? equalNull : new UnaryOperationExpression(UnaryOperator.NOT, equalNull);
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
    public Expression visitRlikeExpr(final FrostlakeParser.RlikeExprContext ctx) {
        // Snowflake defines `<subject> [NOT] RLIKE|REGEXP <pattern>` as REGEXP_LIKE(subject, pattern)
        // — a FULL-string regex match — so build exactly that call and inherit its evaluation
        // (including NULL propagation); NOT wraps the call like any negated predicate.
        final List<Expression> args = new ArrayList<>();
        args.add(visit(ctx.expression(0)));
        args.add(visit(ctx.expression(1)));
        final Expression call = new FunctionCallExpression("REGEXP_LIKE", args);
        return ctx.NOT() != null ? new UnaryOperationExpression(UnaryOperator.NOT, call) : call;
    }

    @Override
    public Expression visitLikeAnyAllExpr(final FrostlakeParser.LikeAnyAllExprContext ctx) {
        // Snowflake's multi-pattern matching — `x [NOT] LIKE/ILIKE ANY|ALL (p1, p2, …)` — is defined as the
        // OR (ANY) / AND (ALL) expansion of the single-pattern predicate, so build exactly that tree and
        // inherit the existing LIKE evaluation and its three-valued NULL semantics.
        final BinaryOperator op = ctx.ILIKE() != null ? BinaryOperator.ILIKE : BinaryOperator.LIKE;
        final BinaryOperator combiner = ctx.q.getType() == FrostlakeParser.ALL ? BinaryOperator.AND : BinaryOperator.OR;
        final Expression subject = visit(ctx.expression(0));
        final Expression escape = ctx.esc != null ? visit(ctx.esc) : null;
        Expression combined = null;
        for (final FrostlakeParser.ExpressionContext patternCtx : ctx.patterns) {
            final Expression one = new BinaryOperationExpression(subject, op, visit(patternCtx), escape);
            combined = combined == null ? one : new BinaryOperationExpression(combined, combiner, one);
        }
        return ctx.NOT() != null ? new UnaryOperationExpression(UnaryOperator.NOT, combined) : combined;
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
        final Expression operand = visit(ctx.expression());
        final List<CaseExpression.WhenClause> whens = new ArrayList<>();
        for (final FrostlakeParser.WhenClauseContext when : ctx.whenClause()) {
            // CASE x WHEN v THEN r  ==>  condition (x = v); booleanExpr(0)=WHEN value, booleanExpr(1)=THEN result
            final Expression condition = new BinaryOperationExpression(
                operand, BinaryOperator.EQUAL, visit(when.booleanExpr(0)));
            whens.add(new CaseExpression.WhenClause(condition, visit(when.booleanExpr(1))));
        }
        final Expression elseExpr = ctx.booleanExpr() != null ? visit(ctx.booleanExpr()) : null;
        return new CaseExpression(whens, elseExpr);
    }

    @Override
    public Expression visitSearchedCaseExpr(final FrostlakeParser.SearchedCaseExprContext ctx) {
        final List<CaseExpression.WhenClause> whens = new ArrayList<>();
        for (final FrostlakeParser.WhenClauseContext when : ctx.whenClause()) {
            whens.add(new CaseExpression.WhenClause(visit(when.booleanExpr(0)), visit(when.booleanExpr(1))));
        }
        // SearchedCase has a single (optional) ELSE booleanExpr directly under the rule.
        final Expression elseExpr = ctx.booleanExpr() != null ? visit(ctx.booleanExpr()) : null;
        return new CaseExpression(whens, elseExpr);
    }

    @Override
    public Expression visitCastExpr(final FrostlakeParser.CastExprContext ctx) {
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()));
    }

    @Override
    public Expression visitTryCastExpr(final FrostlakeParser.TryCastExprContext ctx) {
        // TRY_CAST(expr AS type): same as CAST but a failed conversion yields NULL instead of erroring.
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()), true);
    }

    @Override
    public Expression visitCollateFuncExpr(final FrostlakeParser.CollateFuncExprContext ctx) {
        // COLLATE(expr, 'spec') — the function form of Snowflake's COLLATE. Expression-level collation
        // metadata is not modelled, so the collation is a parse-time pass-through: the value is the inner
        // expression's value and comparisons stay binary (correct for consistent-case data).
        return visit(ctx.expression());
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

    /**
     * The JSON key a variant path segment names. A QUOTED segment ({@code x:"source".y}) sheds its
     * surrounding quotes (with {@code ""} un-doubled) — keeping them made the lookup search for a key
     * that literally contains quote characters, so the whole path silently resolved to NULL.
     */
    private static String variantPathKeyText(final FrostlakeParser.VariantPathKeyContext key) {
        final String text = key.getText();
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text;
    }

    @Override
    public Expression visitObjectAccessExpr(final FrostlakeParser.ObjectAccessExprContext ctx) {
        // Keep the path as its ordered key segments from the parse tree; no flatten-then-re-split.
        final List<String> pathParts = new ArrayList<>();
        for (final FrostlakeParser.VariantPathKeyContext key : ctx.variantPathKey()) {
            pathParts.add(variantPathKeyText(key));
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
        pathParts.add(variantPathKeyText(ctx.variantPathKey()));
        return new ObjectAccessExpression(visit(ctx.expression()), pathParts);
    }

    @Override
    public Expression visitJsonObjectExpr(final FrostlakeParser.JsonObjectExprContext ctx) {
        boolean hasSpread = false;
        for (final FrostlakeParser.JsonObjectEntryContext entry : ctx.jsonObjectLiteral().jsonObjectEntry()) {
            if (entry.DOUBLE_STAR() != null) {
                hasSpread = true;
            }
        }
        if (hasSpread) {
            // {'a': 1, **obj}: desugar to OBJECT_CONSTRUCT with the spread merged into the
            // alternating key/value argument list (last key wins, as in the plain constructor).
            final List<Expression> args = new ArrayList<>();
            for (final FrostlakeParser.JsonObjectEntryContext entry : ctx.jsonObjectLiteral().jsonObjectEntry()) {
                if (entry.DOUBLE_STAR() != null) {
                    args.add(new SpreadExpression(visit(entry.expression())));
                } else {
                    args.add(new LiteralExpression(
                        unquoteString(entry.jsonKeyValuePair().STRING_LITERAL().getText()), LiteralType.STRING));
                    args.add(visit(entry.jsonKeyValuePair().expression()));
                }
            }
            return new FunctionCallExpression("OBJECT_CONSTRUCT", args);
        }
        final Map<String, Expression> props = new LinkedHashMap<>();
        for (final FrostlakeParser.JsonObjectEntryContext entry : ctx.jsonObjectLiteral().jsonObjectEntry()) {
            final FrostlakeParser.JsonKeyValuePairContext pair = entry.jsonKeyValuePair();
            props.put(unquoteString(pair.STRING_LITERAL().getText()), visit(pair.expression()));
        }
        return new JsonObjectExpression(props);
    }

    @Override
    public Expression visitJsonArrayExpr(final FrostlakeParser.JsonArrayExprContext ctx) {
        boolean hasSpread = false;
        for (final FrostlakeParser.ArrayElementContext element : ctx.jsonArrayLiteral().arrayElement()) {
            if (element.DOUBLE_STAR() != null) {
                hasSpread = true;
            }
        }
        if (hasSpread) {
            // [1, **arr]: desugar to ARRAY_CONSTRUCT with the spread spliced into the arguments.
            final List<Expression> args = new ArrayList<>();
            for (final FrostlakeParser.ArrayElementContext element : ctx.jsonArrayLiteral().arrayElement()) {
                args.add(element.DOUBLE_STAR() != null
                    ? new SpreadExpression(visit(element.expression()))
                    : visit(element.expression()));
            }
            return new FunctionCallExpression("ARRAY_CONSTRUCT", args);
        }
        final List<Expression> elements = new ArrayList<>();
        for (final FrostlakeParser.ArrayElementContext element : ctx.jsonArrayLiteral().arrayElement()) {
            elements.add(visit(element.expression()));
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
        // DATE_PART also allows a quoted part: DATE_PART('month' FROM d).
        final List<Expression> args = new ArrayList<>();
        final String part = ctx.identifier() != null
            ? ctx.identifier().getText()
            : unquoteString(ctx.STRING_LITERAL().getText());
        args.add(new LiteralExpression(part, LiteralType.STRING));
        args.add(visit(ctx.expression()));
        return new FunctionCallExpression(ctx.functionName().getText().toUpperCase(), args);
    }

    @Override
    public Expression visitFunctionCallExpr(final FrostlakeParser.FunctionCallExprContext ctx) {
        if (ctx.overClause() != null) {
            if (ctx.filterClause() != null) {
                throw new RuntimeException("FILTER (WHERE ...) combined with OVER (...) is not supported");
            }
            // A window call nested in an expression stays in the AST as a node keyed by its source text;
            // the window stage precomputes its per-row value and supplies it through the result context.
            return new WindowFunctionExpression(originalText(ctx));
        }
        if (ctx.functionName().KW_IDENTIFIER() != null) {
            // IDENTIFIER('fn') / IDENTIFIER($var) as the function name — resolved per evaluation, so a
            // session-variable name stays correct even though the AST is cached by source text.
            return new FunctionCallExpression(
                originalText(ctx.functionName()),
                visit(ctx.functionName().expression()),
                argList(ctx.functionArgList()));
        }
        return new FunctionCallExpression(
            ctx.functionName().getText().toUpperCase(),
            filteredArgs(argList(ctx.functionArgList()), ctx.filterClause()),
            ctx.DISTINCT() != null,
            false);
    }

    /**
     * FILTER (WHERE cond) — conditional aggregation: every argument is wrapped as
     * CASE WHEN cond THEN arg END, so rows failing the condition contribute NULL, which
     * aggregates ignore. This matches the clause's semantics for NULL-skipping aggregates.
     */
    private List<Expression> filteredArgs(final List<Expression> args,
                                          final FrostlakeParser.FilterClauseContext filter) {
        if (filter == null) {
            return args;
        }
        if (args.isEmpty()) {
            throw new RuntimeException("FILTER (WHERE ...) requires an aggregate with arguments");
        }
        final Expression condition = visit(filter.booleanExpr());
        final List<Expression> wrapped = new ArrayList<>();
        for (final Expression arg : args) {
            final List<CaseExpression.WhenClause> whens = new ArrayList<>();
            whens.add(new CaseExpression.WhenClause(condition, arg));
            wrapped.add(new CaseExpression(whens, null));
        }
        return wrapped;
    }

    @Override
    public Expression visitFunctionCallStarExpr(final FrostlakeParser.FunctionCallStarExprContext ctx) {
        final FunctionCallExpression call = new FunctionCallExpression(
            ctx.functionName().getText().toUpperCase(),
            new ArrayList<>(),
            ctx.DISTINCT() != null,
            true);
        // Column-list modifiers on a `*` argument — currently EXCLUDE (e.g. OBJECT_CONSTRUCT(* EXCLUDE src)).
        final List<String> excludes = new ArrayList<>();
        for (final FrostlakeParser.StarModifierContext mod : ctx.starModifier()) {
            if (mod.EXCLUDE() != null) {
                for (final FrostlakeParser.IdentifierContext id : mod.identifier()) {
                    excludes.add(id.getText().toUpperCase());
                }
            }
        }
        call.setStarExcludes(excludes);
        return call;
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
            args.add(namedArgumentValue(na));
        }
        return new FunctionCallExpression(ctx.functionName().getText().toUpperCase(), args, names);
    }

    /** A named argument's value expression; a bare subquery value (INPUT => SELECT ...) becomes a
     *  scalar subquery node. */
    private Expression namedArgumentValue(final FrostlakeParser.NamedArgumentContext na) {
        return na.expression() != null
            ? visit(na.expression())
            : new SubqueryExpression(originalText(na.selectStatement()));
    }

    @Override
    public Expression visitFunctionCallMixedArgsExpr(final FrostlakeParser.FunctionCallMixedArgsExprContext ctx) {
        if (ctx.overClause() != null) {
            throw notPorted("window function (OVER) with named arguments", ctx);
        }
        // One or more leading positional arguments, then one or more named arguments.
        final List<Expression> args = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext positional : ctx.expression()) {
            args.add(visit(positional));
            names.add(null);
        }
        for (final FrostlakeParser.NamedArgumentContext na : ctx.namedArgument()) {
            names.add(na.identifier().getText());
            args.add(namedArgumentValue(na));
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
        return new BindVariableExpression(ctx.identifier() != null
            ? ctx.identifier().getText()
            : ctx.INTEGER_LITERAL().getText());
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

    /** Function-call arguments — each a lambda (for higher-order functions) or a {@code booleanExpr}. */
    private List<Expression> argList(final FrostlakeParser.FunctionArgListContext list) {
        final List<Expression> args = new ArrayList<>();
        if (list != null) {
            for (final FrostlakeParser.FunctionArgContext arg : list.functionArg()) {
                if (arg.lambdaFunction() != null) {
                    args.add(buildLambda(arg.lambdaFunction()));
                } else if (arg.DOUBLE_STAR() != null) {
                    // f(**arr) — the array's elements become positional arguments at evaluation.
                    args.add(new SpreadExpression(visit(arg.booleanExpr())));
                } else if (arg.STAR() != null) {
                    // A star argument (MINHASH(5, *)) — carried as a '*' column reference.
                    args.add(new ColumnReferenceExpression("*"));
                } else if (arg.exprTuple() != null) {
                    // A parenthesized tuple argument — SEARCH((play, line), 'q') — arrives as an array.
                    final List<Expression> elements = new ArrayList<>();
                    for (final FrostlakeParser.ExpressionContext element : arg.exprTuple().expression()) {
                        elements.add(visit(element));
                    }
                    args.add(new FunctionCallExpression("ARRAY_CONSTRUCT", elements));
                } else {
                    args.add(visit(arg.booleanExpr()));
                }
            }
        }
        return args;
    }

    private Expression buildLambda(final FrostlakeParser.LambdaFunctionContext ctx) {
        final List<String> params = new ArrayList<>();
        for (final FrostlakeParser.LambdaParamContext p : ctx.lambdaParams().lambdaParam()) {
            params.add(p.identifier().getText().toUpperCase());
        }
        return new LambdaExpression(params, visit(ctx.booleanExpr()));
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
        // Canonical decode: '' -> ', \' -> ', \\ -> \, \n/\t/\r -> control chars; other backslashes kept.
        return SqlStringLiterals.decode(raw);
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
