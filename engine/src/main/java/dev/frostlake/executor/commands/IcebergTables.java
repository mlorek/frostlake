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

import dev.frostlake.executor.AccountParameters;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StatementTokens;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ExternalVolume;
import dev.frostlake.metastore.model.IcebergTableMetadata;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Snowflake-managed Iceberg tables: the rows are an ordinary table's, and the Iceberg metadata — external volume,
 * catalog, base location, storage serialization policy, catalog sync — rides on the table
 * ({@link IcebergTableMetadata}). Read here: the metadata CREATE ICEBERG TABLE writes as table options, the
 * ALTER ICEBERG TABLE actions of its own, and the SHOW ICEBERG TABLES listing. Only the {@code SNOWFLAKE} catalog
 * is modelled: a table whose catalog is an integration would read its data and metadata from outside.
 */
final class IcebergTables {

    /** The partition specifications of a table written without a PARTITION BY, as the listing prints them. */
    private static final String PARTITION_SPECS = """
        [ {
          "spec-id" : 0,
          "fields" : [ ]
        } ]""";
    private static final List<String> POLICIES = Arrays.asList("COMPATIBLE", "OPTIMIZED");
    private static final String CHANGE_TRACKING_REFUSAL = "invalid value 'false' for property 'CHANGE_TRACKING',"
        + " Reason: Change Tracking cannot be turned off for Iceberg tables";
    /** The storage an Iceberg table written without an external volume is kept in. */
    private static final String DEFAULT_VOLUME = IcebergTableMetadata.MANAGED_VOLUME;
    private static final String SUFFIX_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private IcebergTables() {
    }

    /** The parameter a table without an EXTERNAL_VOLUME option takes its volume from. */
    private static final String VOLUME_PARAMETER = "EXTERNAL_VOLUME";
    /** The parameter a table without a CATALOG option takes its catalog from. */
    private static final String CATALOG_PARAMETER = "CATALOG";

    /**
     * The volume the nearest container sets — the schema, its database, then the account — or null. A parameter
     * set to the empty text names none.
     */
    private static String inheritedVolume(final Schema schema, final Database database, final Catalog catalog) {
        final String ofSchema = schema.getParameters().get(VOLUME_PARAMETER);
        if (ofSchema != null && !ofSchema.isEmpty()) {
            return ofSchema;
        }
        final String ofDatabase = database == null ? null : database.getParameters().get(VOLUME_PARAMETER);
        if (ofDatabase != null && !ofDatabase.isEmpty()) {
            return ofDatabase;
        }
        final String ofAccount = AccountParameters.value(catalog, VOLUME_PARAMETER);
        return ofAccount == null || ofAccount.isEmpty() ? null : ofAccount;
    }

    /**
     * The catalog the nearest container sets — the schema, its database, then the account — or null. The empty text
     * is a value like any other: it names no catalog, and what a further container sets stays out of it.
     */
    private static String inheritedCatalog(final Schema schema, final Database database, final Catalog catalog) {
        if (schema.getParameters().isSet(CATALOG_PARAMETER)) {
            return schema.getParameters().get(CATALOG_PARAMETER);
        }
        if (database != null && database.getParameters().isSet(CATALOG_PARAMETER)) {
            return database.getParameters().get(CATALOG_PARAMETER);
        }
        return AccountParameters.value(catalog, CATALOG_PARAMETER);
    }

