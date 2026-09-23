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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;


/**
 * Base interface for SQL command handlers
 */
public interface CommandHandler {

    /**
     * Get text from identifier or qualified name context
     *
     * @param ctx the identifier parse tree
     * @return the canonical name — unquoted identifiers folded to upper case, quoted ones verbatim
     */
    default String getText(final FrostlakeParser.IdentifierContext ctx) {
        return SqlIdentifiers.canonical(ctx);
    }

    default String getText(final FrostlakeParser.QualifiedNameContext ctx) {
        if (ctx == null) return null;
        return ParseTreeText.getQualifiedName(ctx);
    }

    /**
     * The identifier parts of a qualified name (db, schema, name), read from the parse tree instead of
     * by splitting its flattened text on '.' — correct even for a quoted identifier containing a dot.
     *
     * @param ctx the qualified-name parse tree
     * @return the canonical name parts in source order, one element per dotted level
     */
    default String[] qualifiedNameParts(final FrostlakeParser.QualifiedNameContext ctx) {
        return ParseTreeText.qualifiedNameParts(ctx);
    }

    /**
     * Extract comment from comment clause
     *
     * @param ctx the {@code COMMENT [=] '<text>'} clause, or null when the statement carries none
     * @return the comment text with its single-quote or dollar-quote delimiters stripped, or null
     *         when the clause is absent
     */
    default String extractComment(final FrostlakeParser.CommentClauseContext ctx) {
        if (ctx == null) return null;
        if (ctx.DOLLAR_QUOTED_STRING() != null) {
            final String raw = ctx.DOLLAR_QUOTED_STRING().getText();
            return raw.substring(2, raw.length() - 2);
        }
        if (ctx.STRING_LITERAL() == null) return null;
        String comment = ctx.STRING_LITERAL().getText();
        // Remove quotes
        if (comment.startsWith("'") && comment.endsWith("'")) {
            comment = comment.substring(1, comment.length() - 1);
        }
        return comment;
    }

    /**
     * The COLUMN-level flavour of {@link #extractComment(FrostlakeParser.CommentClauseContext)} —
     * {@code COMMENT '<text>'} with no equals sign, the only spelling live accepts on a column.
     *
     * @param ctx the column's comment clause, or null when the column carries none
     * @return the comment text — a quoted literal decoded as every string literal is, so {@code 'it''s'}
     *         is {@code it's}; a dollar-quoted one verbatim — or null when the clause is absent
     */
    default String extractComment(final FrostlakeParser.ColumnCommentClauseContext ctx) {
        if (ctx == null) return null;
        if (ctx.DOLLAR_QUOTED_STRING() != null) {
            final String raw = ctx.DOLLAR_QUOTED_STRING().getText();
            return raw.substring(2, raw.length() - 2);
        }
        if (ctx.STRING_LITERAL() == null) return null;
        return ParseTreeText.extractStringLiteral(ctx.STRING_LITERAL());
    }

    /**
     * Get catalog instance
     *
     * @return the engine's metastore catalog, holding the objects this handler reads and mutates
     */
    Catalog getCatalog();

    /**
     * Get query executor instance
     *
     * @return the engine's query executor, for handlers that run nested queries or need session state
     */
    QueryExecutor getQueryExecutor();
}
