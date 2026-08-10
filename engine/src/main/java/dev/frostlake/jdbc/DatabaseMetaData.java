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
 * Simplified JDBC DatabaseMetaData implementation for Frostlake SQL Engine
 * Only implements the most commonly used methods
 */
public class DatabaseMetaData implements java.sql.DatabaseMetaData {
    private final DatabaseConnection connection;
    private final HttpClient httpClient;

    public DatabaseMetaData(final DatabaseConnection connection, final HttpClient httpClient) {
        this.connection = connection;
        this.httpClient = httpClient;
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return true;
    }

    @Override
    public String getURL() throws SQLException {
        return "jdbc:frostlake://localhost:8080";
    }

    @Override
    public String getUserName() throws SQLException {
        return "anonymous";
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        return true;
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
    public String getDatabaseProductName() throws SQLException {
        return "Frostlake SQL Engine";
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return "1.0.0";
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
    public boolean usesLocalFiles() throws SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        return false;
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

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        return "\"";
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        return "VARIANT,ARRAY,OBJECT,FLATTEN,LATERAL";
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        return "ABS,CEIL,FLOOR,ROUND,SQRT";
    }

    @Override
    public String getStringFunctions() throws SQLException {
        return "CONCAT,LENGTH,LOWER,UPPER,SUBSTRING";
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        return "";
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        return "CURRENT_DATE,CURRENT_TIMESTAMP,NOW";
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        return "\\";
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        return "";
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
        return true;
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        return true;
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
        return true;
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        return true;
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
    public String getSchemaTerm() throws SQLException {
        return "SCHEMA";
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        return "PROCEDURE";
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        return "DATABASE";
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        return true;
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        return ".";
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return true;
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
        return true;
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return true;
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
        return true;
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
        return false;
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        return true;
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
        return true;
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return true;
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return 255;
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
    public int getMaxSchemaNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        return 0;
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return false;
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
    public int getMaxTableNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return 255;
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_READ_COMMITTED;
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsTransactionIsolationLevel(final int level) throws SQLException {
        return level == Connection.TRANSACTION_READ_COMMITTED;
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
        return false;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    // Placeholder implementations for methods requiring actual database queries
    @Override
    public ResultSet getProcedures(final String catalog, final String schemaPattern, final String procedureNamePattern) throws SQLException {
        // The Procedures node a JDBC tool shows under a schema. Same INFORMATION_SCHEMA-backed
        // query on both transports; six columns, matching what Snowflake's own driver returns.
        return JdbcMetadataQueries.procedures(connection, catalog, schemaPattern, procedureNamePattern);
    }

    @Override
    // Live answers this with 20 columns, a COLUMN_TYPE=5 return row then one row per parameter. Not implemented here: a tool that
    // asks simply shows no parameters, and the listing above is what puts the node in the tree.
    public ResultSet getProcedureColumns(final String catalog, final String schemaPattern, final String procedureNamePattern, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureColumns not implemented");
    }

    @Override
    public ResultSet getTables(final String catalog, final String schemaPattern, final String tableNamePattern, final String[] types) throws SQLException {
        // Run the same INFORMATION_SCHEMA query the in-process (DirectDatabaseMetaData) path uses, but over
        // the HTTP connection — so BI tools that read object metadata work over jdbc:frostlake://.
        return JdbcMetadataQueries.tables(connection, catalog, schemaPattern, tableNamePattern, types);
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        // Per the JDBC contract this is getSchemas(null, null): every catalog, with TABLE_CATALOG
        // telling them apart. Live Snowflake returns exactly the same rows for both calls.
        return getSchemas(null, null);
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

    @Override
    public ResultSet getColumns(final String catalog, final String schemaPattern, final String tableNamePattern, final String columnNamePattern) throws SQLException {
        return JdbcMetadataQueries.columns(connection, catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    @Override
    public ResultSet getColumnPrivileges(final String catalog, final String schema, final String table, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnPrivileges not implemented");
    }

    @Override
    public ResultSet getTablePrivileges(final String catalog, final String schemaPattern, final String tableNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTablePrivileges not implemented");
    }

    @Override
    public ResultSet getBestRowIdentifier(final String catalog, final String schema, final String table, final int scope, final boolean nullable) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBestRowIdentifier not implemented");
    }

    @Override
    public ResultSet getVersionColumns(final String catalog, final String schema, final String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getVersionColumns not implemented");
    }

    @Override
    public ResultSet getPrimaryKeys(final String catalog, final String schema, final String table) throws SQLException {
        return JdbcMetadataQueries.primaryKeys(connection, catalog, schema, table);
    }

    @Override
    public ResultSet getImportedKeys(final String catalog, final String schema, final String table) throws SQLException {
        // Every FK whose CHILD side is this table — what a tool draws as the table's outbound refs.
        return JdbcMetadataQueries.foreignKeys(connection, null, null, null, catalog, schema, table);
    }

    @Override
    public ResultSet getExportedKeys(final String catalog, final String schema, final String table) throws SQLException {
        // The mirror: every FK POINTING AT this table.
        return JdbcMetadataQueries.foreignKeys(connection, catalog, schema, table, null, null, null);
    }

    @Override
    public ResultSet getCrossReference(final String parentCatalog, final String parentSchema, final String parentTable, final String foreignCatalog, final String foreignSchema, final String foreignTable) throws SQLException {
        // Both ends pinned.
        return JdbcMetadataQueries.foreignKeys(connection, parentCatalog, parentSchema, parentTable,
            foreignCatalog, foreignSchema, foreignTable);
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        // The eight rows live returns, verbatim — see JdbcMetadataQueries.typeInfo.
        return JdbcMetadataQueries.typeInfo(connection);
    }

    @Override
    public ResultSet getIndexInfo(final String catalog, final String schema, final String table, final boolean unique, final boolean approximate) throws SQLException {
        // Frostlake intentionally has no indexes, so this is always empty. Yield a zero-row result with the
        // JDBC index columns (a WHERE 1=0 query over a real system view) rather than referencing the
        // non-existent INFORMATION_SCHEMA.INDEXES.
        return connection.createStatement().executeQuery(EMPTY_INDEX_INFO_SQL);
    }

    private static final String EMPTY_INDEX_INFO_SQL =
        "SELECT CAST(NULL AS VARCHAR) AS TABLE_CAT, CAST(NULL AS VARCHAR) AS TABLE_SCHEM, "
        + "CAST(NULL AS VARCHAR) AS TABLE_NAME, CAST(NULL AS VARCHAR) AS NON_UNIQUE, "
        + "CAST(NULL AS VARCHAR) AS INDEX_QUALIFIER, CAST(NULL AS VARCHAR) AS INDEX_NAME, "
        + "CAST(NULL AS INTEGER) AS TYPE, CAST(NULL AS INTEGER) AS ORDINAL_POSITION, "
        + "CAST(NULL AS VARCHAR) AS COLUMN_NAME, CAST(NULL AS VARCHAR) AS ASC_OR_DESC, "
        + "CAST(NULL AS INTEGER) AS CARDINALITY, CAST(NULL AS INTEGER) AS PAGES, "
        + "CAST(NULL AS VARCHAR) AS FILTER_CONDITION FROM INFORMATION_SCHEMA.COLUMNS WHERE 1=0";

    @Override
    public boolean supportsResultSetType(final int type) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY || type == ResultSet.TYPE_SCROLL_INSENSITIVE;
    }

    @Override
    public boolean supportsResultSetConcurrency(final int type, final int concurrency) throws SQLException {
        return concurrency == ResultSet.CONCUR_READ_ONLY;
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

    @Override
    public ResultSet getUDTs(final String catalog, final String schemaPattern, final String typeNamePattern, final int[] types) throws SQLException {
        // Live answers this with seven columns and no rows — Snowflake has no SQL user-defined types
        // to list. Answering empty rather than throwing is what its driver does.
        return connection.createStatement().executeQuery(
            "SELECT CAST(NULL AS VARCHAR) AS TYPE_CAT, CAST(NULL AS VARCHAR) AS TYPE_SCHEM, "
            + "CAST(NULL AS VARCHAR) AS TYPE_NAME, CAST(NULL AS VARCHAR) AS CLASS_NAME, "
            + "CAST(NULL AS INTEGER) AS DATA_TYPE, CAST(NULL AS VARCHAR) AS REMARKS, "
            + "CAST(NULL AS INTEGER) AS BASE_TYPE WHERE 1=0");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        return false;
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
    public ResultSet getSuperTypes(final String catalog, final String schemaPattern, final String typeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTypes not implemented");
    }

    @Override
    public ResultSet getSuperTables(final String catalog, final String schemaPattern, final String tableNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTables not implemented");
    }

    @Override
    public ResultSet getAttributes(final String catalog, final String schemaPattern, final String typeNamePattern, final String attributeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getAttributes not implemented");
    }

    @Override
    public boolean supportsResultSetHoldability(final int holdability) throws SQLException {
        return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
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
    public int getJDBCMajorVersion() throws SQLException {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        return 2;
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

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        return RowIdLifetime.ROWID_UNSUPPORTED;
    }

    @Override
    public ResultSet getSchemas(final String catalog, final String schemaPattern) throws SQLException {
        // Browsing tools populate their tree per catalog and call this overload, not the no-arg one;
        // throwing here is what left a freshly created database showing no schemas at all.
        return JdbcMetadataQueries.schemas(connection, catalog, schemaPattern);
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return false;
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfoProperties not implemented");
    }

    @Override
    public ResultSet getFunctions(final String catalog, final String schemaPattern, final String functionNamePattern) throws SQLException {
        // Scalar UDFs and UDTFs alike; FUNCTION_TYPE tells them apart.
        return JdbcMetadataQueries.functions(connection, catalog, schemaPattern, functionNamePattern);
    }

    @Override
    // Live answers this with 17 columns, a COLUMN_TYPE=4 return row then one row per parameter. Not implemented here: a tool that
    // asks simply shows no parameters, and the listing above is what puts the node in the tree.
    public ResultSet getFunctionColumns(final String catalog, final String schemaPattern, final String functionNamePattern, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctionColumns not implemented");
    }

    @Override
    public ResultSet getPseudoColumns(final String catalog, final String schemaPattern, final String tableNamePattern, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getPseudoColumns not implemented");
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }
}
