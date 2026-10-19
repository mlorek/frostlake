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
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * The database metadata both transports share. What it says about the SQL the server speaks — how NULLs sort, how
 * names are stored and how long they may be, which keywords and escape functions exist, how transactions behave — is
 * what the account's own driver says, answer for answer (live-verified), since that is the SQL Frostlake answers.
 * What it says about the driver — the cursor moves forward only and closes at commit — holds for both transports
 * alike; a named parameter of a callable statement binds, a surface of this driver's own. The product and the
 * driver name themselves as Frostlake, and each transport supplies its URL and user. The catalog listings run
 * through {@link JdbcMetadataQueries} over the connection, whichever transport carries it.
 */
public abstract class AbstractDatabaseMetaData implements java.sql.DatabaseMetaData {

    private final Connection connection;

    /**
     * The metadata of a connection.
     *
     * @param connection the connection the listings run on
     */
    protected AbstractDatabaseMetaData(final Connection connection) {
        this.connection = connection;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    // ---------------------------------------------------------------- identity

    @Override
    public String getDatabaseProductName() throws SQLException {
        return "Frostlake SQL Engine";
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return "1.0.0";
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        return 1;
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        return 0;
    }

    @Override
    public String getDriverName() throws SQLException {
        return "Frostlake JDBC Driver";
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return "1.0.0";
    }

    @Override
    public int getDriverMajorVersion() {
        return 1;
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        return 2;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    // ---------------------------------------------------------------- names, keywords and escape functions

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        return "\"";
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        return "\\";
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        return "$";
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        return "database";
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        return "schema";
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        return "procedure";
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        return ".";
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        return true;
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        return "ACCOUNT,ASOF,BIT,BYTEINT,CONNECTION,DATABASE,DATETIME,DATE_PART,FIXED,FOLLOWING,GSCLUSTER,GSPACKAGE,"
            + "IDENTIFIER,ILIKE,INCREMENT,ISSUE,LONG,MAP,MATCH_CONDITION,MINUS,NUMBER,OBJECT,ORGANIZATION,QUALIFY,"
            + "REFERENCE,REGEXP,RLIKE,SAMPLE,SCHEMA,STRING,TEXT,TIMESTAMPLTZ,TIMESTAMPNTZ,TIMESTAMPTZ,TIMESTAMP_LTZ,"
            + "TIMESTAMP_NTZ,TIMESTAMP_TZ,TINYINT,TRANSIT,TRY_CAST,VARIANT,VECTOR,VIEW";
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        return "ABS,ACOS,ASIN,ATAN,ATAN2,CBRT,CEILING,COS,COT,DEGREES,EXP,FACTORIAL,FLOOR,HAVERSINE,LN,LOG,MOD,PI,"
            + "POWER,RADIANS,RAND,ROUND,SIGN,SIN,SQRT,SQUARE,TAN,TRUNCATE";
    }

    @Override
    public String getStringFunctions() throws SQLException {
        return "ASCII,BIT_LENGTH,CHAR,CONCAT,INSERT,LCASE,LEFT,LENGTH,LPAD,LOCATE,LTRIM,OCTET_LENGTH,PARSE_IP,"
            + "PARSE_URL,REPEAT,REVERSE,REPLACE,RPAD,RTRIMMED_LENGTH,SPACE,SPLIT,SPLIT_PART,SPLIT_TO_TABLE,STRTOK,"
            + "STRTOK_TO_ARRAY,STRTOK_SPLIT_TO_TABLE,TRANSLATE,TRIM,UNICODE,UUID_STRING,INITCAP,LOWER,UPPER,REGEXP,"
            + "REGEXP_COUNT,REGEXP_INSTR,REGEXP_LIKE,REGEXP_REPLACE,REGEXP_SUBSTR,RLIKE,CHARINDEX,CONTAINS,"
            + "EDITDISTANCE,ENDSWITH,ILIKE,ILIKE ANY,LIKE,LIKE ALL,LIKE ANY,POSITION,REPLACE,RIGHT,STARTSWITH,"
            + "SUBSTRING,"
            + "COMPRESS,DECOMPRESS_BINARY,DECOMPRESS_STRING,BASE64_DECODE_BINARY,BASE64_DECODE_STRING,BASE64_ENCODE,"
            + "HEX_DECODE_BINARY,HEX_DECODE_STRING,HEX_ENCODE,TRY_BASE64_DECODE_BINARY,TRY_BASE64_DECODE_STRING,"
            + "TRY_HEX_DECODE_BINARY,TRY_HEX_DECODE_STRING,MD_5,MD5_HEX,MD5_BINARY,SHA1,SHA1_HEX,SHA2,SHA1_BINARY,"
            + "SHA2_HEX,SHA2_BINARY, HASH,HASH_AGG,COLLATE,COLLATION";
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        return "DATABASE,IFNULL,USER";
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        return "CURDATE,CURTIME,DAYNAME,DAYOFMONTH,DAYOFWEEK,DAYOFYEAR,HOUR,MINUTE,MONTH,MONTHNAME,NOW,QUARTER,SECOND,"
            + "TIMESTAMPADD,TIMESTAMPDIFF,WEEK,YEAR";
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        return true;
    }

    // ---------------------------------------------------------------- limits

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        return 67108864;
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        return 134217728;
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxConnections() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        return 0;
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return true;
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return 0;
    }

