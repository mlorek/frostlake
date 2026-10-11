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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.StreamSources;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableSnapshot;
import dev.frostlake.storage.TableStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * SYSTEM$LAST_CHANGE_COMMIT_TIME and SYSTEM$STREAM_GET_TABLE_TIMESTAMP: what a table's change history says, in the
 * nanoseconds since the epoch both report. Both answered a fresh clock reading, which moved on every call and named
 * no table at all, and both accepted a name nothing answers to.
 */
final class ChangeTimes {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private ChangeTimes() {
    }

    /**
     * SYSTEM$LAST_CHANGE_COMMIT_TIME('&lt;table or view&gt;'): a token that moves only when the table's rows do, and
     * for a view only when the rows of a table it reads do (documented: "If the specified database object is a view,
     * then at least one of the database objects referenced by the view changed"). A view's token is the newest of
     * its tables', and never earlier than the view itself. The name resolves as a written one would, against the
     * session's database and schema, and a name that is no table or view refuses the call. The refusal's wording is
     * the catalog's own; the documentation gives none.
     *
     * @param catalog  the catalog
     * @param argument the call's argument, the object's name as written
     * @return the token, or null for a NULL argument
     */
    static Long lastChangeCommitTime(final Catalog catalog, final Object argument) {
        if (argument == null) {
            return null;
        }
        final String[] name = fullName(catalog, written(argument));
        final Long token = relationToken(catalog, name, new HashSet<String>());
        if (token == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Object", QualifiedName.join(name)));
        }
        return token;
    }

