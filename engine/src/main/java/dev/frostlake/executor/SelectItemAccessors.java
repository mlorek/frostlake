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
 * Stateless accessors over SELECT-item parse-tree nodes — star / qualified-star / spread / expression item
 * predicates, plus the alias, qualifier and value/full expression of an item. Extracted from
 * {@link QueryExecutor}; pure functions over ANTLR {@code FrostlakeParser} contexts with no engine state.
 */
public final class SelectItemAccessors {

    public static boolean isStarItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.StarItemContext;
    }

    public static boolean isQualifiedStarItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.QualifiedStarItemContext;
    }

    public static boolean isSpreadItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.SpreadItemContext || item instanceof FrostlakeParser.SpreadPrefixItemContext;
    }

    public static boolean isExprItem(final FrostlakeParser.SelectItemContext item) {
        return item instanceof FrostlakeParser.ExprItemContext;
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
        return null;
    }

    /** The EXCLUDE/RENAME/REPLACE/ILIKE modifiers on a {@code *} or {@code t.*} item (empty if none). */
    public static List<FrostlakeParser.StarModifierContext> getStarModifiers(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.StarItemContext) {
            return ((FrostlakeParser.StarItemContext) item).starModifier();
        }
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            return ((FrostlakeParser.QualifiedStarItemContext) item).starModifier();
        }
        return Collections.emptyList();
    }

    /** Returns qualifier for t.* or t.** forms, or null for bare * */
    public static String getItemQualifier(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.QualifiedStarItemContext)
            return ((FrostlakeParser.QualifiedStarItemContext) item).qualifiedName().getText();
        if (item instanceof FrostlakeParser.SpreadItemContext)
            return ((FrostlakeParser.SpreadItemContext) item).qualifiedName().getText();
        if (item instanceof FrostlakeParser.SpreadPrefixItemContext)
            return ((FrostlakeParser.SpreadPrefixItemContext) item).qualifiedName().getText();
        return null;
    }

    private SelectItemAccessors() {
    }
}
