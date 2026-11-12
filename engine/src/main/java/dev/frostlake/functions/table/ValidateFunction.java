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

package dev.frostlake.functions.table;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.CopyJob;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.security.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@code VALIDATE(table, JOB_ID => '<query_id>' | '_last')}: "Validates the files loaded in a past execution of the
 * {@code COPY INTO <table>} command and returns all the errors encountered during the load, rather than just the first
 * error"
 * (documented). The job is the COPY a query ID names, which must have loaded the table, or the last load the session
 * ran, whatever its table. The files are read again as {@code VALIDATION_MODE = RETURN_ALL_ERRORS} reads them, in its
 * twelve columns; one no longer staged is reported as missing. A COPY whose ON_ERROR aborted at its first error has
 * nothing to return, and one that transformed its data cannot be validated (documented). The refusals' wording is
 * the engine's own.
 */
public class ValidateFunction extends TableFunction {

    /** The function's name, whose leading argument names a table rather than holding a value. */
    public static final String NAME = "VALIDATE";

    /** The JOB_ID that stands for the session's last load. */
    private static final String LAST = "_LAST";

    private final Catalog catalog;
    private final SessionContext sessionContext;

    public ValidateFunction(final Catalog catalog, final SessionContext sessionContext) {
        super(NAME);
        this.catalog = catalog;
        this.sessionContext = sessionContext;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // The table and the job are judged when the call runs.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        return validate(namedArgs.get("TABLE_NAME"), namedArgs.get("JOB_ID"));
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        return validate(positionalArgs.isEmpty() ? null : positionalArgs.get(0),
            positionalArgs.size() < 2 ? null : positionalArgs.get(1));
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    private ResultSet validate(final Object tableName, final Object jobId) {
        if (tableName == null || jobId == null) {
            throw new RuntimeException(SqlCompilationError.of("VALIDATE needs a table and a JOB_ID."));
        }
        final String canonical = SqlIdentifiers.canonicalText(tableName.toString());
        final QualifiedName name = QualifiedName.parse(canonical);
        if (!catalog.hasTableAsWritten(name)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", canonical));
        }
        final Schema schema = catalog.resolveOwningSchema(canonical);
        final String written = jobId.toString().trim();
        final boolean last = LAST.equalsIgnoreCase(written);
        final CopyJob job = last ? catalog.getCopyJobs().lastOf(sessionContext.getSessionId())
            : catalog.getCopyJobs().byQueryId(written);
        if (job == null || !last && !job.loaded(schema.getDatabaseName(), schema.getName(), name.last())) {
            throw new RuntimeException(SqlCompilationError.of("Invalid JOB_ID '" + written
                + "': no load into table " + canonical + " to validate."));
        }
        if (job.isTransformed()) {
            throw new RuntimeException(SqlCompilationError.of(
                "VALIDATE does not support a COPY that transforms its data."));
        }
        if (job.isAbortsOnError()) {
            return new ResultSet(columns(), new ArrayList<Row>());
        }
        return new ResultSet(columns(), job.getErrors().errors().getRows());
    }

    /** VALIDATION_MODE = RETURN_ALL_ERRORS's twelve columns. */
    private static List<ResultSetColumn> columns() {
        return Arrays.asList(
            new ResultSetColumn("ERROR", StringType.VARCHAR),
            new ResultSetColumn("FILE", StringType.VARCHAR),
            new ResultSetColumn("LINE", NumericType.INTEGER),
            new ResultSetColumn("CHARACTER", NumericType.INTEGER),
            new ResultSetColumn("BYTE_OFFSET", NumericType.INTEGER),
            new ResultSetColumn("CATEGORY", StringType.VARCHAR),
            new ResultSetColumn("CODE", NumericType.INTEGER),
            new ResultSetColumn("SQL_STATE", StringType.VARCHAR),
            new ResultSetColumn("COLUMN_NAME", StringType.VARCHAR),
            new ResultSetColumn("ROW_NUMBER", NumericType.INTEGER),
            new ResultSetColumn("ROW_START_LINE", NumericType.INTEGER),
            new ResultSetColumn("REJECTED_RECORD", StringType.VARCHAR));
    }
}
