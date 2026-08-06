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

package dev.frostlake.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalog-aware SQL behind the {@code java.sql.DatabaseMetaData} object-listing calls, shared by
 * both JDBC transports ({@link DatabaseMetaData} over HTTP and {@link DirectDatabaseMetaData}
 * in-process) so the two answer identically.
 *
 * <p>The reason this exists: {@code INFORMATION_SCHEMA} is <em>per-database</em> in Snowflake
 * semantics, so an unqualified {@code FROM INFORMATION_SCHEMA.SCHEMATA} only ever sees the session's
 * current database. A metadata call naming some other catalog would then silently return nothing —
 * which is what left a database's schema list empty in a browsing tool. Every query built here is
 * therefore qualified with an explicit database ({@code "DB".INFORMATION_SCHEMA.<view>}), one branch
 * per catalog in scope, {@code UNION ALL}-ed together.
 *
 * <p>Every result here carries the column labels, order and count {@code java.sql.DatabaseMetaData}
 * specifies and Snowflake's driver returns — {@code TABLE_CAT} / {@code TABLE_SCHEM} rather than the
 * catalog's own {@code TABLE_CATALOG} / {@code TABLE_SCHEMA}, ten columns for {@code getTables} and
 * twenty-four for {@code getColumns}. The projections are wide because a client is entitled to read
 * any of those columns by name, and one of them — {@code getColumns.DATA_TYPE} — has to be an
 * {@code int} for {@code rs.getInt} to work at all.
 *
 * <p>Argument handling follows what Snowflake's own JDBC driver (3.20.0) was measured to return:
 * a {@code null} catalog spans every database; a catalog naming no database yields zero rows rather
 * than an error; and an empty-string catalog — JDBC's "objects without a catalog" — also yields zero
 * rows. Pattern arguments are SQL {@code LIKE} patterns where {@code null} and {@code "%"} mean
 * "everything", while an empty pattern is a real pattern that matches nothing.
 */
final class JdbcMetadataQueries {

    private JdbcMetadataQueries() {
    }

    private static final String SCHEMAS_PROJECTION =
        "SELECT SCHEMA_NAME AS TABLE_SCHEM, CATALOG_NAME AS TABLE_CATALOG FROM ";

    /**
     * The {@code TABLE_TYPE} a JDBC client sees, from the {@code INFORMATION_SCHEMA} value underneath.
     * Snowflake's driver (3.20.0, measured over a database holding one of each object)
     * collapses the catalog's richer vocabulary onto the two types {@code getTableTypes()} advertises:
     * {@code BASE TABLE} (permanent, transient and dynamic tables alike) and {@code LOCAL TEMPORARY}
     * become {@code TABLE}; {@code VIEW} and {@code MATERIALIZED VIEW} both become {@code VIEW}.
     *
     * <p>Snowflake's driver actually reports a temporary table as {@code TEMPORARY} and — only when
     * {@code types} is exactly {@code {"TABLE"}} — a transient table as {@code TRANSIENT}, two values
     * it neither advertises nor accepts back as a filter, and which it does not report for the same
     * table when the call is unfiltered. That self-inconsistency is not worth reproducing: Frostlake
     * reports a table as {@code TABLE} whichever way the call asked for it.
     */
    private static final String TABLE_TYPE_EXPRESSION =
        "CASE WHEN TABLE_TYPE IN ('VIEW', 'MATERIALIZED VIEW') THEN 'VIEW' ELSE 'TABLE' END";

    /**
     * {@code getTables}' ten columns, in the order {@code java.sql.DatabaseMetaData} specifies and
     * Snowflake's driver returns. Only the first four carry anything: live leaves {@code REMARKS} an
     * empty string and the other five null.
     */
    private static final String TABLES_PROJECTION =
        "SELECT TABLE_CATALOG AS TABLE_CAT, TABLE_SCHEMA AS TABLE_SCHEM, TABLE_NAME, "
        + TABLE_TYPE_EXPRESSION + " AS TABLE_TYPE, "
        + "'' AS REMARKS, CAST(NULL AS VARCHAR) AS TYPE_CAT, CAST(NULL AS VARCHAR) AS TYPE_SCHEM, "
        + "CAST(NULL AS VARCHAR) AS TYPE_NAME, CAST(NULL AS VARCHAR) AS SELF_REFERENCING_COL_NAME, "
        + "CAST(NULL AS VARCHAR) AS REF_GENERATION FROM ";

