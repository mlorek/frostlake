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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for Query commands:
 * SELECT statements
 */
public class QueryCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(QueryCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public QueryCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public ResultSet handleQueryStatement(final FrostlakeParser.QueryStatementContext ctx) {
        if (ctx.selectStatement() != null) {
            return handleSelectStatement(ctx.selectStatement());
        }
        throw new RuntimeException("Unsupported query statement");
    }

    public ResultSet handleSelectStatement(final FrostlakeParser.SelectStatementContext ctx) {
        // Delegate to QueryExecutor which has the full SELECT logic
        return queryExecutor.executeSelectFromContext(ctx);
    }
}