    // ---------------------------------------------------------------- the SQL

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return true;
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        return true;
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        return true;
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsConvert(final int fromType, final int toType) throws SQLException {
        return false;
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        return true;
    }

    // ---------------------------------------------------------------- transactions

    @Override
    public boolean supportsTransactions() throws SQLException {
        return true;
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_READ_COMMITTED;
    }

    /** READ COMMITTED, and NONE — the account's driver accepts the level that asks for no isolation. */
    @Override
    public boolean supportsTransactionIsolationLevel(final int level) throws SQLException {
        return level == Connection.TRANSACTION_NONE || level == Connection.TRANSACTION_READ_COMMITTED;
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return true;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        return false;
    }

    // ---------------------------------------------------------------- the driver

    @Override
    public boolean supportsResultSetType(final int type) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public boolean supportsResultSetConcurrency(final int type, final int concurrency) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public boolean supportsResultSetHoldability(final int holdability) throws SQLException {
        return holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public boolean ownUpdatesAreVisible(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean ownDeletesAreVisible(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean ownInsertsAreVisible(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersUpdatesAreVisible(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersDeletesAreVisible(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersInsertsAreVisible(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean updatesAreDetected(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean deletesAreDetected(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean insertsAreDetected(final int type) throws SQLException {
        return false;
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        return true;
    }

    /** A named parameter of a callable statement binds — a surface of this driver's own. */
    @Override
    public boolean supportsNamedParameters() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        return false;
    }

    @Override
    public int getSQLStateType() throws SQLException {
        return sqlStateSQL;
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        return false;
    }

    /** Not answered: the account's driver refuses the question. */
    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    /** Not answered: the account's driver refuses the question. */
    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    /** Not answered: the account's driver refuses the question. */
    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    // ---------------------------------------------------------------- the catalog listings

    @Override
    public ResultSet getTables(final String catalog, final String schemaPattern, final String tableNamePattern,
                               final String[] types) throws SQLException {
        return JdbcMetadataQueries.tables(connection, catalog, schemaPattern, tableNamePattern, types);
    }

    @Override
    public ResultSet getColumns(final String catalog, final String schemaPattern, final String tableNamePattern,
                                final String columnNamePattern) throws SQLException {
        return JdbcMetadataQueries.columns(connection, catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    /** Frostlake has no indexes, so the listing is always empty: the JDBC index columns and no row. */
    @Override
    public ResultSet getIndexInfo(final String catalog, final String schema, final String table, final boolean unique,
                                  final boolean approximate) throws SQLException {
        return connection.createStatement().executeQuery("""
            SELECT CAST(NULL AS VARCHAR) AS TABLE_CAT, CAST(NULL AS VARCHAR) AS TABLE_SCHEM,
                   CAST(NULL AS VARCHAR) AS TABLE_NAME, CAST(NULL AS VARCHAR) AS NON_UNIQUE,
                   CAST(NULL AS VARCHAR) AS INDEX_QUALIFIER, CAST(NULL AS VARCHAR) AS INDEX_NAME,
                   CAST(NULL AS INTEGER) AS TYPE, CAST(NULL AS INTEGER) AS ORDINAL_POSITION,
                   CAST(NULL AS VARCHAR) AS COLUMN_NAME, CAST(NULL AS VARCHAR) AS ASC_OR_DESC,
                   CAST(NULL AS INTEGER) AS CARDINALITY, CAST(NULL AS INTEGER) AS PAGES,
                   CAST(NULL AS VARCHAR) AS FILTER_CONDITION FROM INFORMATION_SCHEMA.COLUMNS WHERE 1=0""");
    }

    @Override
    public ResultSet getPrimaryKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        return JdbcMetadataQueries.primaryKeys(connection, catalog, schema, table);
    }

    /** Every foreign key whose child side is this table — what a tool draws as the table's outbound references. */
    @Override
    public ResultSet getImportedKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        return JdbcMetadataQueries.foreignKeys(connection, null, null, null, catalog, schema, table);
    }

    /** Every foreign key pointing at this table. */
    @Override
    public ResultSet getExportedKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        return JdbcMetadataQueries.foreignKeys(connection, catalog, schema, table, null, null, null);
    }

    @Override
    public ResultSet getCrossReference(final String parentCatalog, final String parentSchema, final String parentTable,
                                       final String foreignCatalog, final String foreignSchema,
                                       final String foreignTable) throws SQLException {
        return JdbcMetadataQueries.foreignKeys(connection, parentCatalog, parentSchema, parentTable,
            foreignCatalog, foreignSchema, foreignTable);
    }

    /** The Procedures node a JDBC tool shows under a schema: six columns, as the account's driver answers. */
    @Override
    public ResultSet getProcedures(final String catalog, final String schemaPattern,
                                   final String procedureNamePattern) throws SQLException {
        return JdbcMetadataQueries.procedures(connection, catalog, schemaPattern, procedureNamePattern);
    }

    /**
     * Not answered: the account's driver lists a return row then one row per parameter; a tool that asks shows no
     * parameters, and the procedure listing is what puts the node in the tree.
     */
    @Override
    public ResultSet getProcedureColumns(final String catalog, final String schemaPattern,
                                         final String procedureNamePattern, final String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureColumns not supported");
    }

    /** Every catalog's schemas, with TABLE_CATALOG telling them apart, as getSchemas(null, null) answers. */
    @Override
    public ResultSet getSchemas() throws SQLException {
        return getSchemas(null, null);
    }

    /** The overload browsing tools call per catalog to populate their tree. */
    @Override
    public ResultSet getSchemas(final String catalog, final String schemaPattern) throws SQLException {
        return JdbcMetadataQueries.schemas(connection, catalog, schemaPattern);
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        return connection.createStatement().executeQuery(
            "SELECT DATABASE_NAME AS TABLE_CAT FROM INFORMATION_SCHEMA.DATABASES ORDER BY DATABASE_NAME");
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        return connection.createStatement().executeQuery(
            "SELECT 'TABLE' AS TABLE_TYPE UNION ALL SELECT 'VIEW' ORDER BY TABLE_TYPE");
    }

    /** The eight rows the account's driver lists (see {@link JdbcMetadataQueries}). */
    @Override
    public ResultSet getTypeInfo() throws SQLException {
        return JdbcMetadataQueries.typeInfo(connection);
    }

    /** Seven columns and no row: there are no SQL user-defined types to list, and the account's driver says so. */
    @Override
    public ResultSet getUDTs(final String catalog, final String schemaPattern, final String typeNamePattern,
                             final int[] types) throws SQLException {
        return connection.createStatement().executeQuery("""
            SELECT CAST(NULL AS VARCHAR) AS TYPE_CAT, CAST(NULL AS VARCHAR) AS TYPE_SCHEM,
                   CAST(NULL AS VARCHAR) AS TYPE_NAME, CAST(NULL AS VARCHAR) AS CLASS_NAME,
                   CAST(NULL AS INTEGER) AS DATA_TYPE, CAST(NULL AS VARCHAR) AS REMARKS,
                   CAST(NULL AS INTEGER) AS BASE_TYPE WHERE 1=0""");
    }

    /** Scalar functions and table functions alike; FUNCTION_TYPE tells them apart. */
    @Override
    public ResultSet getFunctions(final String catalog, final String schemaPattern, final String functionNamePattern)
            throws SQLException {
        return JdbcMetadataQueries.functions(connection, catalog, schemaPattern, functionNamePattern);
    }

    /** Not answered; see {@link #getProcedureColumns}. */
    @Override
    public ResultSet getFunctionColumns(final String catalog, final String schemaPattern,
                                        final String functionNamePattern, final String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctionColumns not supported");
    }

    @Override
    public ResultSet getColumnPrivileges(final String catalog, final String schema, final String table,
                                         final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnPrivileges not supported");
    }

    @Override
    public ResultSet getTablePrivileges(final String catalog, final String schemaPattern,
                                        final String tableNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTablePrivileges not supported");
    }

    @Override
    public ResultSet getBestRowIdentifier(final String catalog, final String schema, final String table,
                                          final int scope, final boolean nullable) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBestRowIdentifier not supported");
    }

    @Override
    public ResultSet getVersionColumns(final String catalog, final String schema, final String table)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getVersionColumns not supported");
    }

    @Override
    public ResultSet getSuperTypes(final String catalog, final String schemaPattern, final String typeNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTypes not supported");
    }

    @Override
    public ResultSet getSuperTables(final String catalog, final String schemaPattern, final String tableNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTables not supported");
    }

    @Override
    public ResultSet getAttributes(final String catalog, final String schemaPattern, final String typeNamePattern,
                                   final String attributeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getAttributes not supported");
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfoProperties not supported");
    }

    @Override
    public ResultSet getPseudoColumns(final String catalog, final String schemaPattern, final String tableNamePattern,
                                      final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getPseudoColumns not supported");
    }

    // ---------------------------------------------------------------- wrapping

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException(getClass().getName() + " not unwrappable from " + iface.getName());
    }

    /** Not answered: the account's driver refuses the question, though it unwraps. */
    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}