    /** The semi-structured and geospatial types, which share one set of answers in every expression below. */
    private static final String UNSIZED_TYPES = "('VARIANT', 'OBJECT', 'ARRAY', 'GEOGRAPHY', 'GEOMETRY')";

    /**
     * A column's {@code java.sql.Types} code, from the canonical family name
     * {@code INFORMATION_SCHEMA.COLUMNS.DATA_TYPE} reports.
     *
     * <p>Measured from Snowflake's driver on. The one non-obvious rule is that a
     * {@code NUMBER} splits on its scale — scale 0 is a {@code BIGINT} whatever its precision (5 and
     * 38 both measured), any other scale is a {@code DECIMAL}. Since {@code NUMBER(38,0)} is what
     * Snowflake gives a bare {@code INT}, most integer columns land on {@code BIGINT}. Everything
     * unrecognised falls through to {@code VARCHAR}, which is also where the semi-structured and
     * geospatial types land.
     */
    private static final String SQL_TYPE_EXPRESSION =
        "CASE WHEN DATA_TYPE = 'NUMBER' AND NUMERIC_SCALE = 0 THEN " + Types.BIGINT
        + " WHEN DATA_TYPE = 'NUMBER' THEN " + Types.DECIMAL
        + " WHEN DATA_TYPE = 'FLOAT' THEN " + Types.DOUBLE
        + " WHEN DATA_TYPE = 'BOOLEAN' THEN " + Types.BOOLEAN
        + " WHEN DATA_TYPE = 'DATE' THEN " + Types.DATE
        + " WHEN DATA_TYPE = 'TIME' THEN " + Types.TIME
        + " WHEN DATA_TYPE IN ('TIMESTAMP_NTZ', 'TIMESTAMP_LTZ') THEN " + Types.TIMESTAMP
        + " WHEN DATA_TYPE = 'TIMESTAMP_TZ' THEN " + Types.TIMESTAMP_WITH_TIMEZONE
        + " WHEN DATA_TYPE = 'BINARY' THEN " + Types.BINARY
        + " ELSE " + Types.VARCHAR + " END";

    /**
     * The type name a JDBC client sees. Snowflake's driver renames three families out of the
     * catalog's vocabulary — {@code TEXT} to {@code VARCHAR}, {@code FLOAT} to {@code DOUBLE}, and the
     * timestamps to their unpunctuated spellings — and passes everything else through.
     */
    private static final String TYPE_NAME_EXPRESSION =
        "CASE WHEN DATA_TYPE = 'TEXT' THEN 'VARCHAR'"
        + " WHEN DATA_TYPE = 'FLOAT' THEN 'DOUBLE'"
        + " WHEN DATA_TYPE = 'TIMESTAMP_NTZ' THEN 'TIMESTAMPNTZ'"
        + " WHEN DATA_TYPE = 'TIMESTAMP_LTZ' THEN 'TIMESTAMPLTZ'"
        + " WHEN DATA_TYPE = 'TIMESTAMP_TZ' THEN 'TIMESTAMPTZ'"
        + " ELSE DATA_TYPE END";

    /** Precision for exact numerics, declared length for character and binary, 0 for everything else. */
    private static final String COLUMN_SIZE_EXPRESSION =
        "CASE WHEN DATA_TYPE = 'NUMBER' THEN NUMERIC_PRECISION"
        + " WHEN DATA_TYPE = 'TEXT' THEN CHARACTER_MAXIMUM_LENGTH"
        + " WHEN DATA_TYPE = 'BINARY' THEN CHARACTER_OCTET_LENGTH"
        + " ELSE 0 END";

