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

import dev.frostlake.executor.expressions.IntervalStringText;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateless accessors over SELECT-item parse-tree nodes — star / qualified-star / braced-star / expression
 * item predicates, plus the alias, qualifier and value/full expression of an item. Extracted from
 * {@link QueryExecutor}; pure functions over ANTLR {@code FrostlakeParser} contexts with no engine state.
 */
public final class SelectItemAccessors {

    public static boolean isStarItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.StarItemContext;
    }

    public static boolean isQualifiedStarItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.QualifiedStarItemContext;
    }

    /**
     * Snowflake's braced star ({@code {*}}, {@code {* EXCLUDE (c)}}, {@code {t.*}}) — an OBJECT
     * constructor over the row, i.e. {@code OBJECT_CONSTRUCT(*)}. It projects ONE object-valued column,
     * never the star's N columns, so it is deliberately NOT a star item.
     */
    public static boolean isObjectStarItem(final FrostlakeParser.SelectItemContext item) {
        if (!(item instanceof FrostlakeParser.ObjectStarItemContext)) {
            return false;
        }
        rejectProjectionRewritingModifiers((FrostlakeParser.ObjectStarItemContext) item);
        return true;
    }

    /**
     * A braced star accepts only the modifiers that PICK columns — {@code EXCLUDE} and {@code ILIKE}.
     * {@code RENAME} and {@code REPLACE} rewrite the projection, and Snowflake's grammar has no place for them
     * there: the keyword is a syntax error and so is the token after it — {@code {* REPLACE (a + 1 AS a)}} is
     * "unexpected 'REPLACE'" then "unexpected '('", {@code {* RENAME a AS z}} "unexpected 'RENAME'" then
     * "unexpected 'a'" (live-verified), while {@code {* EXCLUDE (a)}} and {@code {* ILIKE 'a%'}} run. They
     * stay legal on a plain {@code *}.
     */
    private static void rejectProjectionRewritingModifiers(final FrostlakeParser.ObjectStarItemContext star) {
        for (final FrostlakeParser.StarModifierContext modifier : star.starModifier()) {
            if (modifier.RENAME() == null && modifier.REPLACE() == null) {
                continue;
            }
            final ParseTree after = modifier.getChild(1);
            final Token next = after instanceof TerminalNode ? ((TerminalNode) after).getSymbol()
                : ((ParserRuleContext) after).getStart();
            final List<String> lines = new ArrayList<>();
            lines.add(unexpected(modifier.getStart()));
            lines.add(unexpected(next));
            final CharStream text = modifier.getStart().getInputStream();
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines,
                text.getText(Interval.of(0, text.size() - 1)));
        }
    }

    /** One syntax-error line naming a token where it was written. */
    private static String unexpected(final Token token) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + token.getText() + "'.";
    }

    public static boolean isExprItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.ExprItemContext;
    }

    /**
     * Snowflake refuses to project the UNIT-LESS interval literal on its own: {@code SELECT INTERVAL
     * '1 day'} and the multi-part {@code SELECT INTERVAL '1 day, 2 hours'} both fail with "interval
     * literal is not supported in this form", whose detail line opens with {@code ": "} (live-verified). The UNIT-SUFFIXED spelling is a first-class
     * value and projects fine — {@code SELECT INTERVAL '1' DAY} is typed {@code INTERVAL DAY(9)}, aliases
     * fine, and {@code TO_VARCHAR} of it is {@code +1} — so only {@code IntervalStringExpr} is rejected
     * here, never {@code IntervalExpr}.
     *
     * <p>Both spellings stay legal as an operand of date arithmetic, so the test is on each item's ROOT
     * expression: in {@code DATE '2024-01-01' + INTERVAL '1 day'} the root is the addition and the item
     * stands.
     */
    public static void rejectStandaloneInterval(final FrostlakeParser.SelectListContext selectList) {
        if (selectList == null) {
            return;
        }
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            final ParserRuleContext value = getItemValueExpr(item);
            if (value instanceof FrostlakeParser.IntervalStringExprContext) {
                // The literal's text is read first: one that does not read is refused as such, at its own
                // token, ahead of this sentence (live-verified).
                IntervalStringText.chain(SqlStringLiterals.decode(
                    ((FrostlakeParser.IntervalStringExprContext) value).STRING_LITERAL().getText()));
                throw new RuntimeException(SqlCompilationError.at(0, -1,
                    ": interval literal is not supported in this form."));
            }
        }
    }

    /**
     * The select item's full expression context (the {@code booleanExpr} wrapper) — for TEXT use:
     * {@code getOriginalText}/{@code getText} of the whole projected item. Returns the full item
     * even once {@code booleanExpr} carries a real OR/AND/NOT body (e.g. {@code SELECT a AND b}).
     */
    public static ParserRuleContext getItemExpression(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.ExprItemContext ? ((FrostlakeParser.ExprItemContext) item).booleanExpr() : null;
    }

    /**
     * The select item's underlying value expression — for STRUCTURAL use: aggregate/window
     * detection and per-group evaluation. Today {@code booleanExpr} is a trivial pass-through so
     * this is simply its inner {@code expression}; when {@code booleanExpr} gains a real OR/AND/NOT
     * body this becomes "unwrap the value alternative" and is null for a boolean projection.
     */
    public static FrostlakeParser.ExpressionContext getItemValueExpr(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.ExprItemContext
            ? unwrapValue(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
    }

    /** Peel a booleanExpr to its underlying value expression (null if it is a boolean AND/OR/NOT). */
    public static FrostlakeParser.ExpressionContext unwrapValue(final FrostlakeParser.BooleanExprContext be) {
        return be instanceof FrostlakeParser.ValueExprContext ? ((FrostlakeParser.ValueExprContext) be).expression() : null;
    }

    /**
     * The expression under any number of wrapping parentheses; a parenthesized BOOLEAN body
     * ({@code (a AND b)}) stays put. Snowflake treats a parenthesized column reference exactly like
     * the bare one when naming and validating a select item — {@code SELECT (amount)} projects a
     * column named {@code AMOUNT}, and a CTAS accepts it without an alias (both live-verified).
     */
    public static FrostlakeParser.ExpressionContext unwrapParens(final FrostlakeParser.ExpressionContext expr) {
        FrostlakeParser.ExpressionContext current = expr;
        while (current instanceof FrostlakeParser.ParenExprContext) {
            final FrostlakeParser.ExpressionContext inner =
                unwrapValue(((FrostlakeParser.ParenExprContext) current).booleanExpr());
            if (inner == null) {
                return current;
            }
            current = inner;
        }
        return current;
    }

    /**
     * Whether a select item's value is, under any wrapping parentheses, a sequence read: a reference of two or
     * more parts whose last is {@code NEXTVAL}. Unaliased it is named after that pseudo-column however it is
     * qualified or spaced — {@code SELECT s1.nextval}, {@code SELECT "q""x".nextval}, {@code SELECT (s1.nextval)}
     * and {@code SELECT s1 . nextval} all come back labelled NEXTVAL (live-verified).
     */
    public static boolean isSequenceRead(final FrostlakeParser.ExpressionContext value) {
        final FrostlakeParser.ExpressionContext simple = value == null ? null : unwrapParens(value);
        if (!(simple instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return false;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) simple).qualifiedName());
        return parts.length > 1 && "NEXTVAL".equalsIgnoreCase(parts[parts.length - 1]);
    }

    /**
     * Whether this select item is an EXPRESSION with no name of its own: unaliased and not a column
     * reference. A (possibly parenthesized, possibly qualified) column reference and a
     * {@code CONNECT_BY_ROOT col} item take the column's name; every other unaliased expression has
     * only its source text. CTAS and CREATE VIEW refuse such items with "Missing column
     * specification" (live-verified) because the projection cannot supply a declared column name.
     * A boolean-bodied item ({@code SELECT a AND b}) has no value expression and no name either.
     */
    public static boolean isUnnamedExpressionItem(final FrostlakeParser.SelectItemContext item) {
        if (!isExprItem(item) || getItemAlias(item) != null) {
            return false;
        }
        final FrostlakeParser.ExpressionContext valueExpr = getItemValueExpr(item);
        if (valueExpr == null) {
            return true;
        }
        final FrostlakeParser.ExpressionContext simple = unwrapParens(valueExpr);
        return !(simple instanceof FrostlakeParser.QualifiedNameExprContext)
            && !(simple instanceof FrostlakeParser.ConnectByRootExprContext);
    }

    /**
     * A select item's alias as CANONICAL TEXT, or null when it has none. Text rather than a parse
     * context because the two spellings reach different rules: `AS x` matches the wider aliasName
     * (which admits CASE, CAST, CONSTRAINT, CROSS, DEFAULT, INNER, JOIN and WHEN, all live-legal
     * there), while the bare `x` matches identifier. Every caller wanted the canonical name anyway.
     */
    public static String getItemAlias(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.ExprItemContext) {
            final FrostlakeParser.ExprItemContext expr = (FrostlakeParser.ExprItemContext) item;
            if (expr.aliasName() != null) {
                return ParseTreeText.getIdentifier(expr.aliasName());
            }
            return expr.identifier() != null ? ParseTreeText.getIdentifier(expr.identifier()) : null;
        }
        if (item instanceof FrostlakeParser.ObjectStarItemContext) {
            final FrostlakeParser.ObjectStarItemContext star = (FrostlakeParser.ObjectStarItemContext) item;
            if (star.aliasName() != null) {
                return ParseTreeText.getIdentifier(star.aliasName());
            }
            return star.identifier() != null ? ParseTreeText.getIdentifier(star.identifier()) : null;
        }
        return null;
    }

    /** The EXCLUDE/RENAME/REPLACE/ILIKE modifiers on a {@code *}, {@code t.*} or {@code {*}} item (empty if none). */
    public static List<FrostlakeParser.StarModifierContext> getStarModifiers(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.StarItemContext) {
            return validateModifierOrder(((FrostlakeParser.StarItemContext) item).starModifier());
        }
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            return validateModifierOrder(((FrostlakeParser.QualifiedStarItemContext) item).starModifier());
        }
        if (item instanceof FrostlakeParser.ObjectStarItemContext) {
            return validateModifierOrder(((FrostlakeParser.ObjectStarItemContext) item).starModifier());
        }
        return Collections.emptyList();
    }

    /**
     * The expressions a plain or qualified star's REPLACE substitutes, in the order written — the SELECT list's
     * own names, which live resolves where they stand: {@code SELECT * REPLACE (nosuch AS id), nosuch2} refuses
     * NOSUCH at its own position first. Empty for any other item.
     */
    public static List<ParserRuleContext> getStarReplaceExpressions(final FrostlakeParser.SelectItemContext item) {
        if (!isStarItem(item) && !isQualifiedStarItem(item)) {
            return Collections.emptyList();
        }
        final List<ParserRuleContext> replaced = new ArrayList<>();
        for (final FrostlakeParser.StarModifierContext modifier : getStarModifiers(item)) {
            for (final FrostlakeParser.StarReplaceItemContext replace : modifier.starReplaceItem()) {
                replaced.add(replace.expression());
            }
        }
        return replaced;
    }

    /**
     * The modifiers come in ONE fixed sequence — ILIKE-or-EXCLUDE (mutually exclusive), then
     * REPLACE, then RENAME, each at most once. Anything out of order, repeated, or combining ILIKE
     * with EXCLUDE is a syntax error at the offending keyword, live-verified cell by cell
     * ({@code * RENAME … EXCLUDE …}, {@code * ILIKE … EXCLUDE …}, {@code * EXCLUDE … EXCLUDE …} and
     * {@code * RENAME … REPLACE …} all refuse; {@code * EXCLUDE … REPLACE … RENAME …} runs).
     */
    private static List<FrostlakeParser.StarModifierContext> validateModifierOrder(
            final List<FrostlakeParser.StarModifierContext> modifiers) {
        int previousRank = -1;
        for (final FrostlakeParser.StarModifierContext modifier : modifiers) {
            final int rank = modifier.REPLACE() != null ? 1 : modifier.RENAME() != null ? 2 : 0;
            if (rank <= previousRank) {
                final Token start = modifier.getStart();
                throw new RuntimeException(SqlCompilationError.of("syntax error line "
                    + start.getLine() + " at position " + start.getCharPositionInLine()
                    + " unexpected '" + start.getText() + "'."));
            }
            previousRank = rank;
        }
        return modifiers;
    }

    /** Returns qualifier for the {@code t.*} / {@code {t.*}} forms, or null for a bare star. */
    /**
     * A qualified star's qualifier by its canonical parts, quotes read: {@code "fz".*} is {@code fz}, {@code fz.*} is
     * {@code FZ}; null for an item that is no qualified star.
     */
    public static String[] getItemQualifierParts(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.StarQualifiedNameContext qualifier = item instanceof FrostlakeParser.QualifiedStarItemContext
            ? ((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName()
            : item instanceof FrostlakeParser.ObjectStarItemContext
                ? ((FrostlakeParser.ObjectStarItemContext) item).starQualifiedName() : null;
        return qualifier == null ? null : ParseTreeText.qualifiedNameParts(qualifier);
    }

    /**
     * A qualified star's qualifier spelled back from its canonical parts as SQL text that reads as the same
     * relation again: {@code "fz"}, {@code FZ}, {@code DB.PUBLIC."x y"}, {@code "a""b"}; null for an item that
     * is no qualified star.
     */
    public static String getItemQualifierSpelled(final FrostlakeParser.SelectItemContext item) {
        final String[] parts = getItemQualifierParts(item);
        if (parts == null) {
            return null;
        }
        final StringBuilder spelled = new StringBuilder();
        for (final String part : parts) {
            spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonicalEscaped(part));
        }
        return spelled.toString();
    }

    /**
     * A qualified star's qualifier as a refusal echoes it: {@link #getItemQualifierSpelled} with a quote a part
     * holds printed once, {@code "a"b"}; null for an item that is no qualified star.
     */
    public static String getItemQualifierEchoed(final FrostlakeParser.SelectItemContext item) {
        final String[] parts = getItemQualifierParts(item);
        if (parts == null) {
            return null;
        }
        final StringBuilder spelled = new StringBuilder();
        for (final String part : parts) {
            spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return spelled.toString();
    }

    public static String getItemQualifier(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.QualifiedStarItemContext)
            return ParseTreeText.writtenText(((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName());
        if (item instanceof FrostlakeParser.ObjectStarItemContext
                && ((FrostlakeParser.ObjectStarItemContext) item).starQualifiedName() != null)
            return ParseTreeText.writtenText(((FrostlakeParser.ObjectStarItemContext) item).starQualifiedName());
        return null;
    }

    /**
     * The output column name Snowflake gives a braced-star item: the item's own source form with its
     * identifiers canonicalised — {@code {*}}, {@code {* EXCLUDE (A)}}, {@code {T.*}} — or its explicit
     * alias when it carries one. Rebuilt from the parse tree rather than re-read off the SQL text.
     */
    public static String objectStarLabel(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ObjectStarItemContext star = (FrostlakeParser.ObjectStarItemContext) item;
        // Either spelling of the alias wins over the echoed source form: `AS x` reaches the wider
        // aliasName rule, the bare `x` reaches identifier.
        final String alias = getItemAlias(item);
        if (alias != null) {
            return alias;
        }
        final StringBuilder label = new StringBuilder("{");
        if (star.starQualifiedName() != null) {
            // The qualifier as written, unquoted parts upper-cased and quoted ones kept whole: {"FZ".*} (live-verified).
            final List<ParseTree> parts = new ArrayList<>();
            parts.add(star.starQualifiedName().nameStartPart());
            parts.addAll(star.starQualifiedName().namePart());
            for (int i = 0; i < parts.size(); i++) {
                final String written = parts.get(i).getText();
                label.append(i > 0 ? "." : "").append(written.startsWith("\"") ? written : written.toUpperCase());
            }
            label.append('.');
        }
        label.append('*');
        for (final FrostlakeParser.StarModifierContext modifier : star.starModifier()) {
            label.append(' ').append(starModifierLabel(modifier));
        }
        return label.append('}').toString();
    }

    /** An excluded column's name, whichever word wrote it — DEFAULT reaches this list and no other. */
    public static String excludedName(final FrostlakeParser.ExcludedColumnContext excluded) {
        return excluded.identifier() != null ? ParseTreeText.getIdentifier(excluded.identifier())
            : excluded.getText().toUpperCase(java.util.Locale.ROOT);
    }

    /** One star modifier rendered back in Snowflake's echoed form, identifiers canonicalised. */
    private static String starModifierLabel(final FrostlakeParser.StarModifierContext modifier) {
        if (modifier.ILIKE() != null) {
            return "ILIKE " + modifier.STRING_LITERAL().getText();
        }
        if (modifier.EXCLUDE() != null) {
            // The account echoes the modifier AS WRITTEN: a bare list stays bare, a parenthesised one
            // keeps its parentheses. A client reading the column by name misses it otherwise.
            final boolean parenthesised = modifier.LPAREN() != null;
            final StringBuilder excluded = new StringBuilder("EXCLUDE ");
            excluded.append(parenthesised ? "(" : "");
            for (int i = 0; i < modifier.excludedColumn().size(); i++) {
                if (i > 0) {
                    excluded.append(", ");
                }
                excluded.append(excludedName(modifier.excludedColumn(i)));
            }
            return excluded.append(parenthesised ? ")" : "").toString();
        }
        if (modifier.RENAME() != null) {
            final StringBuilder renamed = new StringBuilder("RENAME (");
            for (int i = 0; i < modifier.starRenameItem().size(); i++) {
                if (i > 0) {
                    renamed.append(", ");
                }
                final FrostlakeParser.StarRenameItemContext rename = modifier.starRenameItem(i);
                renamed.append(ParseTreeText.getIdentifier(rename.identifier(0))).append(" AS ")
                    .append(ParseTreeText.getIdentifier(rename.identifier(1)));
            }
            return renamed.append(')').toString();
        }
        final StringBuilder replaced = new StringBuilder("REPLACE (");
        for (int i = 0; i < modifier.starReplaceItem().size(); i++) {
            if (i > 0) {
                replaced.append(", ");
            }
            final FrostlakeParser.StarReplaceItemContext replace = modifier.starReplaceItem(i);
            replaced.append(ParseTreeText.getOriginalText(replace.expression())).append(" AS ")
                .append(ParseTreeText.getIdentifier(replace.identifier()));
        }
        return replaced.append(')').toString();
    }

    private SelectItemAccessors() {
    }
}
