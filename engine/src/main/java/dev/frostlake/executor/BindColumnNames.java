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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The names a RESULTSET's query gives its unaliased columns over a block's binds: the text the block wrote, each
 * bind as written, where the query that ran held the value written in the bind's place (live-verified):
 * {@code res := (SELECT :s, UPPER(:s))} names its columns {@code :S} and {@code UPPER(:S)}.
 */
final class BindColumnNames {

    private BindColumnNames() {
    }

    /**
     * @param ran      the rows the query answered
     * @param written  the query as the block wrote it
     * @param bound    the query that ran, each bind written out
     * @param executor the executor that reads the two texts
     * @return the rows under the written names
     */
    static ResultSet asWritten(final ResultSet ran, final String written, final String bound,
                               final QueryExecutor executor) {
        if (ran == null || written == null || written.equals(bound)) {
            return ran;
        }
        final List<FrostlakeParser.SelectItemContext> writtenItems = items(executor, written);
        final List<FrostlakeParser.SelectItemContext> boundItems = items(executor, bound);
        final List<ResultSetColumn> columns = ran.getColumns();
        if (writtenItems == null || boundItems == null || writtenItems.size() != boundItems.size()
                || columns == null || columns.size() != boundItems.size()) {
            return ran;
        }
        final List<ResultSetColumn> renamed = new ArrayList<>(columns.size());
        boolean changed = false;
        for (int i = 0; i < columns.size(); i++) {
            final ResultSetColumn column = columns.get(i);
            final String boundName = unaliasedText(boundItems.get(i));
            final String writtenName = unaliasedText(writtenItems.get(i));
            if (boundName != null && writtenName != null && !boundName.equals(writtenName)
                    && boundName.toUpperCase(Locale.ROOT).equals(column.getName())) {
                renamed.add(column.withName(writtenName.toUpperCase(Locale.ROOT)));
                changed = true;
            } else {
                renamed.add(column);
            }
        }
        return changed ? new ResultSet(renamed, ran.getRows()) : ran;
    }

    /** A query's first select's items, or null where the text is no single select. */
    private static List<FrostlakeParser.SelectItemContext> items(final QueryExecutor executor, final String text) {
        final FrostlakeParser.SelectStatementContext statement;
        try {
            statement = executor.subqueryStatement(text);
        } catch (final RuntimeException unreadable) {
            return null;
        }
        if (statement == null || statement.selectOperand().size() != 1
                || statement.selectOperand(0).selectClause() == null) {
            return null;
        }
        return statement.selectOperand(0).selectClause().selectList().selectItem();
    }

    /** An unaliased expression item's text, or null for any other item. */
    private static String unaliasedText(final FrostlakeParser.SelectItemContext item) {
        if (!(item instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final FrostlakeParser.ExprItemContext expression = (FrostlakeParser.ExprItemContext) item;
        return expression.aliasName() != null || expression.identifier() != null || expression.bareAliasWord() != null
            ? null : ParseTreeText.getOriginalText(expression.booleanExpr());
    }
}
