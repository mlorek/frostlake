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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.AppObjectVersion;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SHOW NOTEBOOKS, SHOW STREAMLITS, DESCRIBE NOTEBOOK and DESCRIBE STREAMLIT. A listing answers every object in
 * its scope; the SHOW pipeline then applies LIKE, the name order, STARTS WITH and LIMIT … FROM.
 */
public class AppObjectListing {

    private static final List<String> SHOW_NOTEBOOK_COLUMNS = Arrays.asList(
        "created_on", "name", "database_name", "schema_name", "comment", "owner", "query_warehouse", "url_id",
        "owner_role_type", "code_warehouse");
    private static final List<String> SHOW_STREAMLIT_COLUMNS = Arrays.asList(
        "created_on", "name", "database_name", "schema_name", "title", "comment", "owner", "query_warehouse",
        "url_id", "owner_role_type", "idle_auto_shutdown_time_seconds", "scheduled_tasks", "artifact_repositories");
    /** The warehouse a notebook's kernel runs on when CREATE names none. */
    private static final String DEFAULT_NOTEBOOK_WAREHOUSE = "SYSTEM$STREAMLIT_NOTEBOOK_WH";
    /** A warehouse-runtime notebook's packages, runtime environment version and default version. */
    private static final String NOTEBOOK_PACKAGES = "python==3.10.*,streamlit==1.39.1,snowbooks==1.72.0";
    private static final String NOTEBOOK_RUNTIME_ENVIRONMENT = "WH-RUNTIME-2.0";
    private static final String DEFAULT_VERSION = "LAST";
    /** The entry-point file of an object made from the template, which CREATE names no MAIN_FILE for. */
    private static final String TEMPLATE_NOTEBOOK_FILE = "notebook_app.ipynb";
    private static final String TEMPLATE_STREAMLIT_FILE = "streamlit_app.py";
    /** A notebook's idle shutdown time when CREATE sets none. */
    private static final long DEFAULT_IDLE_SECONDS = 1800L;
    /** A Streamlit app's runtime, compute pool and packages when CREATE names no runtime. */
    private static final String STREAMLIT_RUNTIME = "SYSTEM$ST_CONTAINER_RUNTIME_PY3_11";
    private static final String STREAMLIT_COMPUTE_POOL = "SYSTEM_COMPUTE_POOL_CPU";
    private static final String STREAMLIT_PACKAGES = "python==3.11.*,snowflake-snowpark-python,streamlit";
    private static final List<String> VERSION_LISTING_COLUMNS = Arrays.asList(
        "created_on", "name", "alias", "location_uri", "is_default", "is_live", "is_first", "is_last", "comment",
        "source_location_uri", "git_commit_hash");
    private static final List<String> VERSION_COLUMNS = Arrays.asList(
        "default_version", "default_version_name", "default_version_alias", "default_version_location_uri",
        "default_version_source_location_uri", "default_version_git_commit_hash", "last_version_name",
        "last_version_alias", "last_version_location_uri", "last_version_source_location_uri",
        "last_version_git_commit_hash", "live_version_location_uri");

    private final Catalog catalog;

    /** @param catalog the catalog to list from */
    public AppObjectListing(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * SHOW NOTEBOOKS / SHOW STREAMLITS over the statement's scope: the account, a database, or a schema — the
     * current schema when no IN clause is given, widening to the current database or the account when the
     * session has none.
     *
     * @param kind which objects to list
     * @param ctx the SHOW statement
     * @param scopeName the name its IN clause gives, or null
     */
    ResultSet show(final AppObjectKind kind, final ShowListingScope scope) {
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : schemasOf(scope)) {
            for (final AppObject object : schema.getAppObjects().all(kind)) {
                rows.add(new Row(kind == AppObjectKind.NOTEBOOK ? notebookRow(object) : streamlitRow(object)));
            }
        }
        return new ResultSet(textColumns(kind == AppObjectKind.NOTEBOOK ? SHOW_NOTEBOOK_COLUMNS
            : SHOW_STREAMLIT_COLUMNS), rows);
    }