    /** Scale for exact numerics, fractional-second digits for the time types, 0 for everything else. */
    private static final String DECIMAL_DIGITS_EXPRESSION =
        "CASE WHEN DATA_TYPE = 'NUMBER' THEN NUMERIC_SCALE"
        + " WHEN DATA_TYPE IN ('TIME', 'TIMESTAMP_NTZ', 'TIMESTAMP_LTZ', 'TIMESTAMP_TZ')"
        + " THEN DATETIME_PRECISION"
        + " ELSE 0 END";

    /** Character columns report their length; the semi-structured types report 0; the rest report nothing. */
    private static final String CHAR_OCTET_LENGTH_EXPRESSION =
        "CASE WHEN DATA_TYPE = 'TEXT' THEN CHARACTER_MAXIMUM_LENGTH"
        + " WHEN DATA_TYPE IN " + UNSIZED_TYPES + " THEN 0"
        + " ELSE CAST(NULL AS INTEGER) END";

    /**
     * {@code getColumns}' twenty-four columns, in the specified order.
     *
     * <p>{@code DATA_TYPE} and {@code SQL_DATA_TYPE} are {@code java.sql.Types} <em>integers</em>, not
     * type names — {@code TYPE_NAME} is the string column. Returning a name from {@code DATA_TYPE},
     * as this used to, breaks {@code rs.getInt("DATA_TYPE")} outright.
     *
     * <p>Every value below matches Snowflake's driver as measured on across a spread of
     * column types, including the parts that look arbitrary: an exact-numeric column reports its
     * precision as {@code COLUMN_SIZE} and its scale as {@code DECIMAL_DIGITS}, a character column
     * reports its length, and everything else reports 0 rather than null; {@code NUM_PREC_RADIX},
     * {@code BUFFER_LENGTH} and the {@code SCOPE_*} columns are always null; and {@code REMARKS} is
     * an empty string.
     */
    private static final String COLUMNS_PROJECTION =
        "SELECT TABLE_CATALOG AS TABLE_CAT, TABLE_SCHEMA AS TABLE_SCHEM, TABLE_NAME, COLUMN_NAME, "
        + SQL_TYPE_EXPRESSION + " AS DATA_TYPE, "
        + TYPE_NAME_EXPRESSION + " AS TYPE_NAME, "
        + COLUMN_SIZE_EXPRESSION + " AS COLUMN_SIZE, "
        + "CAST(NULL AS INTEGER) AS BUFFER_LENGTH, "
        + DECIMAL_DIGITS_EXPRESSION + " AS DECIMAL_DIGITS, "
        + "CAST(NULL AS INTEGER) AS NUM_PREC_RADIX, "
        + "CASE WHEN IS_NULLABLE = 'NO' THEN 0 ELSE 1 END AS NULLABLE, "
        + "'' AS REMARKS, COLUMN_DEFAULT AS COLUMN_DEF, "
        + SQL_TYPE_EXPRESSION + " AS SQL_DATA_TYPE, "
        + "CAST(NULL AS INTEGER) AS SQL_DATETIME_SUB, "
        + CHAR_OCTET_LENGTH_EXPRESSION + " AS CHAR_OCTET_LENGTH, ORDINAL_POSITION, IS_NULLABLE, "
        + "CAST(NULL AS VARCHAR) AS SCOPE_CATALOG, CAST(NULL AS VARCHAR) AS SCOPE_SCHEMA, "
        + "CAST(NULL AS VARCHAR) AS SCOPE_TABLE, CAST(NULL AS INTEGER) AS SOURCE_DATA_TYPE, "
        + "IS_IDENTITY AS IS_AUTOINCREMENT, 'NO' AS IS_GENERATEDCOLUMN FROM ";

