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

import dev.frostlake.executor.IntegerLiteralRange;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StarArgument;
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.parser.FrostlakeBaseVisitor;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VectorType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * A literal, STAMPED with where it was written. The position is what lets a refusal point at an
     * ARGUMENT rather than at the call — live's bad-rounding-mode sentence carries both, the call's
     * offset on the prefix line and the argument's inside the detail.
     */
    @Override
    public Expression visitLiteralExpr(final FrostlakeParser.LiteralExprContext ctx) {
        final Expression built = literalOf(ctx.literal());
        if (built instanceof LiteralExpression) {
            ((LiteralExpression) built).setPosition(new SourcePosition(
                ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        }
        return built;
    }

    private Expression literalOf(final FrostlakeParser.LiteralContext lit) {
        if (lit.INTEGER_LITERAL() != null) {
            final String intText = lit.getText();
            if (IntegerLiteralRange.isOutOfRange(intText)) {
                // Refused where the literal is READ, so it fires wherever a number can be written —
                // a select item, a comparison, an IN list, a CASE branch, an INSERT value. RESOLVED
                // against the enclosing fragment: an expression is re-parsed on its own, so the
                // token's position is an offset into that fragment, not into the statement.
                final SourcePosition at = ExpressionSource.resolve(new SourcePosition(
                    lit.getStart().getLine(), lit.getStart().getCharPositionInLine()));
                throw new RuntimeException(at != null
                    ? SqlCompilationError.atCapitalised(at.getLine(), at.getCharPositionInLine(),
                        IntegerLiteralRange.sentence(intText))
                    : SqlCompilationError.of(IntegerLiteralRange.sentence(intText)));
            }
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
            // Past the exact range the family changes: live reads a literal needing more than
            // thirty-eight digits as a DOUBLE, which is what makes it legal at all — the point-free
            // spelling of the same magnitude is refused above, and a double has no such ceiling.
            final BigDecimal exact = NumericLiteralTypes.exactValue(lit.getText());
            return NumericLiteralTypes.exceedsExactRange(exact)
                ? new LiteralExpression(Double.valueOf(exact.doubleValue()), LiteralType.DECIMAL)
                : new LiteralExpression(exact, LiteralType.DECIMAL);
        }
        if (lit.STRING_LITERAL() != null) {
            return new LiteralExpression(
                unquoteStringAt(lit.getText(), lit.STRING_LITERAL().getSymbol()), LiteralType.STRING);
        }
        if (lit.DOLLAR_QUOTED_STRING() != null) {
            return new LiteralExpression(unquoteDollar(lit.getText()), LiteralType.STRING);
        }
        if (lit.HEX_LITERAL() != null) {
            // x'a1b2' — a BINARY literal, carried as a real BinaryValue.
            return new LiteralExpression(BinaryLiteralText.decode(lit.getText()), LiteralType.BINARY);
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
            // An UNQUOTED bare DEFAULT never resolves to a column, even when one of that name exists:
            // live answers "invalid identifier 'DEFAULT'" for `SELECT default FROM d` over a table that
            // HAS a DEFAULT column, while `SELECT "DEFAULT" FROM d` reads it. The word is the DML
            // marker, and outside the two places that marker stands (a SET value, a VALUES item) it is
            // a name that cannot be found. The quoted spelling arrives through identifier() and is
            // untouched by this.
            if (qn.nameStartPart().identifier() == null && qn.nameStartPart().DEFAULT() != null) {
                // A MARKER, not a refusal: standing alone as a DML value the word means "this column's
                // declared default", and the write paths take it there. Every other route evaluates it,
                // and the evaluator raises live's "invalid identifier 'DEFAULT'" — which is what live
                // answers for `SET c = DEFAULT + 1` and for `SELECT default FROM d` alike.
                return new DefaultMarkerExpression(position);
            }
            // A single-part $N keeps its canonical COLUMN<N> name but is marked POSITIONAL, so
            // resolution can read the Nth column by place when no relation carries that name.
            final FrostlakeParser.IdentifierContext soleName = qn.nameStartPart().identifier();
            final int ordinal = soleName != null && soleName.POSITIONAL_PARAMETER() != null
                ? Integer.parseInt(soleName.POSITIONAL_PARAMETER().getText().substring(1)) : 0;
            final ColumnReferenceExpression bare =
                new ColumnReferenceExpression(null, parts[0], position, ordinal);
            bare.setWrittenName(writtenQualifiedName(qn));
            return bare;
        }
        final String column = parts[parts.length - 1];
        final StringBuilder table = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                table.append('.');
            }
            table.append(parts[i]);
        }
        final ColumnReferenceExpression qualified =
            new ColumnReferenceExpression(table.toString(), column, position);
        qualified.setWrittenName(writtenQualifiedName(qn));
        return qualified;
    }

    /**
     * The reference AS WRITTEN — each part quoted-verbatim or upper-cased, joined by dots. This is the
     * spelling live echoes when the name resolves to nothing.
     */
    private String writtenQualifiedName(final FrostlakeParser.QualifiedNameContext qn) {
        final StringBuilder written = new StringBuilder(
            SqlIdentifiers.spellAsWritten(qn.nameStartPart().getText()));
        if (ParseTreeText.hasEmptySchemaPart(qn)) {
            // db..t.c: live echoes the empty part as the PUBLIC schema it names.
            written.append(".PUBLIC");
        }
        for (final FrostlakeParser.NamePartContext part : qn.namePart()) {
            written.append('.').append(SqlIdentifiers.spellAsWritten(part.getText()));
        }
        return written.toString();
    }

    @Override
    public Expression visitSessionVarExpr(final FrostlakeParser.SessionVarExprContext ctx) {
        // Stamped like a literal: a refusal that points at the variable — ROUND's mode — names its place.
        final SessionVarExpression variable =
            new SessionVarExpression(ctx.SESSION_VAR_REF().getText().substring(1));
        variable.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        return variable;
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
        // Anchored on the keyword, where live points an argument-type refusal for NOT.
        final UnaryOperationExpression negated = new UnaryOperationExpression(
            UnaryOperator.NOT, visit(ctx.booleanExpr()));
        negated.setPosition(new SourcePosition(
            ctx.NOT().getSymbol().getLine(), ctx.NOT().getSymbol().getCharPositionInLine()));
        return negated;
    }

    @Override
    public Expression visitUnaryExpr(final FrostlakeParser.UnaryExprContext ctx) {
        // Two signs in a row are no expression on a real account: `- -n`, `+-n` and `-+1` are refused
        // as an invalid function named by the OUTER sign, spaced or not, before the operand is read -
        // so `+-+n` names '+'. A parenthesised inner sign, `-(-n)`, and a sign after a binary operator,
        // `1 - -n`, are ordinary (live-verified).
        if (ctx.expression() instanceof FrostlakeParser.UnaryExprContext) {
            throw new RuntimeException(SqlCompilationError.of("invalid function '" + ctx.op.getText() + "'"));
        }
        final Expression operand = visit(ctx.expression());
        // Unary plus is a node of its own, not the identity: live names it 'UNARY PLUS' when it
        // refuses a BOOLEAN, a temporal, a BINARY or a semi-structured operand, and it CONVERTS a
        // text or a VARIANT operand to a FLOAT exactly as the minus does. Both signs record the
        // operator token's own place, which is where the refusal is anchored.
        final UnaryOperationExpression signed = new UnaryOperationExpression(
            ctx.op.getType() == FrostlakeParser.MINUS ? UnaryOperator.NEGATE : UnaryOperator.PLUS,
            operand);
        signed.setPosition(new SourcePosition(ctx.op.getLine(), ctx.op.getCharPositionInLine()));
        return signed;
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
        return binary(ctx.expression(0), op, ctx.expression(1), ctx.op);
    }

    @Override
    public Expression visitAdditiveExpr(final FrostlakeParser.AdditiveExprContext ctx) {
        final BinaryOperator op = ctx.op.getType() == FrostlakeParser.PLUS
            ? BinaryOperator.ADD
            : BinaryOperator.SUBTRACT;
        return binary(ctx.expression(0), op, ctx.expression(1), ctx.op);
    }

    @Override
    public Expression visitConcatExpr(final FrostlakeParser.ConcatExprContext ctx) {
        return binary(ctx.expression(0), BinaryOperator.CONCAT, ctx.expression(1),
            ctx.PIPE_PIPE() != null ? ctx.PIPE_PIPE().getSymbol() : null);
    }

    @Override
    public Expression visitComparisonExpr(final FrostlakeParser.ComparisonExprContext ctx) {
        return binary(ctx.expression(0), comparisonOperator(ctx.op), ctx.expression(1));
    }

    @Override
    public Expression visitAndExpr(final FrostlakeParser.AndExprContext ctx) {
        return binary(ctx.booleanExpr(0), BinaryOperator.AND, ctx.booleanExpr(1),
            ctx.AND().getSymbol());
    }

    @Override
    public Expression visitOrExpr(final FrostlakeParser.OrExprContext ctx) {
        return binary(ctx.booleanExpr(0), BinaryOperator.OR, ctx.booleanExpr(1),
            ctx.OR().getSymbol());
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
        // Carry the optional ESCAPE <char> so evaluation can honor a custom escape character rather
        // than always assuming the default backslash. The grammar admits only a bare literal, NULL or
        // a session variable there, so nothing built from one has to be refused here.
        final Expression escape = escapeOperand(ctx.escapeOperand());
        final BinaryOperationExpression like =
            new BinaryOperationExpression(visit(ctx.expression(0)), op, visit(ctx.expression(1)), escape);
        // Anchored on the keyword, where live points a collation LIKE cannot match under.
        final Token keyword = ctx.ILIKE() != null ? ctx.ILIKE().getSymbol() : ctx.LIKE().getSymbol();
        like.setPosition(new SourcePosition(keyword.getLine(), keyword.getCharPositionInLine()));
        return like;
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
        final LikeAnyAllExpression node = new LikeAnyAllExpression(
            visit(ctx.expression(0)),
            patterns,
            ctx.q.getType() == FrostlakeParser.ALL,
            ctx.ILIKE() != null,
            escapeOperand(ctx.esc));
        final Token keyword = ctx.ILIKE() != null ? ctx.ILIKE().getSymbol() : ctx.LIKE().getSymbol();
        node.setPosition(new SourcePosition(keyword.getLine(), keyword.getCharPositionInLine()));
        return node;
    }

    /**
     * The value LIKE's ESCAPE names: a string literal, a dollar-quoted one, NULL, or a session
     * variable's value. The grammar admits nothing else, so there is no expression to walk.
     *
     * @param ctx the operand, or null when the predicate wrote no ESCAPE
     * @return its expression, or null
     */
    private Expression escapeOperand(final FrostlakeParser.EscapeOperandContext ctx) {
        if (ctx == null) {
            return null;
        }
        final SourcePosition at = new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        if (ctx.SESSION_VAR_REF() != null) {
            final SessionVarExpression variable =
                new SessionVarExpression(ctx.SESSION_VAR_REF().getText().substring(1));
            variable.setPosition(at);
            return variable;
        }
        final LiteralExpression literal;
        if (ctx.DOLLAR_QUOTED_STRING() != null) {
            literal = new LiteralExpression(unquoteDollar(ctx.getText()), LiteralType.STRING);
        } else if (ctx.STRING_LITERAL() != null) {
            literal = new LiteralExpression(
                unquoteStringAt(ctx.getText(), ctx.STRING_LITERAL().getSymbol()), LiteralType.STRING);
        } else {
            literal = new LiteralExpression(null, LiteralType.NULL);
        }
        literal.setPosition(at);
        return literal;
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
        final SubqueryExpression subquery = new SubqueryExpression(originalText(ctx.selectStatement()));
        subquery.setPosition(new SourcePosition(ctx.selectStatement().getStart().getLine(),
            ctx.selectStatement().getStart().getCharPositionInLine()));
        return new InExpression(visit(ctx.expression()), subquery, ctx.NOT() != null);
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
        final List<WhenClause> whens = new ArrayList<>();
        for (final FrostlakeParser.WhenClauseContext when : ctx.whenClause()) {
            // CASE x WHEN v THEN r  ==>  condition (x = v); booleanExpr(0)=WHEN value, booleanExpr(1)=THEN result
            final BinaryOperationExpression condition = new BinaryOperationExpression(
                operand, BinaryOperator.EQUAL, visit(when.booleanExpr(0)));
            condition.markSimpleCaseTest();
            whens.add(new WhenClause(condition, visit(when.booleanExpr(1)), true));
        }
        final Expression elseExpr = ctx.booleanExpr() != null ? visit(ctx.booleanExpr()) : null;
        return new CaseExpression(whens, elseExpr);
    }

    @Override
    public Expression visitSearchedCaseExpr(final FrostlakeParser.SearchedCaseExprContext ctx) {
        final List<WhenClause> whens = new ArrayList<>();
        for (final FrostlakeParser.WhenClauseContext when : ctx.whenClause()) {
            whens.add(new WhenClause(visit(when.booleanExpr(0)), visit(when.booleanExpr(1))));
        }
        // SearchedCase has a single (optional) ELSE booleanExpr directly under the rule.
        final Expression elseExpr = ctx.booleanExpr() != null ? visit(ctx.booleanExpr()) : null;
        return new CaseExpression(whens, elseExpr);
    }

    @Override
    public Expression visitCastExpr(final FrostlakeParser.CastExprContext ctx) {
        rejectFileTarget(ctx.dataTypeName(), "CAST(" + originalText(ctx.expression()) + " AS FILE)");
        return positionedCast(new CastExpression(visit(ctx.expression()),
            typeText(ctx.dataTypeName(), ctx.typeParameters()),
            false, declaredTarget(ctx.dataTypeName(), ctx.typeParameters()),
            fieldsModifier(ctx.RENAME(), ctx.ADD())), ctx.getStart());
    }

    /** A cast stamped with where it was written: the {@code ::} of the shorthand, the keyword otherwise. */
    private static CastExpression positionedCast(final CastExpression cast, final Token at) {
        cast.setPosition(new SourcePosition(at.getLine(), at.getCharPositionInLine()));
        return cast;
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
        return positionedCast(new CastExpression(visit(ctx.expression()),
            typeText(ctx.dataTypeName(), ctx.typeParameters()),
            true, declaredTarget(ctx.dataTypeName(), ctx.typeParameters()), CastFieldsModifier.NONE),
            ctx.getStart());
    }

    @Override
    public Expression visitCollateFuncExpr(final FrostlakeParser.CollateFuncExprContext ctx) {
        // COLLATE(expr, 'spec'), the function spelling of the call the infix form below builds too. The
        // specification must be WRITTEN as a string literal: live refuses a computed one while the
        // statement compiles, in its own sentence.
        final Token spec = specLiteralToken(ctx.expression(1));
        if (spec == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Argument number 2 for function 'COLLATE' needs to be a string literal."));
        }
        return collateCall(visit(ctx.expression(0)), spec, ctx);
    }

    @Override
    public Expression visitCollateExpr(final FrostlakeParser.CollateExprContext ctx) {
        final Token spec = ctx.STRING_LITERAL() != null
            ? ctx.STRING_LITERAL().getSymbol() : ctx.DOLLAR_QUOTED_STRING().getSymbol();
        rejectCollatedEscape(ctx.expression(), spec);
        return collateCall(visit(ctx.expression()), spec, ctx);
    }

    /**
     * The one node both spellings of COLLATE build: a call whose second argument is the specification
     * as a string literal. The collation is MODELLED, not dropped — COLLATION reads it back and every
     * comparison the call reaches runs under it — while the value stays the operand's own.
     */
    private Expression collateCall(final Expression operand, final Token spec, final ParserRuleContext at) {
        final String text = spec.getType() == FrostlakeParser.DOLLAR_QUOTED_STRING
            ? unquoteDollar(spec.getText()) : unquoteStringAt(spec.getText(), spec);
        final LiteralExpression specLiteral = new LiteralExpression(text, LiteralType.STRING);
        specLiteral.setPosition(new SourcePosition(spec.getLine(), spec.getCharPositionInLine()));
        final List<Expression> args = new ArrayList<>();
        args.add(operand);
        args.add(specLiteral);
        final FunctionCallExpression call = new FunctionCallExpression("COLLATE", args, false, false);
        call.setPosition(new SourcePosition(at.getStart().getLine(), at.getStart().getCharPositionInLine()));
        return call;
    }

    /** The string-literal token an expression consists of, or null when it is anything else. */
    private static Token specLiteralToken(final FrostlakeParser.ExpressionContext written) {
        if (!(written instanceof FrostlakeParser.LiteralExprContext)) {
            return null;
        }
        final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) written).literal();
        if (literal.STRING_LITERAL() != null) {
            return literal.STRING_LITERAL().getSymbol();
        }
        return literal.DOLLAR_QUOTED_STRING() != null ? literal.DOLLAR_QUOTED_STRING().getSymbol() : null;
    }

    /**
     * A COLLATE written after a LIKE's ESCAPE has nothing to attach to on the account: the escape is a
     * bare literal, and the predicate itself takes no such suffix unless it is parenthesised. Live names
     * the specification as the token it did not expect.
     */
    private static void rejectCollatedEscape(final FrostlakeParser.ExpressionContext collated,
                                             final Token spec) {
        if (!(collated instanceof FrostlakeParser.LikeExprContext)
                || ((FrostlakeParser.LikeExprContext) collated).escapeOperand() == null) {
            return;
        }
        final SourcePosition within = new SourcePosition(spec.getLine(), spec.getCharPositionInLine());
        final SourcePosition at = ExpressionSource.resolve(within);
        final SourcePosition where = at == null ? within : at;
        throw new RuntimeException("SQL compilation error:\nsyntax error line " + where.getLine()
            + " at position " + where.getCharPositionInLine() + " unexpected '" + spec.getText() + "'.");
    }

    @Override
    public Expression visitCastExpr2(final FrostlakeParser.CastExpr2Context ctx) {
        // The `::` shorthand has no modifier slot in the grammar, matching Snowflake — live,
        // `<expr>::OBJECT(y VARCHAR) RENAME FIELDS` is a SYNTAX error there, not a semantic one.
        // The `::` form reports itself as CAST(...) live: `NULL::FILE` fails with
        // "invalid type [CAST(NULL AS FILE)] for parameter 'TO_FILE'".
        rejectFileTarget(ctx.dataTypeName(), "CAST(" + originalText(ctx.expression()) + " AS FILE)");
        return positionedCast(new CastExpression(visit(ctx.expression()),
            typeText(ctx.dataTypeName(), ctx.typeParameters()),
            false, declaredTarget(ctx.dataTypeName(), ctx.typeParameters()), CastFieldsModifier.NONE),
            ctx.DOUBLE_COLON().getSymbol());
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
        final SubqueryExpression subquery = new SubqueryExpression(originalText(ctx.selectStatement()));
        subquery.setPosition(new SourcePosition(ctx.selectStatement().getStart().getLine(),
            ctx.selectStatement().getStart().getCharPositionInLine()));
        return subquery;
    }

    @Override
    public Expression visitExistsExpr(final FrostlakeParser.ExistsExprContext ctx) {
        // Positioned at the EXISTS keyword, where live places a refusal of the subquery's shape.
        final SubqueryExpression subquery = new SubqueryExpression(originalText(ctx.selectStatement()));
        subquery.setPosition(new SourcePosition(ctx.EXISTS().getSymbol().getLine(),
            ctx.EXISTS().getSymbol().getCharPositionInLine()));
        return new UnaryOperationExpression(UnaryOperator.EXISTS, subquery);
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
        //
        // Live takes the dotted key only as a PATH CONTINUATION: after a colon path (v:a.b), a bracket
        // (v['a'].b, ARRAY_CONSTRUCT(o)[0].a) or another dotted key. After a call, a parenthesised
        // expression or a literal it is a syntax error at the dot: GET(v, 'a').b and (v:a).b are
        // "syntax error line 1 at position N unexpected '.'." (live-verified). Refused here rather than
        // in the grammar, which is what lets the sentence point at the dot.
        final FrostlakeParser.ExpressionContext base = ctx.expression();
        if (!(base instanceof FrostlakeParser.ObjectAccessExprContext)
                && !(base instanceof FrostlakeParser.ArrayAccessExprContext)
                && !(base instanceof FrostlakeParser.FieldAccessExprContext)) {
            final Token dot = ctx.DOT().getSymbol();
            final SourcePosition within = new SourcePosition(dot.getLine(), dot.getCharPositionInLine());
            final SourcePosition at = ExpressionSource.resolve(within);
            final SourcePosition where = at == null ? within : at;
            throw new RuntimeException("SQL compilation error:\nsyntax error line "
                + where.getLine() + " at position " + where.getCharPositionInLine() + " unexpected '.'.");
        }
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
                IntervalUnit.fromSpelling(ctx.intervalUnitSingular().getText()));
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
                unit = IntervalUnit.fromSpelling(unitStr);
                if (unit == null) {
                    // Live's own sentence for a unit word it does not know, echoing it AS WRITTEN.
                    throw new RuntimeException(SqlCompilationError.of(
                        part.substring(digitEnd).trim() + " is not recognized as a date type."));
                }
            }
            try {
                chain = new IntervalExpression(
                    new LiteralExpression(Long.parseLong(numStr), LiteralType.INTEGER), unit, chain);
            } catch (final NumberFormatException badNumber) {
                throw notPorted("INTERVAL string literal (non-integer amount '" + numStr + "')", ctx);
            }
        }
        chain.markUnitInString();
        return chain;
    }

    @Override
    public Expression visitPositionInExpr(final FrostlakeParser.PositionInExprContext ctx) {
        // ANSI POSITION(<needle> IN <haystack>) — the same call the two-argument function form makes,
        // in the argument order live uses: POSITION('b' IN 'abcabc') is 2, exactly as POSITION('b',
        // 'abcabc') is. There is no ANSI FROM tail; live refuses POSITION('b' IN s FROM 3).
        //
        // The spelling is POSITION's ALONE. Any other name reaching here wrote a membership test with
        // an unparenthesised right side — live reads `CHARINDEX('b' IN s)` that way and refuses it at
        // the OPERAND after IN, naming that token: "syntax error line 1 at position 24 unexpected 's'".
        // Refusing here rather than in the grammar is what lets the sentence say that: a predicate on
        // the alternative would kill the parse at the function name instead.
        if (!"POSITION".equalsIgnoreCase(ctx.functionName().getText())) {
            final Token operand = ctx.expression(1).getStart();
            final SourcePosition within = new SourcePosition(
                operand.getLine(), operand.getCharPositionInLine());
            final SourcePosition at = ExpressionSource.resolve(within);
            final SourcePosition where = at == null ? within : at;
            throw new RuntimeException("SQL compilation error:\nsyntax error line "
                + where.getLine() + " at position " + where.getCharPositionInLine()
                + " unexpected '" + operand.getText() + "'.");
        }
        final List<Expression> args = new ArrayList<>();
        args.add(visit(ctx.expression(0)));
        args.add(visit(ctx.expression(1)));
        final FunctionCallExpression call = new FunctionCallExpression("POSITION", args, false, false);
        call.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        return call;
    }

    /**
     * The ANSI {@code SUBSTRING(<x> FROM <a> FOR <b>)} spelling, which Snowflake refuses for every
     * function name there is — SUBSTRING included. It reaches the builder at all so the refusal can be
     * put on the FROM, which is where live puts it; the grammar alternative accepts nothing.
     */
    @Override
    public Expression visitAnsiSubstringExpr(final FrostlakeParser.AnsiSubstringExprContext ctx) {
        final Token keyword = ctx.FROM().getSymbol();
        final SourcePosition within = new SourcePosition(
            keyword.getLine(), keyword.getCharPositionInLine());
        final SourcePosition at = ExpressionSource.resolve(within);
        final SourcePosition where = at == null ? within : at;
        throw new RuntimeException("SQL compilation error:\nsyntax error line "
            + where.getLine() + " at position " + where.getCharPositionInLine()
            + " unexpected 'FROM'.");
    }

    /**
     * An IDENTIFIER followed by a STRING inside a call, which live reads as a TYPED LITERAL and refuses
     * by NAME rather than by syntax: {@code TRIM(BOTH ' ')} is "Unsupported data type literal 'BOTH ' ''"
     * there, and {@code UPPER(FOO ' ')} is the same sentence with the same shape — so it is not a TRIM
     * rule, it is the DATE '2020-01-01' form with an unknown word in front.
     *
     * <p>The whole point of the alternative is that it CONSUMES the pair. Without it the parse died on
     * the string, so {@code TRIM(BOTH ' ' FROM v)} was refused there instead of on its FROM, where live
     * refuses it. Nothing it matches is ever accepted.
     */
    @Override
    public Expression visitTypedLiteralArgExpr(
            final FrostlakeParser.TypedLiteralArgExprContext ctx) {
        throw new RuntimeException("SQL compilation error:\nUnsupported data type literal '"
            + ctx.identifier().getText() + " " + ctx.STRING_LITERAL().getText() + "'.");
    }

    @Override
    public Expression visitExtractFromExpr(final FrostlakeParser.ExtractFromExprContext ctx) {
        // ANSI EXTRACT(<part> FROM <expr>) — desugar to the two-argument function form the engine already
        // supports, EXTRACT('<part>', <expr>), with the date-part identifier carried as a string literal.
        final String fromFunction = ctx.functionName().getText().toUpperCase();
        if (!fromFunction.equals("EXTRACT")) {
            // Live reports the FROM itself, at its own position, and says nothing about the function:
            // SUBSTRING(v FROM 2) is "unexpected 'FROM'" and nothing more. Frostlake used to explain
            // the refusal in a sentence of its own invention, carrying no line or position at all —
            // the message-level twin of a syntax extension, and it appears on no real account.
            // Refusing HERE rather than in the grammar is what puts it on the FROM: a predicate on
            // the alternative kills the parse at the argument before it (measured), exactly as the
            // ANSI POSITION form above records.
            final Token keyword = ctx.FROM().getSymbol();
            final SourcePosition within = new SourcePosition(
                keyword.getLine(), keyword.getCharPositionInLine());
            final SourcePosition at = ExpressionSource.resolve(within);
            final SourcePosition where = at == null ? within : at;
            throw new RuntimeException("SQL compilation error:\nsyntax error line "
                + where.getLine() + " at position " + where.getCharPositionInLine()
                + " unexpected 'FROM'.");
        }
        final List<Expression> args = new ArrayList<>();
        args.add(new LiteralExpression(ctx.identifier().getText(), LiteralType.STRING));
        args.add(visit(ctx.expression()));
        final FunctionCallExpression positionedCall =
            // CANONICAL, not blind upper-case: the name is kept as WRITTEN so an unknown one can be
            // echoed the way the call spelled it. RESOLUTION is a separate question and is NOT
            // case-sensitive — `"SUM"(a)`, `"sum"(a)` and `sum(a)` all find SUM on a real account,
            // measured over rows where the aggregate and the column cannot be confused.
            new FunctionCallExpression(
                SqlIdentifiers.canonicalText(ctx.functionName().getText()), args);
        positionedCall.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        return positionedCall;
    }

    /** The OVER clause's keys as ASTs, so a message can re-print the window the way the plan spells it. */
    private void describeOver(final WindowFunctionExpression window,
                              final FrostlakeParser.FunctionCallExprContext ctx) {
        final FrostlakeParser.OverClauseContext over = ctx.overClause();
        final List<Expression> partition = new ArrayList<>();
        if (over.partitionByClause() != null) {
            for (final FrostlakeParser.ExpressionContext key : over.partitionByClause().expressionList().expression()) {
                partition.add(visit(key));
            }
        }
        final List<Expression> order = new ArrayList<>();
        final List<Boolean> ascending = new ArrayList<>();
        final List<Boolean> nullsFirst = new ArrayList<>();
        if (over.orderByClause() != null) {
            for (final FrostlakeParser.OrderItemContext item : over.orderByClause().orderItem()) {
                order.add(visit(item.expression()));
                ascending.add(Boolean.valueOf(item.DESC() == null));
                nullsFirst.add(item.NULLS() == null ? null : Boolean.valueOf(item.FIRST() != null));
            }
        }
        window.describeOver(ctx.DISTINCT() != null, partition, order, ascending, nullsFirst);
    }

    @Override
    public Expression visitFunctionCallExpr(final FrostlakeParser.FunctionCallExprContext ctx) {
        if (ctx.overClause() != null) {
            // A window call nested in an expression stays in the AST as a node keyed by its source text;
            // the window stage precomputes its per-row value and supplies it through the result context.
            // The NAME and ARGUMENTS ride along unused by evaluation, so the static channel can type the
            // functions that hand their argument back — reading them off the parse tree here rather than
            // re-parsing the call text later.
            final WindowFunctionExpression window = new WindowFunctionExpression(originalText(ctx));
            window.describeCall(ctx.functionName().getText().toUpperCase(),
                argList(ctx.functionArgList()));
            // ROWS only: a RANGE frame leaves the window CUMULATIVE as far as the declared width goes,
            // so it must not be reported as framed here (live-verified — see isRowsFramed).
            window.describeWindow(ctx.overClause().orderByClause() != null,
                ctx.overClause().windowFrame() != null
                    && ctx.overClause().windowFrame().ROWS() != null);
            window.describeWithinGroup(withinGroupOrdered(ctx));
            describeOver(window, ctx);
            window.setPosition(new SourcePosition(ctx.getStart().getLine(),
                ctx.getStart().getCharPositionInLine()));
            if (ctx.functionArgList() != null && ctx.functionArgList().functionArg().size() == 1
                    && ctx.functionArgList().functionArg(0).STAR() != null) {
                // A lone star argument keeps its qualifier and filters for the arity walk, which
                // expands it exactly as the plain star call is expanded.
                final FrostlakeParser.FunctionArgContext star = ctx.functionArgList().functionArg(0);
                final FunctionCallExpression starCall = starCall(ctx.functionName().getText().toUpperCase(),
                    ctx.DISTINCT() != null, star.starQualifiedName(), star.starArgumentModifier());
                starCall.setPosition(window.getPosition());
                window.describeStar(starCall);
            }
            return window;
        }
        if (ctx.functionName().KW_IDENTIFIER() != null) {
            // IDENTIFIER('fn') / IDENTIFIER($var) as the function name — resolved per evaluation, so a
            // session-variable name stays correct even though the AST is cached by source text.
            return new FunctionCallExpression(
                originalText(ctx.functionName()),
                visit(ctx.functionName().expression()),
                argList(ctx.functionArgList()));
        }
        final FrostlakeParser.ExpressionContext membership = positionMembershipTest(ctx);
        if (membership != null) {
            // POSITION(<needle> IN <haystack>): the call's first argument parses as a membership test too,
            // and the call alternative comes first. Live reads that test as POSITION's IN form — so a NOT
            // IN, or anything after the form, is a syntax error there — and takes it apart into needle and
            // haystack: POSITION('b' IN (SELECT 'abc')) is 2, and so are POSITION(('b') IN (SELECT
            // 'abc')) and POSITION('b' IN ('abc')).
            rejectPositionMembershipSyntax(ctx, membership);
            final Expression position = positionInForm(ctx, membership);
            if (position != null) {
                return position;
            }
        }
        final FunctionCallExpression call = new FunctionCallExpression(
            // CANONICAL, not blind upper-case: a quoted function name keeps its case, so `"sum"(a)`
            // resolves to nothing — live answers Unknown function "sum". — where `"SUM"(a)` and
            // `sum(a)` both find SUM. Upper-casing through the quotes made all three one call.
            SqlIdentifiers.canonicalText(ctx.functionName().getText()),
            argList(ctx.functionArgList()),
            ctx.DISTINCT() != null,
            false);
        call.setNameParts(functionNameParts(ctx.functionName()));
        call.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        call.describeWithinGroup(withinGroupOrdered(ctx));
        return call;
    }

    /**
     * The membership test a POSITION call's FIRST argument is — an IN or a NOT IN over a subquery or a
     * list, of one value or of a tuple — or null when the call is no POSITION or that argument is
     * anything else.
     */
    private static FrostlakeParser.ExpressionContext positionMembershipTest(
            final FrostlakeParser.FunctionCallExprContext ctx) {
        if (!"POSITION".equals(SqlIdentifiers.canonicalText(ctx.functionName().getText()))
                || ctx.DISTINCT() != null || ctx.functionArgList() == null) {
            return null;
        }
        final FrostlakeParser.BooleanExprContext arg = ctx.functionArgList().functionArg(0).booleanExpr();
        if (!(arg instanceof FrostlakeParser.ValueExprContext)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext expr = ((FrostlakeParser.ValueExprContext) arg).expression();
        final boolean membership = expr instanceof FrostlakeParser.InSubqueryExprContext
            || expr instanceof FrostlakeParser.InListExprContext
            || expr instanceof FrostlakeParser.TupleInSubqueryExprContext
            || expr instanceof FrostlakeParser.TupleInListExprContext
            || expr instanceof FrostlakeParser.TupleInFlatListExprContext;
        return membership ? expr : null;
    }

    /** The NOT a membership test was written with, or null for a plain IN. */
    private static TerminalNode membershipNot(final FrostlakeParser.ExpressionContext test) {
        if (test instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) test).NOT();
        }
        if (test instanceof FrostlakeParser.InListExprContext) {
            return ((FrostlakeParser.InListExprContext) test).NOT();
        }
        if (test instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) test).NOT();
        }
        if (test instanceof FrostlakeParser.TupleInListExprContext) {
            return ((FrostlakeParser.TupleInListExprContext) test).NOT();
        }
        return ((FrostlakeParser.TupleInFlatListExprContext) test).NOT();
    }

    /**
     * POSITION's IN form has no NOT and takes nothing after it. Live's parser reports the word it did
     * not expect, then the token its recovery stopped at, POSITION's closing parenthesis:
     *
     * <pre>
     *   SELECT POSITION('b' NOT IN (SELECT 'abc'))   … position 20 unexpected 'NOT'. … position 41 unexpected ')'.
     *   SELECT POSITION('b' IN (SELECT 'abc'), 1)    … position 37 unexpected ','.   … position 40 unexpected ')'.
     * </pre>
     */
    private static void rejectPositionMembershipSyntax(final FrostlakeParser.FunctionCallExprContext ctx,
                                                       final FrostlakeParser.ExpressionContext test) {
        final TerminalNode not = membershipNot(test);
        if (not != null) {
            throw syntaxPair(not.getSymbol(), ctx.RPAREN().getSymbol());
        }
        if (ctx.functionArgList().functionArg().size() > 1) {
            throw syntaxPair(ctx.functionArgList().COMMA(0).getSymbol(), ctx.RPAREN().getSymbol());
        }
    }

    /** Live's two-line syntax refusal: the token it did not expect, then the one its recovery stopped at. */
    private static RuntimeException syntaxPair(final Token unexpected, final Token recoveredAt) {
        return new RuntimeException("SQL compilation error:\n" + syntaxErrorAt(unexpected) + "\n"
            + syntaxErrorAt(recoveredAt));
    }

    private static String syntaxErrorAt(final Token token) {
        final SourcePosition within = new SourcePosition(token.getLine(), token.getCharPositionInLine());
        final SourcePosition at = ExpressionSource.resolve(within);
        final SourcePosition where = at == null ? within : at;
        return "syntax error line " + where.getLine() + " at position " + where.getCharPositionInLine()
            + " unexpected '" + token.getText() + "'.";
    }

    /**
     * POSITION's IN form taken apart into needle and haystack — one value IN a one-column subquery or a
     * one-value list — or null when either side holds more than one value. Such a form stays the
     * membership test it parsed as, and the evaluator refuses the ROW that side is.
     */
    private Expression positionInForm(final FrostlakeParser.FunctionCallExprContext ctx,
                                      final FrostlakeParser.ExpressionContext test) {
        final List<FrostlakeParser.ExpressionContext> needle;
        final FrostlakeParser.SelectStatementContext query;
        final List<FrostlakeParser.ExpressionContext> list;
        if (test instanceof FrostlakeParser.InSubqueryExprContext) {
            final FrostlakeParser.InSubqueryExprContext in = (FrostlakeParser.InSubqueryExprContext) test;
            needle = Collections.singletonList(in.expression());
            query = in.selectStatement();
            list = null;
        } else if (test instanceof FrostlakeParser.InListExprContext) {
            final FrostlakeParser.InListExprContext in = (FrostlakeParser.InListExprContext) test;
            needle = Collections.singletonList(in.expression());
            query = null;
            list = in.expressionList().expression();
        } else if (test instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            final FrostlakeParser.TupleInSubqueryExprContext tuple =
                (FrostlakeParser.TupleInSubqueryExprContext) test;
            needle = tuple.expressionList().expression();
            query = tuple.selectStatement();
            list = null;
        } else if (test instanceof FrostlakeParser.TupleInFlatListExprContext) {
            final FrostlakeParser.TupleInFlatListExprContext tuple =
                (FrostlakeParser.TupleInFlatListExprContext) test;
            needle = tuple.expressionList(0).expression();
            query = null;
            list = tuple.expressionList(1).expression();
        } else {
            final FrostlakeParser.TupleInListExprContext tuple = (FrostlakeParser.TupleInListExprContext) test;
            if (tuple.tupleRow().size() != 1) {
                return null;
            }
            needle = tuple.expressionList().expression();
            query = null;
            list = tuple.tupleRow(0).expressionList().expression();
        }
        if (needle.size() != 1 || (query != null ? selectItemCount(query) > 1 : list.size() != 1)) {
            return null;
        }
        final Expression haystack;
        if (query != null) {
            final SubqueryExpression subquery = new SubqueryExpression(originalText(query));
            subquery.setPosition(new SourcePosition(query.getStart().getLine(),
                query.getStart().getCharPositionInLine()));
            haystack = subquery;
        } else {
            haystack = visit(list.get(0));
        }
        final List<Expression> args = new ArrayList<>();
        args.add(visit(needle.get(0)));
        args.add(haystack);
        final FunctionCallExpression position = new FunctionCallExpression("POSITION", args, false, false);
        position.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        return position;
    }

    /**
     * How many items a subquery projects, read from its text: the select list of one SELECT block, or
     * -1 when that is not known without planning it — a set operation, or a star.
     */
    private static int selectItemCount(final FrostlakeParser.SelectStatementContext query) {
        if (query.selectOperand().size() != 1) {
            return -1;
        }
        final FrostlakeParser.SelectOperandContext operand = query.selectOperand(0);
        if (operand.selectClause() == null) {
            return operand.selectStatement() != null ? selectItemCount(operand.selectStatement()) : -1;
        }
        final List<FrostlakeParser.SelectItemContext> items = operand.selectClause().selectList().selectItem();
        for (final FrostlakeParser.SelectItemContext item : items) {
            if (item instanceof FrostlakeParser.StarItemContext
                    || item instanceof FrostlakeParser.QualifiedStarItemContext) {
                return -1;
            }
        }
        return items.size();
    }

    /**
     * The single expression a trailing {@code WITHIN GROUP (ORDER BY …)} orders by, or null when the
     * call has no such clause or its ORDER BY does not hold exactly one item. The percentiles are the
     * only functions typed from it, and live refuses them outright with anything but one item, so a
     * longer list is not something to carry.
     */
    private Expression withinGroupOrdered(final FrostlakeParser.FunctionCallExprContext ctx) {
        if (ctx.withinGroupClause() == null || ctx.withinGroupClause().orderByClause() == null) {
            return null;
        }
        final List<FrostlakeParser.OrderItemContext> items =
            ctx.withinGroupClause().orderByClause().orderItem();
        return items.size() == 1 ? visit(items.get(0).expression()) : null;
    }

    /** Canonical per-identifier parts of a function name, or null when it has no identifier parts
     *  (LIKE/ILIKE keyword calls) — the flattened text spelling stands alone then. */
    private List<String> functionNameParts(final FrostlakeParser.FunctionNameContext nameCtx) {
        final String[] parts = ParseTreeText.functionNameParts(nameCtx);
        return parts == null ? null : new ArrayList<>(Arrays.asList(parts));
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
        final FunctionCallExpression namedCall =
            new FunctionCallExpression(
                SqlIdentifiers.canonicalText(ctx.functionName().getText()), args, names);
        namedCall.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        return namedCall;
    }

    @Override
    public Expression visitTupleInListExpr(final FrostlakeParser.TupleInListExprContext ctx) {
        final List<List<Expression>> rows = new ArrayList<>();
        for (final FrostlakeParser.TupleRowContext row : ctx.tupleRow()) {
            rows.add(argList(row.expressionList()));
        }
        return TupleInExpression.ofTupleRows(argList(ctx.expressionList()), rows, ctx.NOT() != null);
    }

    @Override
    public Expression visitTupleInFlatListExpr(final FrostlakeParser.TupleInFlatListExprContext ctx) {
        return TupleInExpression.ofFlatList(
            argList(ctx.expressionList(0)), argList(ctx.expressionList(1)), ctx.NOT() != null);
    }

    @Override
    public Expression visitTupleInSubqueryExpr(final FrostlakeParser.TupleInSubqueryExprContext ctx) {
        return TupleInExpression.ofSubquery(
            argList(ctx.expressionList()),
            new SubqueryExpression(originalText(ctx.selectStatement())),
            ctx.NOT() != null);
    }

    @Override
    public Expression visitBindVarExpr(final FrostlakeParser.BindVarExprContext ctx) {
        // From the CONTEXT's start, not the identifier's: live reports the colon's offset.
        return new BindVariableExpression(ctx.identifier() != null
            ? ctx.identifier().getText()
            : ctx.INTEGER_LITERAL().getText(),
            new SourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
    }

    @Override
    public Expression visitPositionalBindExpr(final FrostlakeParser.PositionalBindExprContext ctx) {
        // A bare positional bind '?' is substituted with its value in the query TEXT before the query is
        // parsed for execution — by the client's bind parameters, OPEN … USING or EXECUTE IMMEDIATE …
        // USING — so reaching AST construction means none was supplied. Snowflake refuses that at compile
        // time with the sentence it uses for an unsupplied :1, positioned on the '?' itself.
        final SourcePosition at = ExpressionSource.resolve(
            new SourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        final String unset = "Bind variable ? not set.";
        throw new RuntimeException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), unset)
            : SqlCompilationError.of(unset));
    }

    @Override
    public Expression visitFunctionCallStarExpr(final FrostlakeParser.FunctionCallStarExprContext ctx) {
        // COUNT(*) / COUNT(DISTINCT *) / COUNT(t.*) — a star call, with its qualifier and its EXCLUDE /
        // ILIKE filters carried for the star-aware callers.
        final FunctionCallExpression call = starCall(ctx.functionName().getText().toUpperCase(),
            ctx.DISTINCT() != null, ctx.starQualifiedName(), ctx.starArgumentModifier());
        // Positioned like any other call, so the star's expanded-arity refusal can point at it.
        call.setPosition(new SourcePosition(ctx.getStart().getLine(),
            ctx.getStart().getCharPositionInLine()));
        return call;
    }

    /**
     * A star-shaped call carrying what its star was written with: the relation a qualified star
     * names (the LAST part, as a column's qualifier — {@code COUNT(db.sch.t.*)} and {@code COUNT(t.*)}
     * resolve alike, live-verified), the EXCLUDE names and the ILIKE pattern.
     */
    private static FunctionCallExpression starCall(final String functionName, final boolean distinct,
                                                   final FrostlakeParser.StarQualifiedNameContext qualifier,
                                                   final List<FrostlakeParser.StarArgumentModifierContext> modifiers) {
        final FunctionCallExpression call = new FunctionCallExpression(functionName, new ArrayList<>(), distinct, true);
        final List<String> excludes = new ArrayList<>();
        for (final FrostlakeParser.StarArgumentModifierContext mod : modifiers) {
            if (mod.ILIKE() != null) {
                final String written = mod.STRING_LITERAL().getText();
                call.setStarIlike(written.substring(1, written.length() - 1));
                continue;
            }
            for (final FrostlakeParser.IdentifierContext id : mod.identifier()) {
                excludes.add(SqlIdentifiers.canonical(id).toUpperCase());
            }
        }
        call.setStarExcludes(excludes);
        if (qualifier != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(qualifier);
            call.setStarQualifier(parts[parts.length - 1].toUpperCase());
        }
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
        final FunctionCallExpression namedCall = new FunctionCallExpression(
            SqlIdentifiers.canonicalText(ctx.functionName().getText()), args, names);
        // Positioned like any other call, so a refusal of its arguments points at it the way live does.
        namedCall.setPosition(new SourcePosition(
            ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()));
        return namedCall;
    }

    @Override
    public Expression visitSystemFuncExpr(final FrostlakeParser.SystemFuncExprContext ctx) {
        final FunctionCallExpression call = new FunctionCallExpression(
            ctx.SYSTEM_FUNC().getText().toUpperCase(), argList(ctx.booleanExprList()));
        // Positioned like any other call, so an arity refusal can point at it the way live does.
        call.setPosition(new SourcePosition(ctx.getStart().getLine(),
            ctx.getStart().getCharPositionInLine()));
        return call;
    }

    @Override
    public Expression visitSystemStreamHasDataExpr(final FrostlakeParser.SystemStreamHasDataExprContext ctx) {
        // A count other than one goes to the ordinary call node, whose measured arity table refuses it
        // the way live does; only the legal one-argument shape gets the dedicated stream node.
        final List<FrostlakeParser.ExpressionContext> args = ctx.expressionList() == null
            ? new ArrayList<FrostlakeParser.ExpressionContext>() : ctx.expressionList().expression();
        if (args.size() != 1) {
            final List<Expression> built = new ArrayList<>();
            for (final FrostlakeParser.ExpressionContext arg : args) {
                built.add(visit(arg));
            }
            final FunctionCallExpression call = new FunctionCallExpression(
                ctx.SYSTEM_STREAM_HAS_DATA().getText().toUpperCase(), built);
            call.setPosition(new SourcePosition(ctx.getStart().getLine(),
                ctx.getStart().getCharPositionInLine()));
            return call;
        }
        return new SystemStreamHasDataExpression(visit(args.get(0)));
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

    /**
     * The same, anchored on the OPERATOR token so an argument-type refusal can point where Snowflake
     * points. ANTLR's line is 1-based and its character position 0-based within that line, which is
     * exactly the pair live prints (live-verified: a multi-line statement reads
     * {@code error line 2 at position 5}, counting from the start of line 2).
     */
    private Expression binary(final ParserRuleContext left,
                              final BinaryOperator op,
                              final ParserRuleContext right,
                              final Token operatorToken) {
        final BinaryOperationExpression node =
            new BinaryOperationExpression(visit(left), op, visit(right));
        if (operatorToken != null) {
            node.setPosition(new SourcePosition(operatorToken.getLine(),
                operatorToken.getCharPositionInLine()));
        }
        return node;
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

    /** The arguments of a SYSTEM$ call, each a boolean expression as a select item's would be. */
    private List<Expression> argList(final FrostlakeParser.BooleanExprListContext list) {
        final List<Expression> args = new ArrayList<>();
        if (list != null) {
            for (final FrostlakeParser.BooleanExprContext arg : list.booleanExpr()) {
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
        if (arg.selectStatement() != null) {
            // ABS(SELECT -1): a subquery without parentheses of its own, read as the parenthesised one is.
            final SubqueryExpression subquery = new SubqueryExpression(originalText(arg.selectStatement()));
            subquery.setPosition(new SourcePosition(arg.selectStatement().getStart().getLine(),
                arg.selectStatement().getStart().getCharPositionInLine()));
            return subquery;
        }
        if (arg.STAR() != null) {
            // The relation's columns are not known yet, so the star rides along whole and is spliced
            // into the list when the call is evaluated (ExpressionEvaluatorVisitor.splicedStarArguments).
            final ColumnReferenceExpression star = new ColumnReferenceExpression("*");
            star.describeStar(StarArgument.of(arg));
            return star;
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

    /**
     * The same decode, positioned. The reader refuses an unpaired surrogate escape, and live reports
     * that at the LITERAL's own start — not at the call around it, which is how the refusal announces
     * that it came from the reader rather than from any function. Verified across seven offsets and
     * onto a second line.
     *
     * @param raw   the literal token's text, quotes included
     * @param token the token itself, for the place to report
     * @return the decoded text
     */
    private String unquoteStringAt(final String raw, final Token token) {
        try {
            return SqlStringLiterals.decode(raw);
        } catch (final RuntimeException refused) {
            if (String.valueOf(refused.getMessage()).startsWith("Invalid Unicode string literal;")) {
                // RESOLVED against the enclosing fragment, not taken raw. An expression is re-parsed on
                // its own, so the token's own position is an offset INTO that fragment — 0 for the whole
                // of `'\uD800'` — and only the resolver knows where the fragment sits in the statement.
                final SourcePosition at = ExpressionSource.resolve(
                    new SourcePosition(token.getLine(), token.getCharPositionInLine()));
                throw new RuntimeException(
                    SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), refused.getMessage()));
            }
            throw refused;
        }
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
