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

package dev.frostlake.rt.py;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.commands.FailureCode;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SnowparkSession {
    private final DatabaseEngine engine;
    private final boolean ownersRights;

    public SnowparkSession(final DatabaseEngine engine) {
        this(engine, false);
    }

    public SnowparkSession(final DatabaseEngine engine, final boolean ownersRights) {
        this.engine = engine;
        this.ownersRights = ownersRights;
    }

    public PythonResultSet sql(final String sqlText) {
        rejectTemporaryObjectUnderOwnersRights(sqlText);
        try {
            // General execute, not executeQuery: handler code runs DDL and DML (CREATE OR REPLACE
            // TABLE, TRUNCATE, MERGE, DELETE, ...) through session.sql exactly as it does queries.
            final ExecutionResult result = engine.execute(sqlText);
            if (!result.isSuccess()) {
                throw new RuntimeException(result.getErrorMessage());
            }
            final List<ResultSet> resultSets = result.getResultSets();
            final ResultSet rs = resultSets == null || resultSets.isEmpty()
                ? new ResultSet(new ArrayList<>(), new ArrayList<>())
                : resultSets.get(resultSets.size() - 1);
            return new PythonResultSet(rs);
        } catch (final Exception e) {
            throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
        }
    }

    /**
     * {@link #sql(String)}, its refusal answered rather than thrown (see {@link SnowparkOutcome}). A statement the
     * engine ran and refused reads as Snowpark's exception does (see {@link #sqlRefusal}); one refused before it ran
     * reads as the exception's own text.
     *
     * @param sqlText the statement
     * @return its result, or the engine's refusal
     */
    public SnowparkOutcome attempt(final String sqlText) {
        try {
            return SnowparkOutcome.of(sql(sqlText));
        } catch (final RuntimeException refused) {
            if (refused.getCause() == null) {
                return SnowparkOutcome.refusedWith(refused.toString());
            }
            final String queryId = engine.lastQueryId();
            return SnowparkOutcome.refusedWith(sqlRefusal(refused.getCause(), queryId), queryId);
        }
    }

    /**
     * The text Snowpark's SnowparkSQLException carries for a statement the engine ran and refused: Snowpark's code
     * for a failed query, 1304, the failed statement's query ID, then the refusal's code in six digits and its
     * SQLSTATE, as the connector writes a failure: {@code (1304): <id>: 090239 (25000): Modifying a transaction that
     * has started at a different scope is not allowed.} (live-verified).
     *
     * @param failure the engine's refusal
     * @param queryId the failed statement's query ID
     * @return the text
     */
    private static String sqlRefusal(final Throwable failure, final String queryId) {
        final FailureCode client = FailureCode.ofClient(failure);
        final FailureCode code = client != null ? client : FailureCode.of(failure);
        return "(1304): " + queryId + ": " + String.format(Locale.ROOT, "%06d", Integer.valueOf(code.code())) + " ("
            + code.state() + "): " + failure.getMessage();
    }

    /**
     * The owner's rights temporary-object refusal, in Snowflake's words — the same rule and the same
     * sentence the Java/Scala session applies, because live applies it to both.
     */
    private void rejectTemporaryObjectUnderOwnersRights(final String sqlText) {
        if (!ownersRights) {
            return;
        }
        final String kind = TemporaryObjectStatements.temporaryObjectKind(sqlText);
        if (kind != null) {
            throw new RuntimeException(
                "Stored procedure execution error: Unsupported statement type 'temporary " + kind + "'.");
        }
    }

    /**
     * Quiet existence probe for the shim's save_as_table paths: resolves through the catalog without
     * executing a statement, so a missing table does not produce an engine ERROR log entry the way a
     * failing probe query would.
     */
    public boolean tableExists(final String tableName) {
        try {
            // The shim hands over the name the handler wrote — save_as_table('shim_target') — which is
            // an identifier reference, not a resolved name. Catalog resolution matches exactly, so the
            // bare form has to be folded here or the probe answers "missing" for a table that exists
            // and the writer appends to a second one.
            return engine.getCatalog().resolveTable(SqlIdentifiers.canonicalText(tableName)) != null;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    public List<Row> table(final String tableName) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT * FROM " + tableName);
            return rs.getRows();
        } catch (final Exception e) {
            throw new RuntimeException("Error reading table: " + e.getMessage(), e);
        }
    }
}