    /**
     * Primary keys are read through SHOW PRIMARY KEYS, re-projected from its result — the same route
     * Snowflake's own driver takes, and the only one available: INFORMATION_SCHEMA.COLUMNS carries no
     * key flag on a real account, and its key-column mapping lives nowhere else in the catalog.
     * The SHOW output names its columns in lower case, so they are quoted here.
     */
    /**
     * {@code getProcedures}' columns — SIX of them, which is NOT what {@code java.sql.DatabaseMetaData}
     * documents. The spec calls for nine, three of them "reserved for future use"; Snowflake's driver
     * simply omits those three, and this is measured from it rather than copied from the interface.
     * Frostlake follows Snowflake, because a client reading these by index has to see what it would see
     * against the real thing.
     *
     * <p>{@code REMARKS} is the fixed string live returns, and {@code PROCEDURE_TYPE} is
     * {@code procedureReturnsResult} (2) for every row: a Snowflake procedure always returns a value.
     * {@code SPECIFIC_NAME} carries the SIGNATURE — {@code MP(NUMBER) RETURN VARCHAR} — where the
     * function equivalent below carries the bare name, an asymmetry that is live's, not ours.
     */
    /**
     * The fourteen columns {@code getImportedKeys} / {@code getExportedKeys} / {@code getCrossReference}
     * all share — one shape, three filters. Read off {@code SHOW IMPORTED KEYS}, whose own output was
     * widened to carry both database names for exactly this.
     *
     * <p>Constants, measured from live rather than chosen: {@code UPDATE_RULE} and {@code DELETE_RULE}
     * are {@code importedKeyNoAction} (3) and {@code DEFERRABILITY} is {@code importedKeyNotDeferrable}
     * (7). Snowflake does not enforce foreign keys and has no deferred checking, so no other value can
     * arise — but the numbers are what its driver returns, not what this code decided.
     */
    private static final String FOREIGN_KEYS_PROJECTION =
        "SELECT \"pk_database_name\" AS PKTABLE_CAT, \"pk_schema_name\" AS PKTABLE_SCHEM, "
        + "\"pk_table_name\" AS PKTABLE_NAME, \"pk_column_name\" AS PKCOLUMN_NAME, "
        + "\"fk_database_name\" AS FKTABLE_CAT, \"fk_schema_name\" AS FKTABLE_SCHEM, "
        + "\"fk_table_name\" AS FKTABLE_NAME, \"fk_column_name\" AS FKCOLUMN_NAME, "
        + "\"key_sequence\" AS KEY_SEQ, 3 AS UPDATE_RULE, 3 AS DELETE_RULE, "
        + "\"fk_name\" AS FK_NAME, \"pk_name\" AS PK_NAME, 7 AS DEFERRABILITY "
        + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE 1=1";

    private static final String PROCEDURES_PROJECTION =
        "SELECT PROCEDURE_CATALOG AS PROCEDURE_CAT, PROCEDURE_SCHEMA AS PROCEDURE_SCHEM, "
        + "PROCEDURE_NAME, 'user-defined procedure' AS REMARKS, 2 AS PROCEDURE_TYPE, "
        + "PROCEDURE_NAME || ARGUMENT_SIGNATURE || ' RETURN ' || DATA_TYPE AS SPECIFIC_NAME FROM ";

    /**
     * {@code getFunctions}' six columns. {@code FUNCTION_TYPE} separates the two kinds, measured live:
     * {@code functionNoTable} (1) for a scalar UDF and {@code functionReturnsTable} (2) for a UDTF. A
     * table function is recognised by its {@code DATA_TYPE}, which Frostlake's INFORMATION_SCHEMA
     * already writes as {@code TABLE (col TYPE, …)} rather than a scalar type name.
     */
    private static final String FUNCTIONS_PROJECTION =
        "SELECT FUNCTION_CATALOG AS FUNCTION_CAT, FUNCTION_SCHEMA AS FUNCTION_SCHEM, "
        + "FUNCTION_NAME, 'user-defined function' AS REMARKS, "
        + "CASE WHEN DATA_TYPE LIKE 'TABLE (%' THEN 2 ELSE 1 END AS FUNCTION_TYPE, "
        + "FUNCTION_NAME AS SPECIFIC_NAME FROM ";

    private static final String PRIMARY_KEYS_PROJECTION =
        "SELECT \"database_name\" AS TABLE_CAT, \"schema_name\" AS TABLE_SCHEM, "
        + "\"table_name\" AS TABLE_NAME, \"column_name\" AS COLUMN_NAME, "
        + "\"key_sequence\" AS KEY_SEQ, \"constraint_name\" AS PK_NAME "
        + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE 1=1";

