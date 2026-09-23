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

import dev.frostlake.metastore.SqlObject;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * A view's CREATE statement as the account's metadata spells it around the view's own COMMENT clause
 * (live-verified): INFORMATION_SCHEMA.VIEWS' VIEW_DEFINITION leaves the clause out, and SHOW VIEWS'
 * {@code text} re-prints it as {@code comment = '…' } — in both, one space written right after the clause
 * goes with it, while a line break stays:
 *
 * <pre>
 *   CREATE VIEW UV COMMENT = 'uv' AS SELECT 1 AS x
 *     VIEW_DEFINITION   CREATE VIEW UV AS SELECT 1 AS x
 *     SHOW VIEWS text   CREATE VIEW UV comment = 'uv' AS SELECT 1 AS x
 * </pre>
 *
 * <p>The clause is found in the parse tree: the pre-AS {@code COMMENT = '…'} the generic view property
 * list takes, or the dedicated comment clause.
 */
final class ViewCommentText {

    private ViewCommentText() {
    }

    /**
     * The statement's text without the view's COMMENT clause, then with it re-printed — or null when the
     * statement carries no COMMENT clause whose place is known.
     *
     * @param ctx the CREATE VIEW statement
     * @param written the statement's text as written, from its first token
     * @param comment the comment the clause sets
     * @return the two texts, trailing semicolon dropped
     */
    static String[] texts(final FrostlakeParser.CreateStatementContext ctx, final String written, final String comment) {
        final ParserRuleContext clause = clauseOf(ctx);
        if (clause == null || clause.getStop() == null) {
            return null;
        }
        final int base = ctx.getStart().getStartIndex();
        final int start = clause.getStart().getStartIndex() - base;
        int stop = clause.getStop().getStopIndex() - base + 1;
        if (start < 0 || stop > written.length() || start >= stop) {
            return null;
        }
        if (stop < written.length() && written.charAt(stop) == ' ') {
            stop++;
        }
        final String before = written.substring(0, start);
        final String after = written.substring(stop);
        return new String[] {
            SqlObject.withoutTrailingSemicolon(before + after),
            SqlObject.withoutTrailingSemicolon(before + "comment = '" + comment.replace("'", "''") + "' " + after)
        };
    }

    private static ParserRuleContext clauseOf(final FrostlakeParser.CreateStatementContext ctx) {
        for (final FrostlakeParser.ViewPropertyContext property : ctx.viewProperty()) {
            if ("COMMENT".equalsIgnoreCase(property.optionKey().getText())
                    && property.copyOptionValue().STRING_LITERAL() != null) {
                return property;
            }
        }
        return ctx.commentClause().isEmpty() ? null : ctx.commentClause().get(0);
    }
}
