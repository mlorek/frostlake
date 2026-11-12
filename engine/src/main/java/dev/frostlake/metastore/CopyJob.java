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

package dev.frostlake.metastore;

/**
 * One COPY INTO a table that ran to completion, as VALIDATE finds it again: the query that ran it, the session it ran
 * in, its target table, and how its files are validated again. A COPY whose ON_ERROR aborted on the first error has
 * no errors to give, and one that transformed its data cannot be validated (documented).
 */
public final class CopyJob {

    private final String queryId;
    private final String sessionId;
    private final String database;
    private final String schema;
    private final String table;
    private final boolean abortsOnError;
    private final boolean transformed;
    private final CopyJobErrors errors;

    /**
     * A completed COPY.
     *
     * @param queryId       the COPY's query ID
     * @param sessionId     the session that ran it
     * @param database      its table's database
     * @param schema        its table's schema
     * @param table         its table's name
     * @param abortsOnError whether its ON_ERROR was ABORT_STATEMENT
     * @param transformed   whether it loaded through a transformation
     * @param errors        how its files are validated again
     */
    public CopyJob(final String queryId, final String sessionId, final String database, final String schema,
                   final String table, final boolean abortsOnError, final boolean transformed,
                   final CopyJobErrors errors) {
        this.queryId = queryId;
        this.sessionId = sessionId;
        this.database = database;
        this.schema = schema;
        this.table = table;
        this.abortsOnError = abortsOnError;
        this.transformed = transformed;
        this.errors = errors;
    }

    public String getQueryId() {
        return queryId;
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * Whether the COPY loaded the named table.
     *
     * @param tableDatabase the table's database
     * @param tableSchema   the table's schema
     * @param tableName     the table's name
     * @return whether it is the COPY's target
     */
    public boolean loaded(final String tableDatabase, final String tableSchema, final String tableName) {
        return database.equals(tableDatabase) && schema.equals(tableSchema) && table.equals(tableName);
    }

    public boolean isAbortsOnError() {
        return abortsOnError;
    }

    public boolean isTransformed() {
        return transformed;
    }

    public CopyJobErrors getErrors() {
        return errors;
    }
}