    /**
     * {@code DatabaseMetaData.getSchemas(catalog, schemaPattern)} — one row per schema, labelled
     * {@code TABLE_SCHEM} / {@code TABLE_CATALOG} and ordered by catalog then schema, matching the
     * shape and ordering measured from Snowflake's driver.
     */
    static ResultSet schemas(final Connection connection, final String catalog, final String schemaPattern)
            throws SQLException {
        final List<String> catalogs = catalogsInScope(connection, catalog);
        final StringBuilder sql = new StringBuilder();
        for (int i = 0; i < catalogs.size(); i++) {
            final String database = catalogs.get(i);
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append(SCHEMAS_PROJECTION).append(qualifier(database)).append("SCHEMATA WHERE 1=1");
            appendPattern(sql, "SCHEMA_NAME", schemaPattern);
            appendNoMatchGuard(sql, database);
        }
        sql.append(" ORDER BY TABLE_CATALOG, TABLE_SCHEM");
        return query(connection, sql.toString());
    }

    /**
     * {@code DatabaseMetaData.getTables(...)}, spanning every catalog in scope. Views and
     * materialized views are listed alongside tables, exactly as live Snowflake does.
     */
    static ResultSet tables(final Connection connection, final String catalog, final String schemaPattern,
                            final String tableNamePattern, final String[] types) throws SQLException {
        final List<String> catalogs = catalogsInScope(connection, catalog);
        final StringBuilder sql = new StringBuilder();
        for (int i = 0; i < catalogs.size(); i++) {
            final String database = catalogs.get(i);
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append(TABLES_PROJECTION).append(qualifier(database)).append("TABLES WHERE 1=1");
            appendPattern(sql, "TABLE_SCHEMA", schemaPattern);
            appendPattern(sql, "TABLE_NAME", tableNamePattern);
            appendTypeFilter(sql, types);
            appendNoMatchGuard(sql, database);
        }
        sql.append(" ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME");
        return query(connection, sql.toString());
    }

    /** {@code DatabaseMetaData.getColumns(...)}, spanning every catalog in scope. */
    static ResultSet columns(final Connection connection, final String catalog, final String schemaPattern,
                             final String tableNamePattern, final String columnNamePattern) throws SQLException {
        final List<String> catalogs = catalogsInScope(connection, catalog);
        final StringBuilder sql = new StringBuilder();
        for (int i = 0; i < catalogs.size(); i++) {
            final String database = catalogs.get(i);
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append(COLUMNS_PROJECTION).append(qualifier(database)).append("COLUMNS WHERE 1=1");
            appendPattern(sql, "TABLE_SCHEMA", schemaPattern);
            appendPattern(sql, "TABLE_NAME", tableNamePattern);
            appendPattern(sql, "COLUMN_NAME", columnNamePattern);
            appendNoMatchGuard(sql, database);
        }
        sql.append(" ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME, ORDINAL_POSITION");
        return query(connection, sql.toString());
    }

    /**
     * {@code DatabaseMetaData.getPrimaryKeys(...)}, spanning every catalog in scope. Schema and table
     * are exact names here rather than patterns, per the JDBC contract for this call.
     */
    static ResultSet primaryKeys(final Connection connection, final String catalog, final String schema,
                                 final String table) throws SQLException {
        final List<String> catalogs = catalogsInScope(connection, catalog);
        // SHOW PRIMARY KEYS is scoped per statement, so the widest scope is listed once and then
        // filtered; a caller naming no real catalog gets a correctly shaped, zero-row answer.
        final StringBuilder sql = new StringBuilder(PRIMARY_KEYS_PROJECTION);
        if (catalogs.size() == 1 && catalogs.get(0) == null) {
            sql.append(" AND 1=0");
        } else {
            appendInList(sql, "\"database_name\"", catalogs);
            appendEquals(sql, "\"schema_name\"", schema);
            appendEquals(sql, "\"table_name\"", table);
        }
        sql.append(" ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME, KEY_SEQ");
        // The listing and its re-projection go out as ONE multi-statement request: RESULT_SCAN
        // reads the listing back, and pairing them costs one round trip instead of two.
        final Statement statement = connection.createStatement();
        statement.execute("SHOW PRIMARY KEYS IN ACCOUNT; " + sql);
        // Advance exactly once, past the listing, to the re-projection: a further getMoreResults()
        // would close that result set while looking for a third statement that does not exist.
        statement.getMoreResults();
        return statement.getResultSet();
    }

