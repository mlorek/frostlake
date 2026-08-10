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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for DML (Data Manipulation Language) commands:
 * INSERT, UPDATE, DELETE, MERGE
 */
public class DMLCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(DMLCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public DMLCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
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

    public Object handleInsertStatement(final FrostlakeParser.InsertStatementContext ctx) {
        // Delegate to QueryExecutor which has the full INSERT logic
        return queryExecutor.executeInsertFromContext(ctx);
    }

    public Object handleMultiTableInsert(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        // Snowflake INSERT ALL / INSERT FIRST — routes subquery rows to multiple targets.
        return queryExecutor.executeMultiTableInsert(ctx);
    }

    public Object handleUpdateStatement(final FrostlakeParser.UpdateStatementContext ctx) {
        // Delegate to QueryExecutor which has the full UPDATE logic
        return queryExecutor.executeUpdateFromContext(ctx);
    }

    public Object handleDeleteStatement(final FrostlakeParser.DeleteStatementContext ctx) {
        // Delegate to QueryExecutor which has the full DELETE logic
        return queryExecutor.executeDeleteFromContext(ctx);
    }

    public Object handleMergeStatement(final FrostlakeParser.MergeStatementContext ctx) {
        // Delegate to QueryExecutor which has the full MERGE logic
        return queryExecutor.executeMergeFromContext(ctx);
    }

    public Object handleCopyIntoStatement(final FrostlakeParser.CopyIntoStatementContext ctx) {
        // Delegate to QueryExecutor which has the full COPY INTO logic
        return queryExecutor.executeCopyIntoFromContext(ctx);
    }
}
