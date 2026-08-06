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
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.parser.FrostlakeBaseVisitor;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.BinaryValue;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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
            // x'a1b2' — a BINARY literal, carried as a real BinaryValue.
            final String hex = lit.getText();
            return new LiteralExpression(
                BinaryValue.fromHex(hex.substring(2, hex.length() - 1)), LiteralType.BINARY);
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

    /** {@code PRIOR <column>} — the parent-row side of a CONNECT BY step. Snowflake resolves only the bare
     *  column here (live-verified: a qualified {@code PRIOR e.id} fails with "invalid identifier"), so any
     *  qualifier is dropped and the reference binds to the parent row's copy of that column. */
    @Override
    public Expression visitPriorExpr(final FrostlakeParser.PriorExprContext ctx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
        return new PriorExpression(parts[parts.length - 1]);
    }

    /** {@code CONNECT_BY_ROOT <column>} — the hierarchy root row's value of that column. */
    @Override
    public Expression visitConnectByRootExpr(final FrostlakeParser.ConnectByRootExprContext ctx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
        final String column = parts[parts.length - 1];
        if (parts.length == 1) {
            return new ConnectByRootExpression(null, column);
        }
        final StringBuilder table = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                table.append('.');
            }
            table.append(parts[i]);
        }
        return new ConnectByRootExpression(table.toString(), column);
    }

    private Expression buildColumnReference(final FrostlakeParser.QualifiedNameContext qn) {
        final String[] parts = ParseTreeText.qualifiedNameParts(qn);
        // Where the reference begins, relative to the fragment being parsed. A message about an
        // unresolvable column reports the position of the reference itself — live, an identifier
        // inside a function call or on the right of a comparison is reported at ITS offset, not at
        // the start of the clause that holds it.
        final SourcePosition position =
            new SourcePosition(qn.getStart().getLine(), qn.getStart().getCharPositionInLine());
        if (parts.length == 1) {
            return new ColumnReferenceExpression(null, parts[0], position);
        }
        final String column = parts[parts.length - 1];
        final StringBuilder table = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                table.append('.');
            }
            table.append(parts[i]);
        }
        return new ColumnReferenceExpression(table.toString(), column, position);
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
        // Snowflake's multi-pattern matching is NOT the OR/AND expansion of the single-pattern
        // predicate: NULL patterns are skipped rather than propagated as UNKNOWN (live-verified), so
        // a dedicated node carries the pattern list and the shared ESCAPE.
        final List<Expression> patterns = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext patternCtx : ctx.patterns) {
            patterns.add(visit(patternCtx));
        }
        return new LikeAnyAllExpression(
            visit(ctx.expression(0)),
            patterns,
            ctx.q.getType() == FrostlakeParser.ALL,
            ctx.ILIKE() != null,
            ctx.esc != null ? visit(ctx.esc) : null);
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
        rejectFileTarget(ctx.dataTypeName(), "CAST(" + originalText(ctx.expression()) + " AS FILE)");
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()),
            false, declaredTarget(ctx.dataTypeName(), ctx.typeParameters()),
            fieldsModifier(ctx.RENAME(), ctx.ADD()));
    }

    @Override
    public Expression visitTryCastExpr(final FrostlakeParser.TryCastExprContext ctx) {
        // TRY_CAST(expr AS type): same as CAST but a failed conversion yields NULL instead of erroring.
        // The field modifiers are a CAST-only feature — live, TRY_CAST(<structured> AS
        // OBJECT(y VARCHAR) RENAME FIELDS) fails "Function RENAME FIELDS modifier is not supported with
        // TRY_CAST" even though the very same CAST succeeds. Rejected here, on the parse tree, so the
        // error does not depend on evaluating a row.
        final CastFieldsModifier modifier = fieldsModifier(ctx.RENAME(), ctx.ADD());
        if (modifier != CastFieldsModifier.NONE) {
            throw new RuntimeException("Function " + modifier.getSql()
                + " modifier is not supported with TRY_CAST");
        }
        // TRY_CAST renders WITHOUT the target in the live message: `TRY_CAST(NULL AS FILE)` fails
        // "invalid type [TRY_CAST(NULL)] for parameter 'TO_FILE'".
        rejectFileTarget(ctx.dataTypeName(), "TRY_CAST(" + originalText(ctx.expression()) + ")");
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()),
            true, declaredTarget(ctx.dataTypeName(), ctx.typeParameters()), CastFieldsModifier.NONE);
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
        // The `::` shorthand has no modifier slot in the grammar, matching Snowflake — live,
        // `<expr>::OBJECT(y VARCHAR) RENAME FIELDS` is a SYNTAX error there, not a semantic one.
        // The `::` form reports itself as CAST(...) live: `NULL::FILE` fails with
        // "invalid type [CAST(NULL AS FILE)] for parameter 'TO_FILE'".
        rejectFileTarget(ctx.dataTypeName(), "CAST(" + originalText(ctx.expression()) + " AS FILE)");
        return new CastExpression(visit(ctx.expression()), typeText(ctx.dataTypeName(), ctx.typeParameters()),
            false, declaredTarget(ctx.dataTypeName(), ctx.typeParameters()), CastFieldsModifier.NONE);
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
        final Map<String, Expression> props = new LinkedHashMap<>();
        for (final FrostlakeParser.JsonObjectEntryContext entry : ctx.jsonObjectLiteral().jsonObjectEntry()) {
            final FrostlakeParser.JsonKeyValuePairContext pair = entry.jsonKeyValuePair();
            props.put(unquoteString(pair.STRING_LITERAL().getText()), visit(pair.expression()));
        }
        return new JsonObjectExpression(props);
    }

    @Override
    public Expression visitJsonArrayExpr(final FrostlakeParser.JsonArrayExprContext ctx) {
        return new JsonArrayExpression(arrayLiteralElements(ctx));
    }

    /** The elements of an array literal, with any {@code ** …} element spliced in place. */
    private List<Expression> arrayLiteralElements(final FrostlakeParser.JsonArrayExprContext ctx) {
        final List<Expression> elements = new ArrayList<>();
        for (final FrostlakeParser.ArrayElementContext element : ctx.jsonArrayLiteral().arrayElement()) {
            if (element.spreadArgument() != null) {
                elements.addAll(spreadElements(element.spreadArgument()));
            } else {
                elements.add(visit(element.expression()));
            }
        }
        return elements;
    }

    @Override
    public Expression visitIntervalExpr(final FrostlakeParser.IntervalExprContext ctx) {
        // INTERVAL '<n>' <singular-unit> (live-verified: the amount must be quoted, and only the
        // singular unit keywords act as a suffix — a plural word after the string is an alias).
        final String amount = unquoteString(ctx.STRING_LITERAL().getText()).trim();
        try {
            return new IntervalExpression(
                new LiteralExpression(Long.parseLong(amount), LiteralType.INTEGER),
                IntervalUnit.valueOf(ctx.intervalUnitSingular().getText().toUpperCase()));
        } catch (final NumberFormatException badNumber) {
            throw notPorted("INTERVAL literal (non-integer amount '" + amount + "')", ctx);
        }
    }

    @Override
    public Expression visitIntervalStringExpr(final FrostlakeParser.IntervalStringExprContext ctx) {
        // Snowflake's quoted interval literal (live-verified): comma-separated `<n> [<unit>]` parts,
        // singular or plural unit words, and a bare number defaulting to SECONDS
        // (CURRENT_DATE + INTERVAL '10' adds ten seconds). Parts apply in order via the rest chain.
        final String inner = unquoteString(ctx.STRING_LITERAL().getText());
        final String[] parts = inner.split(",");
        IntervalExpression chain = null;
        for (int i = parts.length - 1; i >= 0; i--) {
            final String part = parts[i].trim();
            if (part.isEmpty()) {
                throw notPorted("INTERVAL string literal (empty part)", ctx);
            }
            int digitEnd = 0;
            if (digitEnd < part.length() && (part.charAt(0) == '-' || part.charAt(0) == '+')) {
                digitEnd = 1;
            }
            while (digitEnd < part.length() && Character.isDigit(part.charAt(digitEnd))) {
                digitEnd++;
            }
            final String numStr = part.substring(0, digitEnd).trim();
            final String unitStr = part.substring(digitEnd).trim().toUpperCase();
            final IntervalUnit unit;
            if (unitStr.isEmpty()) {
                unit = IntervalUnit.SECOND;
            } else {
                try {
                    unit = IntervalUnit.valueOf(unitStr);
                } catch (final IllegalArgumentException unknownUnit) {
                    throw notPorted("INTERVAL string literal (unrecognized unit '" + unitStr + "')", ctx);
                }
            }
            try {
                chain = new IntervalExpression(
                    new LiteralExpression(Long.parseLong(numStr), LiteralType.INTEGER), unit, chain);
            } catch (final NumberFormatException badNumber) {
                throw notPorted("INTERVAL string literal (non-integer amount '" + numStr + "')", ctx);
            }
        }
        return chain;
    }

    @Override
    public Expression visitExtractFromExpr(final FrostlakeParser.ExtractFromExprContext ctx) {
        // ANSI EXTRACT(<part> FROM <expr>) — desugar to the two-argument function form the engine already
        // supports, EXTRACT('<part>', <expr>), with the date-part identifier carried as a string literal.
        // Only EXTRACT has the FROM form: Snowflake rejects DATE_PART('month' FROM d) as a syntax error.
        final String fromFunction = ctx.functionName().getText().toUpperCase();
        if (!fromFunction.equals("EXTRACT")) {
            throw new RuntimeException("SQL compilation error:\nsyntax error: '" + fromFunction
                + "' does not accept a FROM argument form (only EXTRACT does)");
        }
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
            argList(ctx.functionArgList()),
            ctx.DISTINCT() != null,
            false);
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
    public Expression visitFunctionCallStarExpr(final FrostlakeParser.FunctionCallStarExprContext ctx) {
        // COUNT(*) / COUNT(DISTINCT *) — a star call, with EXCLUDE columns carried for star-aware callers.
        final FunctionCallExpression call = new FunctionCallExpression(
            ctx.functionName().getText().toUpperCase(), new ArrayList<>(), ctx.DISTINCT() != null, true);
        final List<String> excludes = new ArrayList<>();
        for (final FrostlakeParser.StarModifierContext mod : ctx.starModifier()) {
            if (mod.EXCLUDE() == null) {
                continue;
            }
            for (final FrostlakeParser.IdentifierContext id : mod.identifier()) {
                excludes.add(id.getText().toUpperCase());
            }
        }
        call.setStarExcludes(excludes);
        return call;
    }

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

    /** The arguments of a function call, each mapped through {@link #functionArgument}. */
    private List<Expression> argList(final FrostlakeParser.FunctionArgListContext list) {
        final List<Expression> args = new ArrayList<>();
        if (list != null) {
            for (final FrostlakeParser.FunctionArgContext arg : list.functionArg()) {
                if (arg.spreadArgument() != null) {
                    args.addAll(spreadElements(arg.spreadArgument()));
                } else {
                    args.add(functionArgument(arg));
                }
            }
        }
        return args;
    }

    /**
     * The argument expressions a {@code ** <array>} spread contributes to the enclosing list.
     *
     * <p>Snowflake requires the operand to be a CONSTANT array, and decides that STRUCTURALLY rather than
     * by evaluating: an array literal or an {@code ARRAY_CONSTRUCT} call qualifies, but a runtime
     * expression does not — {@code [** PARSE_JSON('[1,2]')]} is rejected live even though its value is an
     * array, as are a column reference, a scalar, a string and an OBJECT. A bare {@code NULL} is the one
     * non-array operand Snowflake accepts, splicing as a single element ({@code [** NULL]} → {@code
     * [undefined]}).
     *
     * <p>Splicing here, at build time, is what makes the constant requirement fall out for free — and it
     * gives every function argument splatting without per-function work, which is what Snowflake does
     * ({@code GREATEST(** [1,5,3])} → 5, {@code CONCAT(** ['a','b'])} → 'ab').
     */
    private List<Expression> spreadElements(final FrostlakeParser.SpreadArgumentContext spread) {
        final FrostlakeParser.ExpressionContext operand = spread.expression();
        if (operand instanceof FrostlakeParser.JsonArrayExprContext) {
            return arrayLiteralElements((FrostlakeParser.JsonArrayExprContext) operand);
        }
        if (isArrayConstructCall(operand)) {
            return argList(((FrostlakeParser.FunctionCallExprContext) operand).functionArgList());
        }
        if (isNullLiteral(operand)) {
            return Collections.singletonList(visit(operand));
        }
        throw new RuntimeException("Unsupported feature 'spread argument with non-constant array input'.");
    }

    /** Whether the expression is literally an {@code ARRAY_CONSTRUCT(…)} call — the other constant-array form. */
    private static boolean isArrayConstructCall(final FrostlakeParser.ExpressionContext operand) {
        if (!(operand instanceof FrostlakeParser.FunctionCallExprContext)) {
            return false;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) operand;
        return "ARRAY_CONSTRUCT".equalsIgnoreCase(call.functionName().getText());
    }

    /** Whether the expression is the bare {@code NULL} literal. */
    private static boolean isNullLiteral(final FrostlakeParser.ExpressionContext operand) {
        if (!(operand instanceof FrostlakeParser.LiteralExprContext)) {
            return false;
        }
        return ((FrostlakeParser.LiteralExprContext) operand).literal().NULL() != null;
    }

    /**
     * One function argument: a lambda (higher-order functions), a parenthesized tuple — carried as the
     * array of its members, the {@code SEARCH((c1, c2), …)} form — a star argument (kept as a {@code *}
     * column reference, only meaningful to star-aware callers), or a plain boolean expression.
     */
    private Expression functionArgument(final FrostlakeParser.FunctionArgContext arg) {
        if (arg.lambdaFunction() != null) {
            return lambda(arg.lambdaFunction());
        }
        if (arg.exprTuple() != null) {
            final List<Expression> members = new ArrayList<>();
            for (final FrostlakeParser.ExpressionContext member : arg.exprTuple().expression()) {
                members.add(visit(member));
            }
            return new FunctionCallExpression("ARRAY_CONSTRUCT", members);
        }
        if (arg.STAR() != null) {
            return new ColumnReferenceExpression("*");
        }
        return visit(arg.booleanExpr());
    }

    /** A lambda argument: parameter names with their declared types (if any), plus the visited body. */
    private Expression lambda(final FrostlakeParser.LambdaFunctionContext fn) {
        final List<String> params = new ArrayList<>();
        final List<String> paramTypes = new ArrayList<>();
        for (final FrostlakeParser.LambdaParamContext param : fn.lambdaParams().lambdaParam()) {
            params.add(param.identifier().getText());
            paramTypes.add(param.dataTypeName() == null ? null
                : typeText(param.dataTypeName(), param.typeParameters()));
        }
        return new LambdaExpression(params, paramTypes, visit(fn.booleanExpr()));
    }

    private String typeText(final FrostlakeParser.DataTypeNameContext type, final FrostlakeParser.TypeParametersContext params) {
        final String base = writtenBaseType(type);
        return params != null ? base + originalText(params) : base;
    }

    /**
     * The cast target's base type AS WRITTEN, with the two spellings that carry no meaning of their own
     * folded onto the name Snowflake itself reports for them. {@code DEC} is a plain {@code NUMBER}
     * synonym and {@code NVARCHAR2} a plain {@code VARCHAR} one (live: {@code 1.5::DEC(8,4)}
     * is {@code 1.5000} and {@code 'ab'::NVARCHAR2} is a VARCHAR). Folding them HERE, where the written
     * target text is first captured, is what keeps them out of the several downstream tables that are
     * keyed by that text — the cast-value classifier, the static-type inferencer and the error-message
     * namer — instead of adding a synonym row to each.
     */
    private String writtenBaseType(final FrostlakeParser.DataTypeNameContext type) {
        if (type.DEC() != null) {
            return "NUMBER";
        }
        if (type.NVARCHAR2() != null) {
            return "VARCHAR";
        }
        return type.getText();
    }

    /**
     * The cast target as a parsed type when its PARAMETERS carry meaning — a STRUCTURED type or a
     * {@code VECTOR(FLOAT|INT, n)} — and null for a plain type. These cannot be recovered from
     * {@link #typeText}: {@code getText()} concatenates tokens without whitespace, so
     * {@code OBJECT(x VARCHAR)} flattens to {@code OBJECT(xVARCHAR)}. The parse tree is the definitive
     * form, so both the field structure and the vector's element type / dimension are read off it.
     */
    private static DataType declaredTarget(final FrostlakeParser.DataTypeNameContext type,
                                           final FrostlakeParser.TypeParametersContext params) {
        final DataType parsed = DataTypeParser.parse(type, params, DataTypeParser.CAST_STRING_DEFAULT);
        return StructuredTypes.isStructured(parsed) || parsed instanceof VectorType ? parsed : null;
    }

    /**
     * FILE is a COLUMN type but not a CAST target: live, {@code NULL::FILE},
     * {@code CAST(NULL AS FILE)} and {@code TRY_CAST(NULL AS FILE)} all fail at COMPILE time (no row is
     * needed) with "invalid type [&lt;rendered cast&gt;] for parameter 'TO_FILE'" — Snowflake routes a
     * cast to FILE through TO_FILE, so the message is that function's argument-type error. Rejected on
     * the parse tree, in the one position that reads a cast's target type.
     */
    private static void rejectFileTarget(final FrostlakeParser.DataTypeNameContext type,
                                         final String renderedCast) {
        if (type.FILE() != null) {
            throw new RuntimeException("SQL compilation error:\ninvalid type [" + renderedCast
                + "] for parameter 'TO_FILE'");
        }
    }

    private static CastFieldsModifier fieldsModifier(final TerminalNode rename, final TerminalNode add) {
        if (rename != null) {
            return CastFieldsModifier.RENAME;
        }
        if (add != null) {
            return CastFieldsModifier.ADD;
        }
        return CastFieldsModifier.NONE;
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
        // The one canonical string-literal decoder (quote stripping + escape handling).
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