    /** {@code AND UPPER(col) IN ('A', 'B')}, skipped when the list is empty. */
    /**
     * {@code DatabaseMetaData.getProcedures(...)}, spanning every catalog in scope — the call a tool
     * makes to put a Procedures node under a schema.
     */
    static ResultSet procedures(final Connection connection, final String catalog,
                                final String schemaPattern, final String procedureNamePattern)
            throws SQLException {
        final List<String> catalogs = catalogsInScope(connection, catalog);
        final StringBuilder sql = new StringBuilder();
        for (int i = 0; i < catalogs.size(); i++) {
            final String database = catalogs.get(i);
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append(PROCEDURES_PROJECTION).append(qualifier(database)).append("PROCEDURES WHERE 1=1");
            appendPattern(sql, "PROCEDURE_SCHEMA", schemaPattern);
            appendPattern(sql, "PROCEDURE_NAME", procedureNamePattern);
            appendNoMatchGuard(sql, database);
        }
        sql.append(" ORDER BY PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME");
        return query(connection, sql.toString());
    }

    /** {@code DatabaseMetaData.getFunctions(...)} — scalar UDFs and UDTFs alike, as live lists both. */
    static ResultSet functions(final Connection connection, final String catalog,
                               final String schemaPattern, final String functionNamePattern)
            throws SQLException {
        final List<String> catalogs = catalogsInScope(connection, catalog);
        final StringBuilder sql = new StringBuilder();
        for (int i = 0; i < catalogs.size(); i++) {
            final String database = catalogs.get(i);
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append(FUNCTIONS_PROJECTION).append(qualifier(database)).append("FUNCTIONS WHERE 1=1");
            appendPattern(sql, "FUNCTION_SCHEMA", schemaPattern);
            appendPattern(sql, "FUNCTION_NAME", functionNamePattern);
            appendNoMatchGuard(sql, database);
        }
        sql.append(" ORDER BY FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME");
        return query(connection, sql.toString());
    }

    /**
     * The foreign keys of one table, filtered at whichever end the caller asked about — the FK side for
     * {@code getImportedKeys}, the PK side for {@code getExportedKeys}, both for
     * {@code getCrossReference}. A null table on a side means "do not filter that side".
     *
     * <p>Follows {@link #primaryKeys}: one multi-statement round trip, the SHOW listing then a
     * re-projection reading it back through RESULT_SCAN.
     */
    static ResultSet foreignKeys(final Connection connection,
                                 final String pkCatalog, final String pkSchema, final String pkTable,
                                 final String fkCatalog, final String fkSchema, final String fkTable)
            throws SQLException {
        final StringBuilder sql = new StringBuilder(FOREIGN_KEYS_PROJECTION);
        appendEquals(sql, "\"pk_database_name\"", pkCatalog);
        appendEquals(sql, "\"pk_schema_name\"", pkSchema);
        appendEquals(sql, "\"pk_table_name\"", pkTable);
        appendEquals(sql, "\"fk_database_name\"", fkCatalog);
        appendEquals(sql, "\"fk_schema_name\"", fkSchema);
        appendEquals(sql, "\"fk_table_name\"", fkTable);
        // Live orders imported keys by the PK end then the sequence, which is also the order a tool
        // wants when drawing a table's inbound references.
        sql.append(" ORDER BY PKTABLE_CAT, PKTABLE_SCHEM, PKTABLE_NAME, KEY_SEQ");
        final Statement statement = connection.createStatement();
        statement.execute("SHOW IMPORTED KEYS IN ACCOUNT; " + sql);
        statement.getMoreResults();
        return statement.getResultSet();
    }

