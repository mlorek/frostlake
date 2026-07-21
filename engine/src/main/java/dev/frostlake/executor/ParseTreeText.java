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
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Stateless helpers that read text and names out of ANTLR parse-tree nodes. Extracted from
 * {@link QueryExecutor} so query stages can share the same parse-tree text extraction without
 * depending on engine instance state — every method here is a pure function of its arguments.
 */
public final class ParseTreeText {

    private ParseTreeText() {
    }

    /** Get the effective whereClause from a selectClause (handles the list produced by the grammar). */
    public static FrostlakeParser.WhereClauseContext getWhereClause(final FrostlakeParser.SelectClauseContext ctx) {
        List<FrostlakeParser.WhereClauseContext> list = ctx.whereClause();
        if (list == null || list.isEmpty()) return null;
        // Prefer the first non-null entry
        for (final FrostlakeParser.WhereClauseContext wc : list) {
            if (wc != null) return wc;
        }
        return null;
    }

    /** Extract SelectClauseContext from a selectOperand (bare or parenthesised). */
    public static FrostlakeParser.SelectClauseContext getSelectClause(final FrostlakeParser.SelectOperandContext op) {
        if (op.selectClause() != null) return op.selectClause();
        return null; // parenthesised sub-select — caller handles via op.selectStatement()
    }

    /** Collect all top-level SelectClauseContexts from selectOperands (non-parenthesised ones). */
    public static List<FrostlakeParser.SelectClauseContext> getSelectClauses(final FrostlakeParser.SelectStatementContext ctx) {
        List<FrostlakeParser.SelectClauseContext> result = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext op : ctx.selectOperand()) {
            if (op.selectClause() != null) result.add(op.selectClause());
        }
        return result;
    }

    public static String getIdentifier(final FrostlakeParser.IdentifierContext ctx) {
        if (ctx.QUOTED_IDENTIFIER() != null) {
            String quoted = ctx.QUOTED_IDENTIFIER().getText();
            return quoted.substring(1, quoted.length() - 1);
        }
        if (ctx.POSITIONAL_PARAMETER() != null) {
            String param = ctx.POSITIONAL_PARAMETER().getText();
            int position = Integer.parseInt(param.substring(1));
            return "COLUMN" + position;
        }
        // Handle regular identifiers and keywords used as identifiers
        return ctx.getText();
    }

    public static String getQualifiedName(final FrostlakeParser.QualifiedNameContext ctx) {
        List<String> parts = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext id : ctx.identifier()) {
            parts.add(getIdentifier(id));
        }
        return String.join(".", parts);
    }

    /** The identifier parts of a qualified name, read from the parse tree instead of splitting its
     *  flattened text on '.' — correct even for a quoted identifier containing a dot. */
    public static String[] qualifiedNameParts(final FrostlakeParser.QualifiedNameContext ctx) {
        final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
        final String[] parts = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            parts[i] = getIdentifier(ids.get(i));
        }
        return parts;
    }

    public static String getOriginalText(final ParserRuleContext ctx) {
        // Get original text with whitespace preserved
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex())
        );
    }

    public static String extractStringLiteral(final TerminalNode node) {
        return SqlStringLiterals.decode(node.getText());
    }

    public static String getIdentifier(final TerminalNode node) {
        String text = node.getText();
        if (text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    public static Object parseLiteral(final FrostlakeParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Long.parseLong(ctx.INTEGER_LITERAL().getText());
        } else if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            return text.substring(1, text.length() - 1);
        } else if (ctx.TRUE() != null) {
            return true;
        } else if (ctx.FALSE() != null) {
            return false;
        } else if (ctx.NULL() != null) {
            return null;
        }
        return null;
    }
}
