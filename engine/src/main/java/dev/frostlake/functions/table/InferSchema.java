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

import dev.frostlake.executor.copy.InferredColumn;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * INFER_SCHEMA — the column definitions of a set of staged files, one row per column: COLUMN_NAME, TYPE,
 * NULLABLE, EXPRESSION, FILENAMES and ORDER_ID (a NUMBER(4,0), counted from 0).
 *
 * <p>{@code LOCATION => '@stage[/path]'} names the files as a stage path does anywhere else: every file whose
 * stage-relative name starts with the path, at any depth, in path order; {@code FILES => ('a.csv', 'b.csv')}
 * picks files under that path instead, and a missing one refuses the call. {@code MAX_FILE_COUNT} reads only
 * the first files, {@code MAX_RECORDS_PER_FILE} only the first records of each CSV or JSON file, and
 * {@code IGNORE_CASE => TRUE} folds the names to upper case. {@code FILE_FORMAT} names a file format object:
 * CSV and JSON are read here, Parquet, Avro and ORC through the optional formats module. An empty stage, or a
 * path naming nothing, answers no rows. The argument rules are checked while the statement compiles (see the
 * executor's {@code InferSchemaArguments}), so this only ever sees a call that passed them.
 */
public class InferSchema extends TableFunction {

    private final StagedFileSchemas schemas;

    /**
     * INFER_SCHEMA over the executor's stages.
     *
     * @param schemas how the staged files are found and read
     */
    public InferSchema(final StagedFileSchemas schemas) {
        super("INFER_SCHEMA");
        this.schemas = schemas;
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final List<Row> rows = new ArrayList<Row>();
        for (final InferredColumn column : schemas.inferColumns(namedArgs)) {
            rows.add(new Row(Arrays.asList((Object) column.name(), column.type(),
                Boolean.valueOf(column.nullable()), column.expression(), column.fileNames(),
                Long.valueOf(column.orderId()))));
        }
        return new ResultSet(columns(), rows);
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // The rules are checked while the statement compiles; nothing is left once the values are in hand.
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    /** The six columns every INFER_SCHEMA answers, typed as the account declares them. */
    public static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        columns.add(text("COLUMN_NAME"));
        columns.add(text("TYPE"));
        columns.add(new ResultSetColumn("NULLABLE", BooleanType.BOOLEAN));
        columns.add(text("EXPRESSION"));
        columns.add(text("FILENAMES"));
        columns.add(new ResultSetColumn("ORDER_ID", NumericType.INTEGER, null, new NumericType("NUMBER", 4, 0)));
        return columns;
    }

    private static ResultSetColumn text(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR, null, new LengthlessStringType());
    }
}
