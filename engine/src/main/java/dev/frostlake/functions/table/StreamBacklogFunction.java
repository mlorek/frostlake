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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.StreamVersion;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SYSTEM$STREAM_BACKLOG: the versions of the tables a stream reads that commits wrote past its offset, one row each,
 * the oldest first (live-verified): the table's name, when the version was committed, its DML mode, the rows it added
 * and removed, and the change records the account estimates it holds for the stream (see StreamVersion). A name that
 * resolves to no stream is refused.
 */
public class StreamBacklogFunction extends TableFunction {

    /** The function's name, which TABLE( … ) calls it by. */
    public static final String NAME = "SYSTEM$STREAM_BACKLOG";

    /** The parameter naming the stream. */
    public static final String STREAM_NAME = "STREAM_NAME";

    private static final StringType TEXT = new StringType("VARCHAR", 134217728);

    private final Catalog catalog;

    public StreamBacklogFunction(final Catalog catalog) {
        super(NAME);
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Judged where the call is read (see QueryExecutor), and the name where the stream is resolved.
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final Object written = namedArgs.get(STREAM_NAME);
        final String name = written == null ? "" : String.valueOf(written);
        final Stream stream = resolve(name);
        final boolean appendOnly = stream.getStreamType() == StreamType.APPEND_ONLY;
        final List<Row> rows = new ArrayList<>();
        for (final StreamVersion version : stream.getPendingVersions()) {
            rows.add(new Row(Arrays.<Object>asList(version.getBaseTable(),
                ShowResultHelpers.createdOn(version.getTime()), version.getDmlMode(),
                Long.valueOf(version.getRowsAdded()), Long.valueOf(version.getRowsRemoved()),
                Long.valueOf(version.estimatedRows(appendOnly)))));
        }
        return new ResultSet(columns(), rows);
    }

    /** The stream a name reads as an identifier does, quoted parts kept; refused where it names none. */
    private Stream resolve(final String name) {
        try {
            final String canonical = SqlIdentifiers.canonicalText(name);
            final Schema schema = catalog.resolveOwningSchema(canonical);
            final Stream stream = schema.getStreamExact(QualifiedName.parse(canonical).last());
            if (stream != null) {
                return stream;
            }
        } catch (final RuntimeException missing) {
            // Refused below, as a name that reaches no stream is.
        }
        throw new RuntimeException("Invalid STREAM '" + name.toUpperCase(Locale.ROOT) + "'. STREAM_BACKLOG expectes a"
            + " fully or partially qualified Stream name.");
    }

    private static List<ResultSetColumn> columns() {
        return DeclaredColumnTypes.typed(Arrays.asList(
            new ResultSetColumn("BASE_TABLE_NAME", TEXT),
            new ResultSetColumn("STREAM_OFFSET_TIME", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("DML_MODE", TEXT),
            new ResultSetColumn("ROWS_ADDED", NumericType.NUMBER),
            new ResultSetColumn("ROWS_REMOVED", NumericType.NUMBER),
            new ResultSetColumn("ESTIMATED_ROWS_TO_PROCESS", NumericType.NUMBER)));
    }
}
