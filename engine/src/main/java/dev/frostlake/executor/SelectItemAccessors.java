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

import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;

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
     * {@code RENAME} and {@code REPLACE} rewrite the projection and Snowflake rejects them there
     * (live-verified: {@code {* RENAME (a AS z)}} and {@code {* REPLACE (a + 1 AS a)}} both fail, while
     * {@code {* EXCLUDE (a)}} and {@code {* ILIKE 'a%'}} run). They stay legal on a plain {@code *}.
     */
    private static void rejectProjectionRewritingModifiers(final FrostlakeParser.ObjectStarItemContext star) {
        for (final FrostlakeParser.StarModifierContext modifier : star.starModifier()) {
            if (modifier.RENAME() != null || modifier.REPLACE() != null) {
                throw new RuntimeException("SQL compilation error:\nsyntax error unexpected '"
                    + (modifier.RENAME() != null ? "RENAME" : "REPLACE")
                    + "'. A braced star {*} accepts only EXCLUDE and ILIKE.");
            }
        }
    }

    public static boolean isExprItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.ExprItemContext;
    }

    /**
     * Snowflake refuses to project the UNIT-LESS interval literal on its own: {@code SELECT INTERVAL
     * '1 day'} and the multi-part {@code SELECT INTERVAL '1 day, 2 hours'} both fail with "interval
     * literal is not supported in this form" (live-verified). The UNIT-SUFFIXED spelling is a first-class
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
            if (getItemValueExpr(item) instanceof FrostlakeParser.IntervalStringExprContext) {
                throw new RuntimeException(SqlCompilationError.at(0, -1,
                    "interval literal is not supported in this form."));
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

    public static FrostlakeParser.IdentifierContext getItemAlias(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.ExprItemContext) return ((FrostlakeParser.ExprItemContext) item).identifier();
        if (item instanceof FrostlakeParser.ObjectStarItemContext) return ((FrostlakeParser.ObjectStarItemContext) item).identifier();
        return null;
    }

    /** The EXCLUDE/RENAME/REPLACE/ILIKE modifiers on a {@code *}, {@code t.*} or {@code {*}} item (empty if none). */
    public static List<FrostlakeParser.StarModifierContext> getStarModifiers(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.StarItemContext) {
            return ((FrostlakeParser.StarItemContext) item).starModifier();
        }
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            return ((FrostlakeParser.QualifiedStarItemContext) item).starModifier();
        }
        if (item instanceof FrostlakeParser.ObjectStarItemContext) {
            return ((FrostlakeParser.ObjectStarItemContext) item).starModifier();
        }
        return Collections.emptyList();
    }

    /** Returns qualifier for the {@code t.*} / {@code {t.*}} forms, or null for a bare star. */
    public static String getItemQualifier(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.QualifiedStarItemContext)
            return ((FrostlakeParser.QualifiedStarItemContext) item).qualifiedName().getText();
        if (item instanceof FrostlakeParser.ObjectStarItemContext
                && ((FrostlakeParser.ObjectStarItemContext) item).qualifiedName() != null)
            return ((FrostlakeParser.ObjectStarItemContext) item).qualifiedName().getText();
        return null;
    }

    /**
     * The output column name Snowflake gives a braced-star item: the item's own source form with its
     * identifiers canonicalised — {@code {*}}, {@code {* EXCLUDE (A)}}, {@code {T.*}} — or its explicit
     * alias when it carries one. Rebuilt from the parse tree rather than re-read off the SQL text.
     */
    public static String objectStarLabel(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ObjectStarItemContext star = (FrostlakeParser.ObjectStarItemContext) item;
        if (star.identifier() != null) {
            return ParseTreeText.getIdentifier(star.identifier());
        }
        final StringBuilder label = new StringBuilder("{");
        if (star.qualifiedName() != null) {
            label.append(ParseTreeText.getQualifiedName(star.qualifiedName())).append('.');
        }
        label.append('*');
        for (final FrostlakeParser.StarModifierContext modifier : star.starModifier()) {
            label.append(' ').append(starModifierLabel(modifier));
        }
        return label.append('}').toString();
    }

    /** One star modifier rendered back in Snowflake's echoed form, identifiers canonicalised. */
    private static String starModifierLabel(final FrostlakeParser.StarModifierContext modifier) {
        if (modifier.ILIKE() != null) {
            return "ILIKE " + modifier.STRING_LITERAL().getText();
        }
        if (modifier.EXCLUDE() != null) {
            final StringBuilder excluded = new StringBuilder("EXCLUDE (");
            for (int i = 0; i < modifier.identifier().size(); i++) {
                if (i > 0) {
                    excluded.append(", ");
                }
                excluded.append(ParseTreeText.getIdentifier(modifier.identifier(i)));
            }
            return excluded.append(')').toString();
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