    private List<Schema> schemasOf(final ShowListingScope scope) {
        final List<Schema> schemas = new ArrayList<>();
        if (scope.isAccount()) {
            for (final Database database : catalog.getAllDatabases()) {
                schemas.addAll(database.getAllSchemas());
            }
        } else if (scope.isDatabase()) {
            schemas.addAll(catalog.getDatabase(scope.name()).getAllSchemas());
        } else if (scope.name() != null) {
            schemas.add(catalog.resolveSchema(scope.name()));
        } else {
            schemas.add(catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema()));
        }
        return schemas;
    }

    private static List<Object> notebookRow(final AppObject object) {
        return Arrays.asList(
            ShowResultHelpers.createdOn(object.getCreatedOn()),
            object.getName(),
            object.getDatabaseName(),
            object.getSchemaName(),
            object.getComment(),
            object.getOwner(),
            object.getQueryWarehouse(),
            object.getUrlId(),
            ShowResultHelpers.ownerRoleType(object.getOwner()),
            codeWarehouse(object));
    }

    private static List<Object> streamlitRow(final AppObject object) {
        return Arrays.asList(
            ShowResultHelpers.createdOn(object.getCreatedOn()),
            object.getName(),
            object.getDatabaseName(),
            object.getSchemaName(),
            object.getTitle(),
            object.getComment(),
            object.getOwner(),
            object.getQueryWarehouse(),
            object.getUrlId(),
            ShowResultHelpers.ownerRoleType(object.getOwner()),
            object.getIdleAutoShutdownTimeSeconds(),
            null,
            "[]");
    }

    /**
     * DESCRIBE NOTEBOOK / DESCRIBE STREAMLIT: one row of the object's properties and versions. A Streamlit app
     * created with the legacy ROOT_LOCATION answers the legacy shape, which has no version columns.
     */
    public ResultSet describe(final AppObjectKind kind, final FrostlakeParser.QualifiedNameContext nameCtx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(nameCtx);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final AppObject object = schema.getAppObjects().get(kind, name);
        if (object == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind.displayName(),
                schema.qualifiedName(name)));
        }
        final List<String> names = new ArrayList<>();
        final List<Object> values = new ArrayList<>();
        if (kind == AppObjectKind.STREAMLIT && object.getRootLocation() != null) {
            add(names, values, "name", object.getName());
            add(names, values, "title", object.getTitle());
            add(names, values, "root_location", object.getRootLocation());
            add(names, values, "main_file", object.getMainFile());
            add(names, values, "query_warehouse", object.getQueryWarehouse());
            add(names, values, "url_id", object.getUrlId());
            addPackagesAndAccess(names, values, object);
            return oneRow(names, values);
        }
        add(names, values, "title", object.getTitle());
        add(names, values, "main_file", object.getMainFile() != null ? object.getMainFile()
            : kind == AppObjectKind.NOTEBOOK ? TEMPLATE_NOTEBOOK_FILE : TEMPLATE_STREAMLIT_FILE);
        add(names, values, "query_warehouse", object.getQueryWarehouse());
        add(names, values, "url_id", object.getUrlId());
        if (kind == AppObjectKind.NOTEBOOK) {
            add(names, values, "default_packages", NOTEBOOK_PACKAGES);
            add(names, values, "user_packages", "");
            add(names, values, "runtime_name", object.getRuntimeName());
            add(names, values, "compute_pool", object.getComputePool());
            add(names, values, "owner", object.getOwner());
            addAccess(names, values, object);
            add(names, values, "code_warehouse", codeWarehouse(object));
            add(names, values, "idle_auto_shutdown_time_seconds", object.getIdleAutoShutdownTimeSeconds() != null
                ? object.getIdleAutoShutdownTimeSeconds() : Long.valueOf(DEFAULT_IDLE_SECONDS));
            add(names, values, "runtime_environment_version", NOTEBOOK_RUNTIME_ENVIRONMENT);
        } else {
            final boolean defaultRuntime = object.getRuntimeName() == null;
            add(names, values, "default_packages", STREAMLIT_PACKAGES);
            add(names, values, "user_packages", "");
            addAccess(names, values, object);
            add(names, values, "compute_pool", defaultRuntime && object.getComputePool() == null
                ? STREAMLIT_COMPUTE_POOL : object.getComputePool());
            add(names, values, "runtime_name", defaultRuntime ? STREAMLIT_RUNTIME : object.getRuntimeName());
        }
        add(names, values, "name", object.getName());
        add(names, values, "comment", object.getComment());
        // The default version follows the last one, so both sets of columns describe the last version.
        final AppObjectVersion last = object.lastVersion();
        final String lastVersion = last == null ? null : last.getName();
        final String alias = last == null ? null : last.getAlias();
        final String source = last == null ? null : last.getSourceLocation();
        final String location = last == null ? null : object.versionLocation(lastVersion);
        final List<Object> versions = Arrays.asList(
            last == null ? null : DEFAULT_VERSION,
            lastVersion,
            alias,
            location,
            source,
            null,
            lastVersion,
            alias,
            location,
            source,
            null,
            object.hasLiveVersion() ? object.versionLocation("live") : null);
        for (int i = 0; i < VERSION_COLUMNS.size(); i++) {
            add(names, values, VERSION_COLUMNS.get(i), versions.get(i));
        }
        return oneRow(names, values);
    }

    /**
     * SHOW VERSIONS IN NOTEBOOK | STREAMLIT [LIMIT n]: the live version first, when one is open, then the committed
     * versions from the last to the first, as many as the LIMIT lets through. The default version is the last one. A
     * legacy ROOT_LOCATION app has no versions to list. Any other SHOW VERSIONS is an unsupported feature, named with
     * the kind of scope it was written with: {@code Unsupported feature 'SHOW VERSIONS IN SCHEMA'.}, or
     * {@code 'SHOW VERSIONS'} with none or with a bare name.
     *
     * @param ctx the SHOW statement
     * @return the listing
     */
    public ResultSet showVersions(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.qualifiedName() == null || ctx.NOTEBOOK() == null && ctx.STREAMLIT() == null) {
            final String scope = ctx.ACCOUNT() != null ? " IN ACCOUNT" : ctx.DATABASE() != null ? " IN DATABASE"
                : ctx.SCHEMA() != null ? " IN SCHEMA" : ctx.TABLE() != null ? " IN TABLE" : "";
            throw new RuntimeException("Unsupported feature 'SHOW VERSIONS" + scope + "'.");
        }
        ShowTailSyntax.requireCountOnly(ctx);
        int limit = Integer.MAX_VALUE;
        if (ctx.showTail() != null && ctx.showTail().LIMIT() != null) {
            final BigInteger written = new BigInteger(ctx.showTail().INTEGER_LITERAL().getText());
            if (written.signum() == 0) {
                throw new RuntimeException("page size \"0\" must be greater than 0 in limit clause");
            }
            limit = written.bitLength() < Integer.SIZE ? written.intValue() : Integer.MAX_VALUE;
        }
        final AppObjectKind kind = ctx.NOTEBOOK() != null ? AppObjectKind.NOTEBOOK : AppObjectKind.STREAMLIT;
        final String[] parts = ParseTreeText.qualifiedNameParts(ctx.qualifiedName());
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final AppObject object = schema.getAppObjects().get(kind, name);
        if (object == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(kind.displayName(),
                schema.qualifiedName(name)));
        }
        if (object.getRootLocation() != null) {
            throw new RuntimeException("Attached stage not exists.");
        }
        final List<Row> rows = new ArrayList<>();
        if (object.hasLiveVersion()) {
            rows.add(versionRow(object, object.getLiveVersion(), "live", false, true, false, false));
        }
        final List<AppObjectVersion> committed = object.getVersions();
        for (int i = committed.size() - 1; i >= 0; i--) {
            final AppObjectVersion version = committed.get(i);
            final boolean last = i == committed.size() - 1;
            rows.add(versionRow(object, version, version.getName(), last, false, i == 0, last));
        }
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String column : VERSION_LISTING_COLUMNS) {
            columns.add(new ResultSetColumn(column, "created_on".equals(column) ? ShowResultHelpers.CREATED_ON
                : StringType.VARCHAR));
        }
        return new ResultSet(columns, rows.size() > limit ? new ArrayList<Row>(rows.subList(0, limit)) : rows);
    }

    private static Row versionRow(final AppObject object, final AppObjectVersion version, final String directory,
                                  final boolean isDefault, final boolean isLive, final boolean isFirst,
                                  final boolean isLast) {
        return new Row(Arrays.<Object>asList(
            ShowResultHelpers.createdOn(version.getCreatedOn()),
            version.getName(),
            version.getAlias(),
            object.versionLocation(directory),
            String.valueOf(isDefault),
            String.valueOf(isLive),
            String.valueOf(isFirst),
            String.valueOf(isLast),
            version.getComment(),
            version.getSourceLocation(),
            null));
    }

    /** The warehouse a notebook's kernel runs on: the one CREATE named, or the account's default. */
    private static String codeWarehouse(final AppObject object) {
        return object.getWarehouse() != null ? object.getWarehouse() : DEFAULT_NOTEBOOK_WAREHOUSE;
    }

    private static void addPackagesAndAccess(final List<String> names, final List<Object> values,
                                             final AppObject object) {
        add(names, values, "default_packages", null);
        add(names, values, "user_packages", null);
        addAccess(names, values, object);
    }

    private static void addAccess(final List<String> names, final List<Object> values, final AppObject object) {
        add(names, values, "import_urls", jsonList(object.getImports()));
        add(names, values, "external_access_integrations", jsonList(object.getExternalAccessIntegrations()));
        add(names, values, "external_access_secrets", "{}");
    }

    private static void add(final List<String> names, final List<Object> values, final String name,
                            final Object value) {
        names.add(name);
        values.add(value);
    }

    private static ResultSet oneRow(final List<String> names, final List<Object> values) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if ("idle_auto_shutdown_time_seconds".equals(names.get(i))) {
                columns.add(new ResultSetColumn(names.get(i), NumericType.BIGINT));
            } else {
                columns.add(new ResultSetColumn(names.get(i), StringType.VARCHAR));
            }
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows);
    }

    private static List<ResultSetColumn> textColumns(final List<String> names) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : names) {
            if ("created_on".equals(name)) {
                columns.add(new ResultSetColumn(name, ShowResultHelpers.CREATED_ON));
            } else if ("idle_auto_shutdown_time_seconds".equals(name)) {
                columns.add(new ResultSetColumn(name, NumericType.BIGINT));
            } else {
                columns.add(new ResultSetColumn(name, StringType.VARCHAR));
            }
        }
        return columns;
    }

    /** A list as the JSON array text a DESCRIBE cell carries: {@code ["a","b"]}, or {@code []}. */
    private static String jsonList(final List<String> items) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append('"').append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return text.append(']').toString();
    }
}
