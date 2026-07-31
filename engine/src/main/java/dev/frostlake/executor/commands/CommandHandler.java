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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * Base interface for SQL command handlers
 */
public interface CommandHandler {

    /**
     * Get text from identifier or qualified name context
     */
    default String getText(final FrostlakeParser.IdentifierContext ctx) {
        return SqlIdentifiers.canonical(ctx);
    }

    default String getText(final FrostlakeParser.QualifiedNameContext ctx) {
        if (ctx == null) return null;
        List<FrostlakeParser.IdentifierContext> identifiers = ctx.identifier();
        if (identifiers.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < identifiers.size(); i++) {
            if (i > 0) sb.append(".");
            sb.append(getText(identifiers.get(i)));
        }
        if (ctx.TABLE() != null) {
            sb.append(sb.length() > 0 ? ".TABLE" : "TABLE");   // db.table — trailing part named "table"
        }
        return sb.toString();
    }

    /** The identifier parts of a qualified name (db, schema, name), read from the parse tree instead of
     *  by splitting its flattened text on '.' — correct even for a quoted identifier containing a dot. */
    default String[] qualifiedNameParts(final FrostlakeParser.QualifiedNameContext ctx) {
        final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
        final boolean trailingTable = ctx.TABLE() != null;   // db.table — a part literally named "table"
        final String[] parts = new String[ids.size() + (trailingTable ? 1 : 0)];
        for (int i = 0; i < ids.size(); i++) {
            parts[i] = getText(ids.get(i));
        }
        if (trailingTable) {
            parts[parts.length - 1] = "TABLE";
        }
        return parts;
    }

    /**
     * Extract comment from comment clause
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
     * Get catalog instance
     */
    Catalog getCatalog();

    /**
     * Get query executor instance
     */
    QueryExecutor getQueryExecutor();
}