    /**
     * Makes a table created with CREATE ICEBERG TABLE an Iceberg table: its metadata comes from the table
     * options, a clone or a copy starting from its Iceberg source's. A table that names no external volume takes
     * the EXTERNAL_VOLUME parameter of its schema, else its database's, else the account's, and its catalog comes
     * from the CATALOG parameter the same way. All live-verified, in this order: a table kept in managed storage
     * takes the SNOWFLAKE catalog alone; a volume must exist, and a table on one must name a catalog, which is
     * SNOWFLAKE here; then the storage serialization policy is read, and only then the base location.
     *
     * @param ctx the CREATE statement
     * @param table the table being created
     * @param source the CLONE or LIKE source, or null
     * @param catalog the catalog
     * @param databaseName the table's database
     * @param schema the table's schema
     */
    static void applyAtCreate(final FrostlakeParser.CreateStatementContext ctx, final Table table, final Table source,
                              final Catalog catalog, final String databaseName, final Schema schema) {
        if (ctx.ICEBERG() == null) {
            return;
        }
        final String schemaPath = databaseName + "/" + schema.getName();
        final boolean copied = source != null && source.getIcebergMetadata() != null;
        final IcebergTableMetadata metadata = copied ? source.getIcebergMetadata().copy() : new IcebergTableMetadata();
        String named = copied ? metadata.getCatalog() : null;
        String policy = null;
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.optionKey() == null || tail.copyOptionValue() == null) {
                continue;
            }
            final String key = tail.optionKey().getText().toUpperCase(Locale.ROOT);
            final String value = optionValue(tail.copyOptionValue());
            if ("EXTERNAL_VOLUME".equals(key)) {
                // The empty text names none.
                metadata.setExternalVolume(value.isEmpty() ? metadata.getExternalVolume() : value);
            } else if ("CATALOG".equals(key)) {
                named = value;
            } else if ("BASE_LOCATION".equals(key)) {
                metadata.setBaseLocation(value);
            } else if ("CATALOG_SYNC".equals(key)) {
                metadata.setCatalogSync(value);
            } else if ("STORAGE_SERIALIZATION_POLICY".equals(key)) {
                if (tail.copyOptionValue().INTEGER_LITERAL() != null) {
                    // A number is no text of any kind.
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value, key));
                }
                policy = value;
            }
        }
        final Database database = catalog.getDatabase(databaseName);
        if (metadata.getExternalVolume() == null) {
            metadata.setExternalVolume(inheritedVolume(schema, database, catalog));
        }
        final String catalogName = named != null ? named : inheritedCatalog(schema, database, catalog);
        final boolean namesCatalog = catalogName != null && !catalogName.isEmpty();
        final boolean ofIntegration = namesCatalog
            && !IcebergTableMetadata.SNOWFLAKE_CATALOG.equalsIgnoreCase(catalogName);
        if (metadata.getExternalVolume() == null && ofIntegration) {
            throw new RuntimeException("Iceberg table " + table.getName() + " must have the table parameter"
                + " EXTERNAL_VOLUME defined on the table, schema, database, or account.");
        }
        final boolean managedStorage = metadata.getExternalVolume() == null
            || DEFAULT_VOLUME.equalsIgnoreCase(metadata.getExternalVolume());
        if (managedStorage && ofIntegration) {
            throw new RuntimeException("SQL Compilation Error: Iceberg tables using Snowflake managed storage must"
                + " specify 'SNOWFLAKE' as the catalog.");
        }
        if (!managedStorage) {
            metadata.setExternalVolume(requireVolume(metadata.getExternalVolume(), catalog).getName());
            if (!namesCatalog) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Iceberg tables require a catalog integration to be specified"));
            }
            if (ofIntegration) {
                throw refusedCatalog(catalogName, ctx, catalog);
            }
        }
        metadata.setCatalog(IcebergTableMetadata.SNOWFLAKE_CATALOG);
        if (policy != null) {
            metadata.setStorageSerializationPolicy(policy(policy));
        }
        if (managedStorage) {
            if (metadata.getBaseLocation() != null && ctx.CLONE() == null && ctx.LIKE() == null) {
                throw new RuntimeException("SQL Compilation Error: BASE_LOCATION property is not supported for"
                    + " Iceberg tables using Snowflake Managed Storage.");
            }
            metadata.setExternalVolume(DEFAULT_VOLUME);
        }
        // A clone keeps its source's files; any other table gets a directory of its own.
        if (metadata.getBaseLocation() == null || ctx.LIKE() != null && source != null
                && source.getIcebergMetadata() != null
                && metadata.getBaseLocation().equals(source.getIcebergMetadata().getBaseLocation())) {
            metadata.setBaseLocation(schemaPath + "/" + table.getName() + "." + suffix() + "/");
        }
        // GET_DDL spells the base location a clone shares with its source (live-verified).
        metadata.setBaseLocationSpelled(ctx.CLONE() != null);
        table.setIcebergMetadata(metadata);
        // An Iceberg table always tracks its changes.
        for (final FrostlakeParser.TableTailOptionContext tail : ctx.tableTailOption()) {
            if (tail.optionKey() != null && tail.copyOptionValue() != null
                    && "CHANGE_TRACKING".equalsIgnoreCase(tail.optionKey().getText())
                    && "FALSE".equalsIgnoreCase(tail.copyOptionValue().getText())) {
                throw new RuntimeException(SqlCompilationError.of(CHANGE_TRACKING_REFUSAL));
            }
        }
        table.setChangeTracking(true);
    }

    /** The catalog integration of a name, matched exactly and then as an unquoted name folds, or null. */
    private static Integration catalogIntegration(final String written, final Catalog catalog) {
        final Integration integration = findByName(catalog.getIntegrations().find(written),
            catalog.getIntegrations().find(written.toUpperCase(Locale.ROOT)));
        return integration != null && integration.getKind() == IntegrationKind.CATALOG ? integration : null;
    }

    /**
     * The refusal of a table on a volume under another catalog than SNOWFLAKE. The name must be a catalog
     * integration's, as it is written, and such a table takes no column list (live-verified); any other such table
     * would read its data and its metadata from outside.
     */
    private static RuntimeException refusedCatalog(final String written, final FrostlakeParser.CreateStatementContext ctx,
                                                   final Catalog catalog) {
        final Integration integration = catalogIntegration(written, catalog);
        if (integration == null) {
            return new RuntimeException("Catalog integration " + written
                + " either does not exist or is not of type CATALOG. Please verify that catalog integration is valid.");
        }
        if (ctx.columnList() != null) {
            return new RuntimeException(SqlCompilationError.inline("Column specifications are only allowed for Iceberg"
                + " tables using the Snowflake catalog or Iceberg tables created within a catalog-linked database."));
        }
        return new RuntimeException(SqlCompilationError.of("Unsupported feature 'CATALOG = " + integration.getName()
            + "': an externally managed Iceberg table reads its data files from outside, which Frostlake does not."));
    }

    /**
     * Judges what a CATALOG parameter is set to, at any level: none, SNOWFLAKE in any case, or a catalog integration
     * (live-verified).
     *
     * @param written the name as SHOW PARAMETERS reports it
     * @param catalog the catalog
     */
    static void requireCatalogParameter(final String written, final Catalog catalog) {
        if (!written.isEmpty() && !IcebergTableMetadata.SNOWFLAKE_CATALOG.equalsIgnoreCase(written)
                && catalogIntegration(written, catalog) == null) {
            throw new RuntimeException("Catalog integration " + written
                + " is not valid. Please ensure required properties are valid.");
        }
    }

    /** The eight random characters that end a generated base location's directory name. */
    private static String suffix() {
        return StatementTokens.draw(SUFFIX_CHARACTERS, 8);
    }

    private static Integration findByName(final Integration exact, final Integration folded) {
        return exact != null ? exact : folded;
    }

    /** The external volume a table names, matched exactly and then as an unquoted name folds. */
    static ExternalVolume requireVolume(final String written, final Catalog catalog) {
        ExternalVolume volume = catalog.getExternalVolumes().find(written);
        if (volume == null) {
            volume = catalog.getExternalVolumes().find(written.toUpperCase(Locale.ROOT));
        }
        if (volume == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("External volume",
                written.toUpperCase(Locale.ROOT)));
        }
        return volume;
    }

    private static String policy(final String value) {
        final String upper = value.toUpperCase(Locale.ROOT);
        if (!POLICIES.contains(upper)) {
            throw new RuntimeException("SQL Compilation error:\nInvalid value for storage serialization policy."
                + " Supported are COMPATIBLE,OPTIMIZED.");
        }
        return upper;
    }

    private static String optionValue(final FrostlakeParser.CopyOptionValueContext value) {
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        if (value.qualifiedName() != null) {
            return QualifiedName.join(ParseTreeText.qualifiedNameParts(value.qualifiedName()));
        }
        return value.getText();
    }

    /**
     * ALTER ICEBERG TABLE … REFRESH and … CONVERT TO MANAGED. Both act on an externally managed table, and every
     * Iceberg table Frostlake holds is Snowflake-managed, so both are refused for it; a table that is not an
     * Iceberg table is refused as one of another type.
     */
    static void alter(final Table table, final FrostlakeParser.IcebergTableActionContext action) {
        final IcebergTableMetadata metadata = table.getIcebergMetadata();
        if (action.CONVERT() != null) {
            for (final Map.Entry<String, PropertyValue> property
                    : ObjectProperties.read(action.objectProperty()).entrySet()) {
                if (!"BASE_LOCATION".equals(property.getKey())
                        && !"STORAGE_SERIALIZATION_POLICY".equals(property.getKey())) {
                    throw new RuntimeException(SqlCompilationError.of("invalid property '" + property.getKey()
                        + "' for 'ICEBERG_TABLE'"));
                }
            }
        }
        if (metadata == null || metadata.isManaged()) {
            throw new RuntimeException("SQL Compilation error:\nALTER command failed. The provided table must be an"
                + " Iceberg table with an external catalog integration to perform the command "
                + (action.CONVERT() != null ? "ICEBERG_TABLE_CONVERT_TO_MANAGED" : "ICEBERG_TABLE_REFRESH")
                + ". The type of table " + table.getName() + " is " + (metadata == null ? "NOT_ICEBERG" : "MANAGED")
                + ".");
        }
    }

    /**
     * A clone is of its source's kind: an Iceberg table is cloned by CREATE ICEBERG TABLE … CLONE alone, and
     * that statement clones nothing else (live-verified).
     */
    static void requireCloneOfSameKind(final FrostlakeParser.CreateStatementContext ctx, final Table source) {
        final boolean iceberg = source.getIcebergMetadata() != null;
        if (iceberg && ctx.ICEBERG() == null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid operation The table " + source.getName()
                + " is an Iceberg table. Clone Iceberg table should use CREATE ICEBERG TABLE CLONE command. Please"
                + " ensure the appropriate command format is used."));
        }
        if (!iceberg && ctx.ICEBERG() != null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid operation The table " + source.getName()
                + " is not Iceberg table. Clone from Non-Iceberg table to Iceberg table is not supported. Please use"
                + " Iceberg table with Snowflake catalog to clone."));
        }
    }

    /** The refusal of a statement that alters an Iceberg table as an ordinary one (live-verified). */
    static RuntimeException alteredAsTable(final Table table) {
        return new RuntimeException("SQL Compilation error: The table " + table.getName() + " is an Iceberg table."
            + " Iceberg tables should use ALTER ICEBERG TABLE commands. Please ensure the appropriate command"
            + " format is used.");
    }

    /**
     * An Iceberg table always tracks its changes: a SET that turns CHANGE_TRACKING off and an UNSET of it are
     * refused, whatever else the statement sets, before anything is changed (live-verified).
     */
    static void requireChangeTrackingKept(final Table table, final FrostlakeParser.TableActionContext action) {
        if (table.getIcebergMetadata() == null) {
            return;
        }
        boolean turnedOff = false;
        if (action.tableUnsetProperties() != null) {
            for (final FrostlakeParser.OptionKeyContext key : action.tableUnsetProperties().optionKey()) {
                turnedOff = turnedOff || "CHANGE_TRACKING".equalsIgnoreCase(key.getText());
            }
        }
        for (final FrostlakeParser.TableSetPropertyContext property : action.tableSetProperty()) {
            turnedOff = turnedOff || "CHANGE_TRACKING".equalsIgnoreCase(property.optionKey().getText())
                && property.copyOptionValue() != null
                && "FALSE".equalsIgnoreCase(property.copyOptionValue().getText());
        }
        if (turnedOff) {
            throw new RuntimeException(SqlCompilationError.of(CHANGE_TRACKING_REFUSAL));
        }
    }

    /** The table's Iceberg metadata; a table that has none is refused as a table of another type. */
    static IcebergTableMetadata requireIceberg(final Table table) {
        if (table.getIcebergMetadata() == null) {
            throw new RuntimeException(SqlCompilationError.objectOfOtherType("TABLE", "ICEBERG_TABLE"));
        }
        return table.getIcebergMetadata();
    }

    /**
     * SHOW ICEBERG TABLES: the Iceberg tables among a SHOW TABLES listing of the same scope, in the columns the
     * Iceberg listing reports.
     */
    static ResultSet listing(final ResultSet tableListing, final Catalog catalog) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON));
        for (final String name : Arrays.asList("name", "database_name", "schema_name", "owner",
                "external_volume_name", "catalog_name", "iceberg_table_type", "catalog_table_name",
                "catalog_namespace", "base_location", "can_write_metadata", "comment", "owner_role_type",
                "name_mapping", "catalog_sync_name", "auto_refresh_status", "partition_specs")) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        columns.add(new ResultSetColumn("current_partition_spec_id", ShowResultHelpers.COUNT));
        columns.add(new ResultSetColumn("iceberg_table_format_version", ShowResultHelpers.COUNT));
        final int iceberg = tableListing.getColumnIndex("is_iceberg");
        final int created = tableListing.getColumnIndex("created_on");
        final int name = tableListing.getColumnIndex("name");
        final int database = tableListing.getColumnIndex("database_name");
        final int schema = tableListing.getColumnIndex("schema_name");
        final int owner = tableListing.getColumnIndex("owner");
        final int comment = tableListing.getColumnIndex("comment");
        final List<Row> rows = new ArrayList<>();
        for (final Row row : tableListing.getRows()) {
            if (!"Y".equals(row.getValue(iceberg))) {
                continue;
            }
            final Table table = catalog.getDatabase((String) row.getValue(database))
                .getSchema((String) row.getValue(schema)).getTable((String) row.getValue(name));
            final IcebergTableMetadata metadata = table.getIcebergMetadata();
            rows.add(new Row(Arrays.asList(row.getValue(created), row.getValue(name), row.getValue(database),
                row.getValue(schema), row.getValue(owner), metadata.getExternalVolume(), metadata.getCatalog(),
                metadata.isManaged() ? "MANAGED" : "UNMANAGED", metadata.getCatalogTableName(),
                metadata.getCatalogNamespace(), metadata.getBaseLocation(), metadata.isManaged() ? "Y" : "N",
                ShowResultHelpers.text((String) row.getValue(comment)), ShowResultHelpers.OWNER_ROLE_TYPE, null,
                ShowResultHelpers.text(metadata.getCatalogSync()), "", PARTITION_SPECS, Long.valueOf(0L),
                Long.valueOf(2L))));
        }
        return new ResultSet(columns, rows);
    }
}
