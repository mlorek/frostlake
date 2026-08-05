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

import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC DatabaseMetaData implementation for DirectConnection.
 * Provides metadata about tables, columns, indexes, and primary keys.
 */
public class DirectDatabaseMetaData implements java.sql.DatabaseMetaData {

    private final Connection connection;

    public DirectDatabaseMetaData(final Connection connection) {
        this.connection = connection;
    }

    @Override
    public java.sql.ResultSet getTables(final String catalog, final String schemaPattern, final String tableNamePattern, final String[] types) throws SQLException {
        return JdbcMetadataQueries.tables(connection, catalog, schemaPattern, tableNamePattern, types);
    }

    @Override
    public java.sql.ResultSet getColumns(final String catalog, final String schemaPattern, final String tableNamePattern, final String columnNamePattern) throws SQLException {
        return JdbcMetadataQueries.columns(connection, catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    @Override
    public java.sql.ResultSet getIndexInfo(final String catalog, final String schema, final String table, final boolean unique, final boolean approximate) throws SQLException {
        // Frostlake intentionally has no indexes, so index metadata is always empty. (The former query
        // referenced INFORMATION_SCHEMA.INDEXES / INDEX_COLUMNS, which do not exist, and always threw.)
        List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("TABLE_CAT", new StringType("VARCHAR", 256)));
        columns.add(new ResultSetColumn("TABLE_SCHEM", new StringType("VARCHAR", 256)));
        columns.add(new ResultSetColumn("TABLE_NAME", new StringType("VARCHAR", 256)));
        columns.add(new ResultSetColumn("NON_UNIQUE", new StringType("VARCHAR", 8)));
        columns.add(new ResultSetColumn("INDEX_QUALIFIER", new StringType("VARCHAR", 256)));
        columns.add(new ResultSetColumn("INDEX_NAME", new StringType("VARCHAR", 256)));
        columns.add(new ResultSetColumn("TYPE", new NumericType("INTEGER", 10, 0)));
        columns.add(new ResultSetColumn("ORDINAL_POSITION", new NumericType("INTEGER", 10, 0)));
        columns.add(new ResultSetColumn("COLUMN_NAME", new StringType("VARCHAR", 256)));
        columns.add(new ResultSetColumn("ASC_OR_DESC", new StringType("VARCHAR", 1)));
        columns.add(new ResultSetColumn("CARDINALITY", new NumericType("INTEGER", 10, 0)));
        columns.add(new ResultSetColumn("PAGES", new NumericType("INTEGER", 10, 0)));
        columns.add(new ResultSetColumn("FILTER_CONDITION", new StringType("VARCHAR", 256)));
        dev.frostlake.storage.ResultSet empty =
                new dev.frostlake.storage.ResultSet(columns, new ArrayList<>());
        return new DirectResultSet(connection.createStatement(), empty);
    }

    @Override
    public java.sql.ResultSet getPrimaryKeys(final String catalog, final String schema, final String table) throws SQLException {
        // Note: This is a simplified implementation — a full one would read table constraint metadata
        // rather than the IS_PRIMARY_KEY column flag.
        return JdbcMetadataQueries.primaryKeys(connection, catalog, schema, table);
    }

    @Override
    public java.sql.ResultSet getImportedKeys(final String catalog, final String schema, final String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getImportedKeys not supported");
    }

    @Override
    public java.sql.ResultSet getExportedKeys(final String catalog, final String schema, final String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getExportedKeys not supported");
    }

    @Override
    public java.sql.ResultSet getCrossReference(final String parentCatalog, final String parentSchema, final String parentTable,
                                      final String foreignCatalog, final String foreignSchema, final String foreignTable) throws SQLException {
        throw new SQLFeatureNotSupportedException("getCrossReference not supported");
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsResultSetType(final int type) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public boolean supportsResultSetConcurrency(final int type, final int concurrency) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY;
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
        return "Frostlake Direct JDBC Driver";
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
    public String getSQLKeywords() throws SQLException {
        return "VARIANT,ARRAY,OBJECT,FLATTEN,LATERAL";
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        return "ABS,CEIL,FLOOR,ROUND,SQRT,POWER,MOD,SIGN,TRUNC,EXP,LN,LOG";
    }

    @Override
    public String getStringFunctions() throws SQLException {
        return "CONCAT,UPPER,LOWER,SUBSTRING,LENGTH,TRIM,LTRIM,RTRIM,REVERSE,INITCAP,LPAD,RPAD,REPLACE";
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        return "CURRENT_TIMESTAMP,CURRENT_DATE,CURRENT_TIME,CURRENT_DATABASE,CURRENT_SCHEMA,CURRENT_USER";
    }

    @Override
    public String getURL() throws SQLException {
        return "jdbc:frostlake:direct";
    }

    @Override
    public String getUserName() throws SQLException {
        return "direct_user";
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
    public Connection getConnection() throws SQLException {
        return connection;
    }

    // Minimal implementations for remaining required methods

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return true;
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
    public String getTimeDateFunctions() throws SQLException {
        return "CURRENT_TIMESTAMP,CURRENT_DATE,CURRENT_TIME,DATEADD,DATEDIFF";
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
        return true;
    }

    @Override
    public boolean supportsConvert(final int fromType, final int toType) throws SQLException {
        return true;
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
        return false;
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
        return "schema";
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        return "procedure";
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        return "database";
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
        return true;
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
        return true;
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
        return true;
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
        return 0; // no limit
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxConnections() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        return 0; // no limit
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return true;
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return 256;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return 0; // no limit
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return 256;
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_READ_COMMITTED;
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

    @Override
    public java.sql.ResultSet getProcedures(final String catalog, final String schemaPattern, final String procedureNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedures not supported");
    }

    @Override
    public java.sql.ResultSet getProcedureColumns(final String catalog, final String schemaPattern, final String procedureNamePattern, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureColumns not supported");
    }

    @Override
    public java.sql.ResultSet getSchemas() throws SQLException {
        // Per the JDBC contract this is getSchemas(null, null): every catalog, with TABLE_CATALOG
        // telling them apart. Live Snowflake returns exactly the same rows for both calls.
        return getSchemas(null, null);
    }

    @Override
    public java.sql.ResultSet getCatalogs() throws SQLException {
        return connection.createStatement().executeQuery(
            "SELECT DATABASE_NAME AS TABLE_CAT FROM INFORMATION_SCHEMA.DATABASES ORDER BY DATABASE_NAME");
    }

    @Override
    public java.sql.ResultSet getTableTypes() throws SQLException {
        return connection.createStatement().executeQuery(
            "SELECT 'TABLE' AS TABLE_TYPE UNION ALL SELECT 'VIEW' ORDER BY TABLE_TYPE");
    }

    @Override
    public java.sql.ResultSet getColumnPrivileges(final String catalog, final String schema, final String table, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnPrivileges not supported");
    }

    @Override
    public java.sql.ResultSet getTablePrivileges(final String catalog, final String schemaPattern, final String tableNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTablePrivileges not supported");
    }

    @Override
    public java.sql.ResultSet getBestRowIdentifier(final String catalog, final String schema, final String table, final int scope, final boolean nullable) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBestRowIdentifier not supported");
    }

    @Override
    public java.sql.ResultSet getVersionColumns(final String catalog, final String schema, final String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getVersionColumns not supported");
    }

    @Override
    public java.sql.ResultSet getTypeInfo() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTypeInfo not supported");
    }

    @Override
    public java.sql.ResultSet getUDTs(final String catalog, final String schemaPattern, final String typeNamePattern, final int[] types) throws SQLException {
        throw new SQLFeatureNotSupportedException("getUDTs not supported");
    }

    @Override
    public boolean supportsResultSetHoldability(final int holdability) throws SQLException {
        return holdability == java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT;
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
        return true;
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
    public java.sql.ResultSet getSchemas(final String catalog, final String schemaPattern) throws SQLException {
        // Browsing tools populate their tree per catalog and call this overload, not the no-arg one;
        // throwing here is what left a freshly created database showing no schemas at all.
        return JdbcMetadataQueries.schemas(connection, catalog, schemaPattern);
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return true;
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    @Override
    public java.sql.ResultSet getClientInfoProperties() throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfoProperties not supported");
    }

    @Override
    public java.sql.ResultSet getFunctions(final String catalog, final String schemaPattern, final String functionNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctions not supported");
    }

    @Override
    public java.sql.ResultSet getFunctionColumns(final String catalog, final String schemaPattern, final String functionNamePattern, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctionColumns not supported");
    }

    @Override
    public java.sql.ResultSet getPseudoColumns(final String catalog, final String schemaPattern, final String tableNamePattern, final String columnNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getPseudoColumns not supported");
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
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
    public boolean supportsSavepoints() throws SQLException {
        return false;
    }

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
        return true;
    }

    @Override
    public java.sql.ResultSet getSuperTypes(final String catalog, final String schemaPattern, final String typeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTypes not supported");
    }

    @Override
    public java.sql.ResultSet getSuperTables(final String catalog, final String schemaPattern, final String tableNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTables not supported");
    }

    @Override
    public java.sql.ResultSet getAttributes(final String catalog, final String schemaPattern, final String typeNamePattern, final String attributeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getAttributes not supported");
    }
}