    /**
     * SYSTEM$STREAM_GET_TABLE_TIMESTAMP('&lt;stream&gt;'): the stamp, in nanoseconds, of the latest version of the
     * stream's table at or before the stream's offset (documented). Consuming the stream moves the offset, and so the
     * answer, and nothing else does. A stream on a view is refused (documented), in words of frostlake's own. An
     * unknown stream is refused as SYSTEM$STREAM_HAS_DATA refuses one, a shape not verified for this function.
     *
     * @param catalog       the catalog
     * @param storageEngine the storage engine holding the table's versions
     * @param argument      the call's argument, the stream's name as written
     * @return the stamp, or null for a NULL argument
     */
    static Long streamTableTimestamp(final Catalog catalog, final StorageEngine storageEngine,
                                     final Object argument) {
        if (argument == null) {
            return null;
        }
        final String written = written(argument);
        Stream stream = null;
        try {
            stream = catalog.resolveStream(SqlIdentifiers.canonicalText(written.trim()));
        } catch (final RuntimeException unresolvable) {
            // refused just below, as an unknown stream
        }
        if (stream == null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid value ['" + written
                + "'] for function 'SYSTEM$STREAM_GET_TABLE_TIMESTAMP', parameter 1: must be a valid stream name"));
        }
        if (stream.getSourceType() == StreamSourceType.VIEW) {
            throw new RuntimeException(SqlCompilationError.of("SYSTEM$STREAM_GET_TABLE_TIMESTAMP does not support"
                + " stream '" + stream.getName() + "' on a view."));
        }
        final Schema home = StreamSources.homeOf(catalog, stream);
        final Table table = home != null ? StreamSources.sourceOf(catalog, home, stream) : null;
        final Schema holder = table != null ? StreamSources.holderOf(catalog, table) : null;
        if (holder == null) {
            // The words a read of such a stream is refused in.
            throw new RuntimeException(SqlCompilationError.of("Base table '" + StreamSources.fullName(home, stream)
                + "' dropped, cannot read from stream '" + stream.getName() + "'."));
        }
        final TableStorage storage = storageEngine.getTableStorage(
            QualifiedName.key(holder.getDatabaseName(), holder.getName(), table.getName()));
        long stamp = storage.getCreatedMillis();
        for (final TableSnapshot version : storage.getSnapshots()) {
            if (version.sequence <= stream.getOffsetSequence()) {
                stamp = Math.max(stamp, version.epochMillis);
            }
        }
        return stamp * NANOS_PER_MILLI;
    }

    /** The argument's text, its surrounding quotes dropped when a caller left them on. */
    private static String written(final Object argument) {
        return argument == null ? "" : argument.toString().replaceAll("^'|'$", "");
    }

    /** A written name in full, completed from the session's database and schema. */
    private static String[] fullName(final Catalog catalog, final String written) {
        final String[] parts = SqlIdentifiers.canonicalTextParts(written.trim());
        if (parts.length >= 3) {
            return parts;
        }
        final String database = catalog.getCurrentDatabase();
        if (parts.length == 2) {
            return new String[] {database, parts[0], parts[1]};
        }
        return new String[] {database, catalog.getCurrentSchema(), parts.length == 1 ? parts[0] : ""};
    }

    /** A name in full read where a view lives, as the names in a view's body are. */
    private static String[] fullName(final Schema home, final String[] parts) {
        if (parts.length >= 3) {
            return parts;
        }
        if (parts.length == 2) {
            return new String[] {home.getDatabaseName(), parts[0], parts[1]};
        }
        return new String[] {home.getDatabaseName(), home.getName(), parts[0]};
    }

    /**
     * The token of the relation a full name names, or null when it names none. A view, a materialized view or a
     * dynamic table answers through the tables its query reads; {@code seen} keeps a view that reads itself through
     * others from being read twice.
     */
    private static Long relationToken(final Catalog catalog, final String[] name, final Set<String> seen) {
        if (name.length != 3 || name[0] == null || name[1] == null) {
            return null;
        }
        final Database database = catalog.findDatabase(name[0]);
        if (database == null || !database.hasSchema(name[1])) {
            return null;
        }
        final Schema schema = database.getSchema(name[1]);
        if (schema.hasTable(name[2])) {
            return schema.getTable(name[2]).getChangeToken();
        }
        if (!seen.add(QualifiedName.join(name).toUpperCase(Locale.ROOT))) {
            return 0L;
        }
        if (schema.hasView(name[2])) {
            final View view = schema.getView(name[2]);
            return Math.max(view.getCreatedTime().toEpochMilli() * NANOS_PER_MILLI,
                queryToken(catalog, schema, view.getDefinition(), seen));
        }
        if (schema.hasMaterializedView(name[2])) {
            final MaterializedView materialized = schema.getMaterializedView(name[2]);
            return Math.max(materialized.getCreatedTime().toEpochMilli() * NANOS_PER_MILLI,
                queryToken(catalog, schema, materialized.getDefinition(), seen));
        }
        if (schema.hasDynamicTable(name[2])) {
            final DynamicTable dynamic = schema.getDynamicTable(name[2]);
            return Math.max(dynamic.getCreatedTime().toEpochMilli() * NANOS_PER_MILLI,
                queryToken(catalog, schema, dynamic.getQuery(), seen));
        }
        return null;
    }

    /** The newest token of the relations a query reads, its own CTEs aside; 0 for a query that reads none. */
    private static long queryToken(final Catalog catalog, final Schema home, final String query,
                                   final Set<String> seen) {
        final FrostlakeParser.SelectStatementContext statement = parsed(query);
        if (statement == null) {
            return 0L;
        }
        final Set<String> cteNames = new HashSet<>();
        QueryExecutor.collectCteNames(statement, cteNames);
        final List<FrostlakeParser.TableQualifiedNameContext> names = new ArrayList<>();
        collectRelations(statement, names);
        long token = 0L;
        for (final FrostlakeParser.TableQualifiedNameContext written : names) {
            final String[] parts = ParseTreeText.qualifiedNameParts(written);
            if (parts.length == 1 && cteNames.contains(parts[0].toUpperCase(Locale.ROOT))) {
                continue;
            }
            final Long read = relationToken(catalog, fullName(home, parts), seen);
            if (read != null) {
                token = Math.max(token, read);
            }
        }
        return token;
    }

    /** Every relation a FROM names, in a query and its subqueries. */
    private static void collectRelations(final ParseTree node, final List<FrostlakeParser.TableQualifiedNameContext> into) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            into.add(((FrostlakeParser.TableSourceContext) node).tableQualifiedName());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRelations(node.getChild(i), into);
        }
    }

    /** A query's text as a SELECT statement, or null when it does not parse as one. */
    private static FrostlakeParser.SelectStatementContext parsed(final String query) {
        if (query == null) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(query));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            return parser.selectStatement();
        } catch (final ParseCancellationException unparsable) {
            return null;
        }
    }
}