    /**
     * {@code DatabaseMetaData.getTypeInfo()} — the eight rows Snowflake's driver returns, COPIED from it
     * rather than derived from Frostlake's type hierarchy.
     *
     * <pre>
     *   NUMBER    3   precision 38, scale 0..37
     *   INTEGER   4   precision 38, scale 0..0
     *   DOUBLE    8   precision 38, scale 0..37
     *   VARCHAR  12   precision -1
     *   DATE     91   TIME 92   TIMESTAMP 93   BOOLEAN 16
     * </pre>
     *
     * <p>Several cells are the driver's constants rather than facts about the type, and deriving them
     * would produce something more "correct" and less faithful: {@code CASE_SENSITIVE} is false even for
     * VARCHAR, {@code AUTO_INCREMENT} is true on every row including DATE, and {@code SQL_DATA_TYPE},
     * {@code SQL_DATETIME_SUB} and {@code NUM_PREC_RADIX} are -1 throughout. A client comparing against
     * a real account sees these values, so these are the values.
     *
     * <p>The list is also SHORTER than the type system: no VARIANT, OBJECT, ARRAY, BINARY, GEOGRAPHY or
     * the TIMESTAMP variants. That is live's list, not an omission here.
     */
    static ResultSet typeInfo(final Connection connection) throws SQLException {
        final StringBuilder sql = new StringBuilder();
        final String[][] types = {
            //  name        jdbc  precision  minScale  maxScale
            {"NUMBER",    "3",  "38", "0",  "37"},
            {"INTEGER",   "4",  "38", "0",  "0"},
            {"DOUBLE",    "8",  "38", "0",  "37"},
            {"VARCHAR",   "12", "-1", "-1", "-1"},
            {"DATE",      "91", "-1", "-1", "-1"},
            {"TIME",      "92", "-1", "-1", "-1"},
            {"TIMESTAMP", "93", "-1", "-1", "-1"},
            {"BOOLEAN",   "16", "-1", "-1", "-1"},
        };
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT '").append(types[i][0]).append("' AS TYPE_NAME, ")
               .append(types[i][1]).append(" AS DATA_TYPE, ")
               .append(types[i][2]).append(" AS PRECISION, ")
               .append("CAST(NULL AS VARCHAR) AS LITERAL_PREFIX, CAST(NULL AS VARCHAR) AS LITERAL_SUFFIX, ")
               .append("CAST(NULL AS VARCHAR) AS CREATE_PARAMS, 1 AS NULLABLE, FALSE AS CASE_SENSITIVE, ")
               .append("3 AS SEARCHABLE, FALSE AS UNSIGNED_ATTRIBUTE, TRUE AS FIXED_PREC_SCALE, ")
               .append("TRUE AS AUTO_INCREMENT, CAST(NULL AS VARCHAR) AS LOCAL_TYPE_NAME, ")
               .append(types[i][3]).append(" AS MINIMUM_SCALE, ")
               .append(types[i][4]).append(" AS MAXIMUM_SCALE, ")
               .append("-1 AS SQL_DATA_TYPE, -1 AS SQL_DATETIME_SUB, -1 AS NUM_PREC_RADIX");
        }
        return query(connection, sql.toString());
    }

    private static void appendInList(final StringBuilder sql, final String column,
                                     final List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        sql.append(" AND UPPER(").append(column).append(") IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('\'').append(values.get(i).toUpperCase().replace("'", "''")).append('\'');
        }
        sql.append(')');
    }

    /**
     * The databases a metadata call should read, given its {@code catalog} argument.
     *
     * <p>Qualifying a query with a name that is not a database is an error in the engine ("Database
     * does not exist"), so a name that matches nothing must never reach the SQL. The list is instead
     * never empty and never partially matching: it holds either the real databases in scope, or the
     * single element {@code null} meaning "no catalog in scope at all" — which
     * {@link #appendNoMatchGuard} turns into a correctly shaped, zero-row answer.
     */
    private static List<String> catalogsInScope(final Connection connection, final String catalog)
            throws SQLException {
        final List<String> matched = new ArrayList<>();
        try (final Statement statement = connection.createStatement();
             final ResultSet rs = statement.executeQuery(
                 "SELECT DATABASE_NAME FROM INFORMATION_SCHEMA.DATABASES ORDER BY DATABASE_NAME")) {
            while (rs.next()) {
                final String name = rs.getString(1);
                if (name != null && (catalog == null || matches(name, catalog))) {
                    matched.add(name);
                }
            }
        }
        if (matched.isEmpty()) {
            matched.add(null);
        }
        return matched;
    }

    /**
     * Disables a branch built for the "no catalog in scope" placeholder, so the statement keeps the
     * right column shape but returns no rows — what Snowflake's driver answers for a catalog naming
     * no database, and for JDBC's empty-string "objects without a catalog".
     */
    private static void appendNoMatchGuard(final StringBuilder sql, final String database) {
        if (database == null) {
            sql.append(" AND 1=0");
        }
    }

    /**
     * Whether a database answers to the {@code catalog} argument. Frostlake folds unquoted
     * identifiers to upper case and resolves names case-insensitively, so metadata arguments are
     * matched the same way — as the sibling {@code getTables}/{@code getColumns} filters always have.
     * Snowflake's own driver was measured to be case-SENSITIVE here (a lower-case catalog argument
     * returns zero rows); Frostlake is deliberately the more forgiving of the two, since the strict
     * reading can only ever hide objects that exist.
     */
    private static boolean matches(final String databaseName, final String catalog) {
        return databaseName.equalsIgnoreCase(catalog);
    }

    /**
     * {@code "NAME".INFORMATION_SCHEMA.} — or the unqualified prefix for the placeholder branch,
     * which is always paired with an always-false predicate and so never reads anything.
     */
    private static String qualifier(final String databaseName) {
        if (databaseName == null) {
            return "INFORMATION_SCHEMA.";
        }
        return "\"" + databaseName.replace("\"", "\"\"") + "\".INFORMATION_SCHEMA.";
    }

    /**
     * A JDBC pattern argument as a {@code LIKE} filter. {@code null} and {@code "%"} match everything
     * and need no filter; an empty pattern is left as a real {@code LIKE ''}, which matches nothing —
     * live Snowflake returns zero rows for it.
     */
    private static void appendPattern(final StringBuilder sql, final String column, final String pattern) {
        if (pattern == null || "%".equals(pattern)) {
            return;
        }
        sql.append(" AND UPPER(").append(column).append(") LIKE '")
            .append(pattern.toUpperCase().replace("'", "''")).append("'");
    }

    /** An exact-name argument as an equality filter, skipping the "match all" cases (null, empty). */
    private static void appendEquals(final StringBuilder sql, final String column, final String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        sql.append(" AND UPPER(").append(column).append(") = '")
            .append(value.toUpperCase().replace("'", "''")).append("'");
    }

    /**
     * The {@code types[]} argument of {@code getTables}, matched against the {@code TABLE_TYPE} the
     * caller will actually be shown rather than the raw {@code INFORMATION_SCHEMA} value — otherwise
     * the two types {@code getTableTypes()} advertises would select nothing at all, which is the
     * defect this fixes.
     *
     * <p>{@code null} means "every type"; an empty array is a real filter that matches nothing, which
     * is what live Snowflake returns for it. Unrecognised entries — including the raw catalog values
     * {@code BASE TABLE} and {@code MATERIALIZED VIEW} — simply match no row, again as live. Live is
     * case-sensitive here and rejects {@code "table"}; Frostlake matches case-insensitively, staying
     * consistent with the leniency its other metadata arguments already have.
     */
    private static void appendTypeFilter(final StringBuilder sql, final String[] types) {
        if (types == null) {
            return;
        }
        sql.append(" AND ").append(TABLE_TYPE_EXPRESSION).append(" IN (");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            final String type = types[i] == null ? "" : types[i].toUpperCase();
            sql.append("'").append(type.replace("'", "''")).append("'");
        }
        if (types.length == 0) {
            sql.append("''");
        }
        sql.append(")");
    }

    private static ResultSet query(final Connection connection, final String sql) throws SQLException {
        return connection.createStatement().executeQuery(sql);
    }
}
