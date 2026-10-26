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

import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;

/**
 * One query a view stream's change rows pass through between a table and the stream: a view's own query, or a
 * subquery in a FROM clause. A view over a view, or over a FROM-clause subquery, stacks them, the query that reads
 * the table innermost.
 */
final class ViewStreamLevel {

    /** The query: the filter and the select list it applies. */
    final FrostlakeParser.SelectClauseContext clause;
    /** The view the query is the body of, or null for a FROM-clause subquery. */
    final View view;
    /** The view whose body holds the query: the view itself, or the one a subquery is written in. */
    final View holder;
    /**
     * The columns the statement the query is a branch of gives, below the view's own query: as its view resolved
     * them, or as its subquery resolves; null where unknown.
     */
    final List<TableColumn> shape;
    /** The name the query's FROM clause reads its relation by: the alias, else the relation's own name; or null. */
    final String sourceName;
    /** The column names the FROM clause's alias gives the relation, or null when it gives none. */
    final List<String> sourceColumns;

    ViewStreamLevel(final FrostlakeParser.SelectClauseContext clause, final View view, final View holder,
                    final List<TableColumn> shape, final String sourceName, final List<String> sourceColumns) {
        this.clause = clause;
        this.view = view;
        this.holder = holder;
        this.shape = shape;
        this.sourceName = sourceName;
        this.sourceColumns = sourceColumns;
    }
}
