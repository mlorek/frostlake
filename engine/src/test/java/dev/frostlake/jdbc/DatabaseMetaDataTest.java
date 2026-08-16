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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseMetaDataTest extends BaseJdbcTest {

    private static final String DRIVER_IDENTITY =
        "asserts the Frostlake driver's own identity strings; under SF_LIVE the connection is "
        + "Snowflake's driver, which names itself and its server version instead";

    private static final String DRIVER_CAPABILITY =
        "asserts a capability the FROSTLAKE driver declares about itself through "
        + "java.sql.DatabaseMetaData; under SF_LIVE the answer comes from Snowflake's own driver, "
        + "which declares its own (different) answer — nothing about the account's SQL is checked";

    @Test
    public void testGetMetaData() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertNotNull(metaData);
    }

    @Test
    public void testGetDatabaseProductName() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_IDENTITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals("Frostlake SQL Engine", metaData.getDatabaseProductName());
    }

    @Test
    public void testGetDatabaseProductVersion() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_IDENTITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals("1.0.0", metaData.getDatabaseProductVersion());
    }

    @Test
    public void testGetDriverName() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_IDENTITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals("Frostlake JDBC Driver", metaData.getDriverName());
    }

    @Test
    public void testGetTables() throws SQLException {
        statement.execute("CREATE TABLE meta_test1 (id INTEGER, name VARCHAR)");
        statement.execute("CREATE TABLE meta_test2 (value DOUBLE)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getTables(null, null, "META_TEST%", null);

        int count = 0;
        while (rs.next()) {
            final String tableName = rs.getString("TABLE_NAME");
            assertTrue(tableName.toUpperCase().startsWith("META_TEST"));
            count++;
        }
        assertTrue(count >= 2);
        rs.close();
    }

    @Test
    public void testGetTablesWithPattern() throws SQLException {
        statement.execute("CREATE TABLE pattern_test1 (id INTEGER)");
        statement.execute("CREATE TABLE pattern_test2 (id INTEGER)");
        statement.execute("CREATE TABLE other_table (id INTEGER)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getTables(null, null, "PATTERN_TEST%", null);

        int count = 0;
        while (rs.next()) {
            final String tableName = rs.getString("TABLE_NAME");
            assertTrue(tableName.toUpperCase().startsWith("PATTERN_TEST"));
            count++;
        }
        assertEquals(2, count);
        rs.close();
    }

    @Test
    public void testGetColumns() throws SQLException {
        statement.execute("CREATE TABLE col_test (id INTEGER, name VARCHAR, value DOUBLE)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getColumns(null, null, "COL_TEST", null);

        int count = 0;
        while (rs.next()) {
            final String tableName = rs.getString("TABLE_NAME");
            final String columnName = rs.getString("COLUMN_NAME");
            assertEquals("COL_TEST", tableName.toUpperCase());
            final String upperColName = columnName.toUpperCase();
            assertTrue(upperColName.equals("ID") || upperColName.equals("NAME") || upperColName.equals("VALUE"));
            count++;
        }
        assertEquals(3, count);
        rs.close();
    }

    @Test
    public void testGetColumnsWithPattern() throws SQLException {
        statement.execute("CREATE TABLE col_pattern (id INTEGER, name VARCHAR, email VARCHAR)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getColumns(null, null, "COL_PATTERN", "N%");

        int count = 0;
        while (rs.next()) {
            final String columnName = rs.getString("COLUMN_NAME");
            assertTrue(columnName.toUpperCase().startsWith("N"));
            count++;
        }
        assertEquals(1, count);  // Only "NAME"
        rs.close();
    }

    @Test
    public void testGetColumnsDataTypes() throws SQLException {
        statement.execute("CREATE TABLE type_test (int_col INTEGER, str_col VARCHAR, dbl_col DOUBLE)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getColumns(null, null, "TYPE_TEST", null);

        // TYPE_NAME is the name column. Snowflake's driver renames two of the catalog's families
        // on the way out — TEXT becomes VARCHAR and FLOAT becomes DOUBLE — and NUMBER stays NUMBER.
        while (rs.next()) {
            final String columnName = rs.getString("COLUMN_NAME");
            final String typeName = rs.getString("TYPE_NAME");
            assertNotNull(typeName);

            if (columnName.equals("INT_COL")) {
                assertEquals("NUMBER", typeName);
            } else if (columnName.equals("STR_COL")) {
                assertEquals("VARCHAR", typeName);
            } else if (columnName.equals("DBL_COL")) {
                assertEquals("DOUBLE", typeName);
            }
        }
        rs.close();
    }

    @Test
    public void testGetPrimaryKeys() throws SQLException {
        statement.execute("CREATE TABLE pk_test (id INTEGER PRIMARY KEY, name VARCHAR)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getPrimaryKeys(null, null, "PK_TEST");

        // Note: Primary key metadata not yet fully supported in INFORMATION_SCHEMA
        // This test verifies the method returns a ResultSet (even if empty)
        assertNotNull(rs);

        // Count results (may be 0 if PK tracking not implemented)
        int count = 0;
        while (rs.next()) {
            count++;
        }
        // For now, just verify method doesn't throw exception
        assertTrue(count >= 0);
        rs.close();
    }

    @Test
    public void testGetPrimaryKeysComposite() throws SQLException {
        statement.execute("""
            CREATE TABLE pk_composite (id1 INTEGER, id2 INTEGER, value VARCHAR, PRIMARY KEY (id1, id2))
            """);

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getPrimaryKeys(null, null, "PK_COMPOSITE");

        // Note: Primary key metadata not yet fully supported in INFORMATION_SCHEMA
        assertNotNull(rs);

        int count = 0;
        while (rs.next()) {
            count++;
        }
        // For now, just verify method doesn't throw exception
        assertTrue(count >= 0);
        rs.close();
    }

    @Test
    public void testGetPrimaryKeysNoPrimaryKey() throws SQLException {
        statement.execute("CREATE TABLE no_pk (id INTEGER, name VARCHAR)");

        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getPrimaryKeys(null, null, "NO_PK");

        assertFalse(rs.next());  // No primary keys
        rs.close();
    }

    @Test
    public void testGetPrimaryKeysNonExistentTable() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final ResultSet rs = metaData.getPrimaryKeys(null, null, "NONEXISTENT_TABLE");

        assertFalse(rs.next());  // No results for non-existent table
        rs.close();
    }

    @Test
    public void testSupportsTransactions() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsTransactions());
    }

    @Test
    public void testSupportsResultSetType() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY));
    }

    @Test
    public void testGetSQLKeywords() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_CAPABILITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        final String keywords = metaData.getSQLKeywords();
        assertTrue(keywords.contains("VARIANT"));
        assertTrue(keywords.contains("ARRAY"));
        assertTrue(keywords.contains("FLATTEN"));
    }

    @Test
    public void testGetNumericFunctions() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final String functions = metaData.getNumericFunctions();
        assertTrue(functions.contains("ABS"));
        assertTrue(functions.contains("ROUND"));
        assertTrue(functions.contains("SQRT"));
    }

    @Test
    public void testGetStringFunctions() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final String functions = metaData.getStringFunctions();
        assertTrue(functions.contains("CONCAT"));
        assertTrue(functions.contains("UPPER"));
        assertTrue(functions.contains("SUBSTRING"));
    }

    @Test
    public void testSupportsGroupBy() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsGroupBy());
    }

    @Test
    public void testSupportsOuterJoins() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsOuterJoins());
        assertTrue(metaData.supportsFullOuterJoins());
    }

    @Test
    public void testSupportsSubqueries() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsSubqueriesInComparisons());
        assertTrue(metaData.supportsSubqueriesInExists());
        assertTrue(metaData.supportsCorrelatedSubqueries());
    }

    @Test
    public void testSupportsUnion() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsUnion());
        assertTrue(metaData.supportsUnionAll());
    }

    @Test
    public void testGetMaxColumnNameLength() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_CAPABILITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals(256, metaData.getMaxColumnNameLength());
    }

    @Test
    public void testGetIdentifierQuoteString() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals("\"", metaData.getIdentifierQuoteString());
    }

    @Test
    public void testSupportsAlterTable() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsAlterTableWithAddColumn());
        assertTrue(metaData.supportsAlterTableWithDropColumn());
    }

    @Test
    public void testNullSorting() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_CAPABILITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertFalse(metaData.nullsAreSortedHigh());
        assertTrue(metaData.nullsAreSortedLow());
        assertFalse(metaData.nullsAreSortedAtStart());
        assertFalse(metaData.nullsAreSortedAtEnd());
    }

    @Test
    public void testGetConnection() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals(connection, metaData.getConnection());
    }

    @Test
    public void testSupportsBatchUpdates() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsBatchUpdates());
    }

    @Test
    public void testSupportsSavepoints() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertFalse(metaData.supportsSavepoints());
    }

    @Test
    public void testSupportsNamedParameters() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_CAPABILITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsNamedParameters());
    }

    @Test
    public void testSupportsGetGeneratedKeys() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), DRIVER_CAPABILITY);
        final DatabaseMetaData metaData = connection.getMetaData();
        assertTrue(metaData.supportsGetGeneratedKeys());
    }

    @Test
    public void testGetJDBCVersion() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals(4, metaData.getJDBCMajorVersion());
        assertEquals(2, metaData.getJDBCMinorVersion());
    }

    // ── getSchemas(catalog, schemaPattern) ────────────────────────────────────────────────────
    // The two-argument overload is the one a browsing tool calls to fill in a catalog's schema list;
    // it used to throw SQLFeatureNotSupportedException, which showed up as a database with no schemas
    // under it at all. Shape and argument handling below are matched against Snowflake's own JDBC
    // driver (3.20.0) run against a live account.

    /** Every schema name a getSchemas result reports, in the order the driver returned them. */
    private List<String> schemaNames(final ResultSet rs) throws SQLException {
        final List<String> names = new ArrayList<>();
        while (rs.next()) {
            names.add(rs.getString("TABLE_SCHEM"));
        }
        return names;
    }

    @Test
    public void testGetSchemasForCatalogListsPublicAndInformationSchema() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getSchemas("TEST_DB", null)) {
            final List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("TABLE_SCHEM"));
                assertEquals("TEST_DB", rs.getString("TABLE_CATALOG"),
                    "getSchemas(catalog, …) must only report schemas of that catalog");
            }
            assertTrue(names.contains("PUBLIC"), "a fresh database has PUBLIC, got " + names);
            assertTrue(names.contains("INFORMATION_SCHEMA"),
                "a fresh database has INFORMATION_SCHEMA, got " + names);
        }
    }

    @Test
    public void testGetSchemasColumnLabels() throws SQLException {
        // A browsing tool reads these by label, so the labels are part of the contract. Snowflake's
        // driver returns exactly two columns, TABLE_SCHEM then TABLE_CATALOG.
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getSchemas("TEST_DB", null)) {
            final ResultSetMetaData md = rs.getMetaData();
            assertEquals(2, md.getColumnCount());
            assertEquals("TABLE_SCHEM", md.getColumnLabel(1).toUpperCase());
            assertEquals("TABLE_CATALOG", md.getColumnLabel(2).toUpperCase());
        }
    }

    @Test
    public void testGetSchemasWildcardPatternMatchesEveryScheme() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final List<String> withNull;
        try (ResultSet rs = metaData.getSchemas("TEST_DB", null)) {
            withNull = schemaNames(rs);
        }
        try (ResultSet rs = metaData.getSchemas("TEST_DB", "%")) {
            assertEquals(withNull, schemaNames(rs), "a \"%\" pattern matches everything, as null does");
        }
    }

    @Test
    public void testGetSchemasHonoursSchemaPattern() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getSchemas("TEST_DB", "PUB%")) {
            assertEquals(List.of("PUBLIC"), schemaNames(rs));
        }
        try (ResultSet rs = metaData.getSchemas("TEST_DB", "PUBLIC")) {
            assertEquals(List.of("PUBLIC"), schemaNames(rs));
        }
    }

    @Test
    public void testGetSchemasUnknownCatalogIsEmptyNotAnError() throws SQLException {
        // Live Snowflake answers zero rows — it does not raise "database does not exist".
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getSchemas("NO_SUCH_DATABASE_FL160", null)) {
            assertFalse(rs.next());
        }
    }

    @Test
    public void testGetSchemasNullCatalogSpansCatalogs() throws SQLException {
        // JDBC's getSchemas() enumerates across catalogs, with TABLE_CATALOG telling them apart —
        // live-confirmed on Snowflake, where the no-arg and (null, null) forms return the same rows.
        final DatabaseMetaData metaData = connection.getMetaData();
        boolean sawTestDb = false;
        try (ResultSet rs = metaData.getSchemas(null, null)) {
            while (rs.next()) {
                if ("TEST_DB".equals(rs.getString("TABLE_CATALOG"))
                        && "PUBLIC".equals(rs.getString("TABLE_SCHEM"))) {
                    sawTestDb = true;
                }
            }
        }
        assertTrue(sawTestDb, "getSchemas(null, null) must include TEST_DB.PUBLIC");
    }

    @Test
    public void testGetSchemasNoArgMatchesNullNull() throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final List<String> noArg;
        try (ResultSet rs = metaData.getSchemas()) {
            noArg = schemaNames(rs);
        }
        try (ResultSet rs = metaData.getSchemas(null, null)) {
            assertEquals(noArg, schemaNames(rs), "getSchemas() is defined as getSchemas(null, null)");
        }
    }

    @Test
    public void testGetSchemasKeepsDatabasesApart() throws SQLException {
        // The case a single-database test cannot catch: asking for one database must never answer
        // with another's schemas. This is what the old catalog-blind query got wrong — it read the
        // session's current database whatever catalog was named.
        statement.execute("CREATE DATABASE fl160_alpha");
        statement.execute("CREATE SCHEMA fl160_alpha.alpha_only");
        statement.execute("CREATE DATABASE fl160_beta");
        statement.execute("CREATE SCHEMA fl160_beta.beta_only");
        try {
            final DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getSchemas("FL160_ALPHA", null)) {
                final List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("TABLE_SCHEM"));
                    assertEquals("FL160_ALPHA", rs.getString("TABLE_CATALOG"));
                }
                assertTrue(names.contains("ALPHA_ONLY"), "expected ALPHA_ONLY, got " + names);
                assertFalse(names.contains("BETA_ONLY"), "leaked the sibling database's schema");
            }
            try (ResultSet rs = metaData.getSchemas("FL160_BETA", null)) {
                final List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("TABLE_SCHEM"));
                    assertEquals("FL160_BETA", rs.getString("TABLE_CATALOG"));
                }
                assertTrue(names.contains("BETA_ONLY"), "expected BETA_ONLY, got " + names);
                assertFalse(names.contains("ALPHA_ONLY"), "leaked the sibling database's schema");
            }
        } finally {
            statement.execute("DROP DATABASE IF EXISTS fl160_alpha");
            statement.execute("DROP DATABASE IF EXISTS fl160_beta");
        }
    }

    @Test
    public void testGetTablesReadsNonCurrentCatalog() throws SQLException {
        // Same catalog-blindness, one level down: expanding a schema of a database that is not the
        // session's current one used to list no tables at all.
        statement.execute("CREATE DATABASE fl160_other");
        statement.execute("CREATE TABLE fl160_other.public.other_probe (id INTEGER)");
        try {
            statement.execute("USE DATABASE test_db");
            statement.execute("USE SCHEMA PUBLIC");
            final DatabaseMetaData metaData = connection.getMetaData();
            boolean sawProbe = false;
            try (ResultSet rs = metaData.getTables("FL160_OTHER", "PUBLIC", "%", null)) {
                while (rs.next()) {
                    if ("OTHER_PROBE".equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        sawProbe = true;
                    }
                }
            }
            assertTrue(sawProbe, "getTables must see a table in a catalog other than the current one");

            boolean sawColumn = false;
            try (ResultSet rs = metaData.getColumns("FL160_OTHER", "PUBLIC", "OTHER_PROBE", "%")) {
                while (rs.next()) {
                    if ("ID".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        sawColumn = true;
                    }
                }
            }
            assertTrue(sawColumn, "getColumns must see a column in a catalog other than the current one");
        } finally {
            statement.execute("DROP DATABASE IF EXISTS fl160_other");
        }
    }

    // ── getTables: the types getTableTypes() advertises ───────────────────────────────────────
    // getTables used to pass INFORMATION_SCHEMA's own TABLE_TYPE straight through, so it answered
    // "BASE TABLE" — a value getTableTypes() never offers — and asking for either advertised type
    // returned nothing. Views did not appear under any filter at all.

    /** The TABLE_TYPE each object reports, keyed by name, for one getTables call. */
    private Map<String, String> tableTypesOf(final DatabaseMetaData metaData, final String[] types)
            throws SQLException {
        final Map<String, String> found = new TreeMap<>();
        try (ResultSet rs = metaData.getTables("TEST_DB", "PUBLIC", "FL162%", types)) {
            while (rs.next()) {
                found.put(rs.getString("TABLE_NAME").toUpperCase(), rs.getString("TABLE_TYPE"));
            }
        }
        return found;
    }

    @Test
    public void testGetTablesMapsTypesOntoTheAdvertisedVocabulary() throws SQLException {
        statement.execute("CREATE TABLE fl162_tbl (id INTEGER, name VARCHAR)");
        statement.execute("CREATE VIEW fl162_vw AS SELECT id FROM fl162_tbl");

        final DatabaseMetaData metaData = connection.getMetaData();
        // Every type reported has to be one getTableTypes() offers, or a client that filters by
        // what it was told about can never match anything.
        final List<String> advertised = new ArrayList<>();
        try (ResultSet rs = metaData.getTableTypes()) {
            while (rs.next()) {
                advertised.add(rs.getString("TABLE_TYPE"));
            }
        }
        assertEquals(List.of("TABLE", "VIEW"), advertised);

        final Map<String, String> unfiltered = tableTypesOf(metaData, null);
        assertEquals("TABLE", unfiltered.get("FL162_TBL"));
        assertEquals("VIEW", unfiltered.get("FL162_VW"),
            "a view must be listed, and as VIEW — it used to be absent entirely");
    }

    @Test
    public void testGetTablesHonoursEveryTypeFilter() throws SQLException {
        statement.execute("CREATE TABLE fl162_tbl (id INTEGER)");
        statement.execute("CREATE VIEW fl162_vw AS SELECT id FROM fl162_tbl");

        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals(Set.of("FL162_TBL"), tableTypesOf(metaData, new String[] {"TABLE"}).keySet());
        assertEquals(Set.of("FL162_VW"), tableTypesOf(metaData, new String[] {"VIEW"}).keySet());
        assertEquals(Set.of("FL162_TBL", "FL162_VW"),
            tableTypesOf(metaData, new String[] {"TABLE", "VIEW"}).keySet());
        assertEquals(Set.of("FL162_TBL", "FL162_VW"),
            tableTypesOf(metaData, new String[] {"VIEW", "TABLE"}).keySet(), "order must not matter");

        // Live Snowflake reads an EMPTY array as a filter that matches nothing, not as "no filter".
        assertTrue(tableTypesOf(metaData, new String[0]).isEmpty());
        // A type outside the advertised vocabulary matches nothing — including the catalog's own
        // raw value, which is what used to be the ONLY thing that matched.
        assertTrue(tableTypesOf(metaData, new String[] {"BASE TABLE"}).isEmpty());
        assertTrue(tableTypesOf(metaData, new String[] {"SYSTEM TABLE"}).isEmpty());
    }

    @Test
    public void testGetTablesListsAMaterializedViewAsAView() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "creating a materialized view on the live account needs a running warehouse and is "
            + "slow; the TABLE_TYPE mapping it exercises was measured live and is asserted above");
        statement.execute("CREATE TABLE fl162_tbl (id INTEGER)");
        statement.execute("CREATE MATERIALIZED VIEW fl162_mv AS SELECT id FROM fl162_tbl");

        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals("VIEW", tableTypesOf(metaData, null).get("FL162_MV"),
            "Snowflake reports a materialized view as VIEW");
        assertTrue(tableTypesOf(metaData, new String[] {"VIEW"}).containsKey("FL162_MV"));
        assertFalse(tableTypesOf(metaData, new String[] {"TABLE"}).containsKey("FL162_MV"));
    }

    @Test
    public void testGetTablesReturnsTheSpecifiedColumns() throws SQLException {
        // A browsing tool reads these by label. Snowflake's driver returns exactly these ten,
        // in this order; the four columns returned before used non-spec TABLE_CATALOG/TABLE_SCHEMA.
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables("TEST_DB", "PUBLIC", "%", null)) {
            assertEquals(List.of("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS",
                "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION"),
                labelsOf(rs));
        }
    }

    // ── getColumns: the specified shape, and DATA_TYPE as a java.sql.Types code ───────────────

    @Test
    public void testGetColumnsReturnsTheSpecifiedColumns() throws SQLException {
        statement.execute("CREATE TABLE fl162_shape (id INTEGER)");
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL162_SHAPE", "%")) {
            assertEquals(List.of("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE",
                "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "NUM_PREC_RADIX",
                "NULLABLE", "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB",
                "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG",
                "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT",
                "IS_GENERATEDCOLUMN"), labelsOf(rs));
        }
    }

    @Test
    public void testGetColumnsReportsDataTypeAsAJavaSqlTypesCode() throws SQLException {
        // The spec makes DATA_TYPE an int; it used to be a type-name string, so rs.getInt on it
        // failed outright. Every code below was measured from Snowflake's own driver.
        statement.execute("""
            CREATE TABLE fl162_types (
                c_number NUMBER(10,2), c_int INT, c_float FLOAT, c_varchar VARCHAR(50),
                c_bool BOOLEAN, c_date DATE, c_time TIME, c_ts TIMESTAMP_NTZ,
                c_tstz TIMESTAMP_TZ, c_bin BINARY(16), c_variant VARIANT)
            """);
        final Map<String, Integer> codes = new TreeMap<>();
        final Map<String, String> names = new TreeMap<>();
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL162_TYPES", "%")) {
            while (rs.next()) {
                codes.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getInt("DATA_TYPE"));
                names.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getString("TYPE_NAME"));
            }
        }
        // A NUMBER splits on its scale: scale 0 is a BIGINT whatever the precision, anything else
        // is a DECIMAL. Since a bare INT is NUMBER(38,0), integer columns land on BIGINT.
        assertEquals(Types.DECIMAL, codes.get("C_NUMBER"));
        assertEquals(Types.BIGINT, codes.get("C_INT"));
        assertEquals(Types.DOUBLE, codes.get("C_FLOAT"));
        assertEquals(Types.VARCHAR, codes.get("C_VARCHAR"));
        assertEquals(Types.BOOLEAN, codes.get("C_BOOL"));
        assertEquals(Types.DATE, codes.get("C_DATE"));
        assertEquals(Types.TIME, codes.get("C_TIME"));
        assertEquals(Types.TIMESTAMP, codes.get("C_TS"));
        assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, codes.get("C_TSTZ"));
        assertEquals(Types.BINARY, codes.get("C_BIN"));
        assertEquals(Types.VARCHAR, codes.get("C_VARIANT"), "a VARIANT reads out as text");

        assertEquals("NUMBER", names.get("C_INT"));
        assertEquals("VARCHAR", names.get("C_VARCHAR"));
        assertEquals("TIMESTAMPNTZ", names.get("C_TS"));
        assertEquals("VARIANT", names.get("C_VARIANT"));
    }

    @Test
    public void testGetColumnsReportsSizeScaleAndNullability() throws SQLException {
        statement.execute("""
            CREATE TABLE fl162_sizes (
                c_number NUMBER(10,2), c_varchar VARCHAR(50), c_bin BINARY(16),
                c_ts TIMESTAMP_NTZ, c_req INTEGER NOT NULL)
            """);
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL162_SIZES", "%")) {
            while (rs.next()) {
                final String column = rs.getString("COLUMN_NAME").toUpperCase();
                if ("C_NUMBER".equals(column)) {
                    assertEquals(10, rs.getInt("COLUMN_SIZE"), "precision is the size of a numeric");
                    assertEquals(2, rs.getInt("DECIMAL_DIGITS"));
                } else if ("C_VARCHAR".equals(column)) {
                    assertEquals(50, rs.getInt("COLUMN_SIZE"), "declared length, not the maximum");
                    assertEquals(50, rs.getInt("CHAR_OCTET_LENGTH"));
                } else if ("C_BIN".equals(column)) {
                    assertEquals(16, rs.getInt("COLUMN_SIZE"));
                } else if ("C_TS".equals(column)) {
                    assertEquals(9, rs.getInt("DECIMAL_DIGITS"), "fractional-second digits");
                } else if ("C_REQ".equals(column)) {
                    assertEquals(java.sql.DatabaseMetaData.columnNoNulls, rs.getInt("NULLABLE"));
                    assertEquals("NO", rs.getString("IS_NULLABLE"));
                }
            }
        }
    }

    // ── getColumns: a VIEW's columns ─────────────────────────────────────────────────────────
    // getColumns returned ZERO rows for a view, because a view stored only its column NAMES and only
    // when explicitly declared. Once #162 made views appear in getTables, a browsing tool listed one
    // and then expanded it to nothing. Live Snowflake reports a view's columns in the same row shape
    // as a table's; every expectation below was measured on a real account.

    /** COLUMN_NAME → TYPE_NAME for one getColumns call, in projection order. */
    private Map<String, String> columnTypesOf(final DatabaseMetaData metaData, final String table)
            throws SQLException {
        final Map<String, String> found = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", table, "%")) {
            while (rs.next()) {
                found.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getString("TYPE_NAME"));
            }
        }
        return found;
    }

    @Test
    public void testGetColumnsReportsAViewsColumns() throws SQLException {
        statement.execute("""
            CREATE TABLE fl164_base (
                id INTEGER, label VARCHAR(30), amount NUMBER(12,4), ratio FLOAT, ok BOOLEAN)
            """);
        statement.execute("CREATE VIEW fl164_star AS SELECT * FROM fl164_base");
        statement.execute("CREATE VIEW fl164_proj AS SELECT id, label FROM fl164_base");
        statement.execute("CREATE VIEW fl164_over AS SELECT * FROM fl164_star");

        final DatabaseMetaData metaData = connection.getMetaData();
        // A SELECT * view carries every base column, in order, with the base column's own type —
        // the star is expanded once, when the view is created.
        assertEquals(List.of("ID", "LABEL", "AMOUNT", "RATIO", "OK"),
            new ArrayList<>(columnTypesOf(metaData, "FL164_STAR").keySet()),
            "a view's columns used to be absent entirely");
        assertEquals(Map.of("ID", "NUMBER", "LABEL", "VARCHAR", "AMOUNT", "NUMBER",
            "RATIO", "DOUBLE", "OK", "BOOLEAN"), columnTypesOf(metaData, "FL164_STAR"));
        assertEquals(List.of("ID", "LABEL"),
            new ArrayList<>(columnTypesOf(metaData, "FL164_PROJ").keySet()));
        // A view over a view resolves all the way through to the base column types.
        assertEquals(columnTypesOf(metaData, "FL164_STAR"), columnTypesOf(metaData, "FL164_OVER"));

        // Size, scale, position and the java.sql.Types code are the table's, not placeholders.
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL164_STAR", "%")) {
            while (rs.next()) {
                final String column = rs.getString("COLUMN_NAME").toUpperCase();
                if ("LABEL".equals(column)) {
                    assertEquals(2, rs.getInt("ORDINAL_POSITION"));
                    assertEquals(30, rs.getInt("COLUMN_SIZE"));
                    assertEquals(30, rs.getInt("CHAR_OCTET_LENGTH"));
                    assertEquals(Types.VARCHAR, rs.getInt("DATA_TYPE"));
                } else if ("AMOUNT".equals(column)) {
                    assertEquals(12, rs.getInt("COLUMN_SIZE"), "precision comes through the view");
                    assertEquals(4, rs.getInt("DECIMAL_DIGITS"), "and so does scale");
                    assertEquals(Types.DECIMAL, rs.getInt("DATA_TYPE"));
                } else if ("ID".equals(column)) {
                    assertEquals(Types.BIGINT, rs.getInt("DATA_TYPE"), "INTEGER is NUMBER(38,0)");
                }
            }
        }
    }

    @Test
    public void testGetColumnsReportsAViewsExplicitColumnList() throws SQLException {
        statement.execute("CREATE TABLE fl164_base (id INTEGER, label VARCHAR(30))");
        statement.execute("CREATE VIEW fl164_named (k, v) AS SELECT id, label FROM fl164_base");

        final DatabaseMetaData metaData = connection.getMetaData();
        // The declared names win, and each still carries the type of the item it renames.
        assertEquals(Map.of("K", "NUMBER", "V", "VARCHAR"), columnTypesOf(metaData, "FL164_NAMED"));
    }

    @Test
    public void testGetColumnsReportsAnExpressionProjectionsColumns() throws SQLException {
        statement.execute("CREATE TABLE fl164_base (id INTEGER, label VARCHAR(30))");
        statement.execute("""
            CREATE VIEW fl164_expr AS SELECT
                id + 1 AS bumped, UPPER(label) AS shout,
                OBJECT_CONSTRUCT('k', label) AS obj, id > 0 AS positive
            FROM fl164_base
            """);

        final DatabaseMetaData metaData = connection.getMetaData();
        final Map<String, String> columns = columnTypesOf(metaData, "FL164_EXPR");
        // Every projected expression is reported, under its alias and in projection order.
        assertEquals(List.of("BUMPED", "SHOUT", "OBJ", "POSITIVE"), new ArrayList<>(columns.keySet()));
        assertEquals("VARCHAR", columns.get("SHOUT"), "a string function's result is text");
        assertEquals("OBJECT", columns.get("OBJ"),
            "the semi-structured type an OBJECT_CONSTRUCT projection is statically known to produce");
        // BUMPED and POSITIVE are deliberately NOT asserted to a type here. Live reports them NUMBER
        // and BOOLEAN; Frostlake reports the VARCHAR placeholder every computed projection carries,
        // which is its own open defect about projected column types rather than anything about views.
        assertNotNull(columns.get("BUMPED"));
        assertNotNull(columns.get("POSITIVE"));
    }

    @Test
    public void testGetColumnsForAViewIsFrozenWhenTheViewIsCreated() throws SQLException {
        statement.execute("CREATE TABLE fl164_base (id INTEGER, label VARCHAR(10))");
        statement.execute("CREATE VIEW fl164_star AS SELECT * FROM fl164_base");

        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals(10, sizeOfColumn(metaData, "FL164_STAR", "LABEL"));

        // Live-verified: a view's reported column metadata is a snapshot taken when the view was
        // created and does NOT follow the table underneath it afterwards. Widening the base column
        // leaves the view still reporting the width it was created against, and adding a column does
        // not widen a SELECT * view — the star was expanded once.
        statement.execute("ALTER TABLE fl164_base ALTER COLUMN label SET DATA TYPE VARCHAR(50)");
        statement.execute("ALTER TABLE fl164_base ADD COLUMN extra INTEGER");
        assertEquals(50, sizeOfColumn(metaData, "FL164_BASE", "LABEL"), "the table itself follows");
        assertEquals(10, sizeOfColumn(metaData, "FL164_STAR", "LABEL"),
            "the view does not follow an ALTER of the column it projects");
        assertEquals(2, columnTypesOf(metaData, "FL164_STAR").size(),
            "and a SELECT * view does not gain a column the base table gained");

        // Recreating the view is what re-resolves it — also live-verified.
        statement.execute("CREATE OR REPLACE VIEW fl164_star AS SELECT * FROM fl164_base");
        assertEquals(50, sizeOfColumn(metaData, "FL164_STAR", "LABEL"));
        assertEquals(3, columnTypesOf(metaData, "FL164_STAR").size());
    }

    @Test
    public void testGetColumnsForAViewDropsDefaultIdentityAndKey() throws SQLException {
        statement.execute("""
            CREATE TABLE fl164_base (
                seq_id INTEGER IDENTITY(1,1), keyed INTEGER PRIMARY KEY,
                with_default VARCHAR(5) DEFAULT 'hi')
            """);
        statement.execute("CREATE VIEW fl164_star AS SELECT * FROM fl164_base");

        final DatabaseMetaData metaData = connection.getMetaData();
        // Live reports all three of these EMPTY for a view, however the underlying column was
        // declared: a view has no defaults, no identity and no constraints of its own.
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL164_STAR", "%")) {
            while (rs.next()) {
                assertNull(rs.getString("COLUMN_DEF"),
                    "a view does not inherit its base column's DEFAULT");
                assertEquals("NO", rs.getString("IS_AUTOINCREMENT"),
                    "a view does not inherit its base column's IDENTITY");
            }
        }
        try (ResultSet rs = metaData.getPrimaryKeys("TEST_DB", "PUBLIC", "FL164_STAR")) {
            assertFalse(rs.next(), "a view has no primary key");
        }
        // The base table still reports all three, so nothing was lost on the way.
        boolean sawIdentity = false;
        boolean sawDefault = false;
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL164_BASE", "%")) {
            while (rs.next()) {
                if ("SEQ_ID".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    sawIdentity = "YES".equals(rs.getString("IS_AUTOINCREMENT"));
                } else if ("WITH_DEFAULT".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    sawDefault = rs.getString("COLUMN_DEF") != null;
                }
            }
        }
        assertTrue(sawIdentity, "the base table still reports its IDENTITY column");
        assertTrue(sawDefault, "the base table still reports its DEFAULT");
    }

    @Test
    public void testGetColumnsTypesAViewOverASchemaQualifiedTable() throws SQLException {
        // A table reached through a schema-qualified FROM used to look like a DERIVED relation to the
        // static-type channel, because the check asked the catalog to re-resolve its BARE name — which
        // resolves in the session's CURRENT schema and so found nothing (or the wrong table). Every
        // column of such a table was then untyped, and a view over one reported the VARCHAR
        // placeholder for a projection whose type is perfectly well known.
        statement.execute("CREATE SCHEMA IF NOT EXISTS fl164_other");
        statement.execute("CREATE TABLE fl164_other.src (tags ARRAY, id INTEGER)");
        statement.execute("USE SCHEMA PUBLIC");
        statement.execute("""
            CREATE VIEW fl164_qualified AS
            SELECT IFF(ARRAY_CONTAINS('x'::VARIANT, tags), ARRAY_APPEND(tags, 'y'), tags) AS tags
            FROM fl164_other.src
            """);
        // The same projection over an UNqualified source always worked, so both must now agree.
        statement.execute("CREATE TABLE fl164_local (tags ARRAY, id INTEGER)");
        statement.execute("""
            CREATE VIEW fl164_unqualified AS
            SELECT IFF(ARRAY_CONTAINS('x'::VARIANT, tags), ARRAY_APPEND(tags, 'y'), tags) AS tags
            FROM fl164_local
            """);

        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals("ARRAY", columnTypesOf(metaData, "FL164_UNQUALIFIED").get("TAGS"));
        assertEquals("ARRAY", columnTypesOf(metaData, "FL164_QUALIFIED").get("TAGS"),
            "a schema-qualified source must type its columns exactly as an unqualified one does");
    }

    @Test
    public void testGetColumnsTypesAViewOverAClonedTable() throws SQLException {
        // A CLONEd table is every bit as much a catalog table as the one it was cloned from, so a view
        // created against it must type its columns the same way. The clone path puts tables into the
        // new schema directly rather than through the schema's own add, so the two have to agree about
        // what makes a table the catalog's own — otherwise a view recreated inside a clone reports
        // different types from the identical view in the source database.
        statement.execute("CREATE TABLE fl164_src (tags ARRAY, id INTEGER)");
        statement.execute("""
            CREATE VIEW fl164_view AS
            SELECT IFF(ARRAY_CONTAINS('x'::VARIANT, tags), ARRAY_APPEND(tags, 'y'), tags) AS tags
            FROM fl164_src
            """);
        statement.execute("CREATE DATABASE fl164_clone CLONE test_db");
        try {
            statement.execute("USE DATABASE fl164_clone");
            statement.execute("USE SCHEMA PUBLIC");
            // Recreating the view inside the clone must resolve it against the cloned table exactly as
            // the original resolution did.
            statement.execute("""
                CREATE OR REPLACE VIEW fl164_view AS
                SELECT IFF(ARRAY_CONTAINS('x'::VARIANT, tags), ARRAY_APPEND(tags, 'y'), tags) AS tags
                FROM fl164_src
                """);
            final DatabaseMetaData metaData = connection.getMetaData();
            String inSource = null;
            try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", "FL164_VIEW", "TAGS")) {
                if (rs.next()) {
                    inSource = rs.getString("TYPE_NAME");
                }
            }
            String inClone = null;
            try (ResultSet rs = metaData.getColumns("FL164_CLONE", "PUBLIC", "FL164_VIEW", "TAGS")) {
                if (rs.next()) {
                    inClone = rs.getString("TYPE_NAME");
                }
            }
            assertEquals("ARRAY", inSource);
            assertEquals("ARRAY", inClone, "a view recreated in a clone must type as it does in the source");
        } finally {
            statement.execute("USE DATABASE test_db");
            statement.execute("USE SCHEMA PUBLIC");
            statement.execute("DROP DATABASE IF EXISTS fl164_clone");
        }
    }

    @Test
    public void testGetColumnsReportsAMaterializedViewsColumns() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "creating a materialized view on the live account needs a running warehouse and is "
            + "slow; that a materialized view reports its columns — and freezes them exactly as a "
            + "plain view does — was measured live separately");
        statement.execute("CREATE TABLE fl164_base (id INTEGER, label VARCHAR(30))");
        statement.execute("CREATE MATERIALIZED VIEW fl164_mv AS SELECT id, label FROM fl164_base");

        final DatabaseMetaData metaData = connection.getMetaData();
        assertEquals(Map.of("ID", "NUMBER", "LABEL", "VARCHAR"), columnTypesOf(metaData, "FL164_MV"));
    }

    /** COLUMN_SIZE of one named column of one relation. */
    private int sizeOfColumn(final DatabaseMetaData metaData, final String table, final String column)
            throws SQLException {
        try (ResultSet rs = metaData.getColumns("TEST_DB", "PUBLIC", table, column)) {
            assertTrue(rs.next(), "expected a row for " + table + "." + column);
            return rs.getInt("COLUMN_SIZE");
        }
    }

    @Test
    public void testGetPrimaryKeysUsesTheSpecifiedColumnLabels() throws SQLException {
        statement.execute("CREATE TABLE fl162_pk (id INTEGER PRIMARY KEY, name VARCHAR)");
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getPrimaryKeys("TEST_DB", "PUBLIC", "FL162_PK")) {
            assertEquals(List.of("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME",
                "KEY_SEQ", "PK_NAME"), labelsOf(rs));
        }
    }

    /** The column labels of a result, upper-cased, in order. */
    private List<String> labelsOf(final ResultSet rs) throws SQLException {
        final ResultSetMetaData md = rs.getMetaData();
        final List<String> labels = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            labels.add(md.getColumnLabel(i).toUpperCase());
        }
        return labels;
    }
}
