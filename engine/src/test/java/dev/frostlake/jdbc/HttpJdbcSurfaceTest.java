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

import dev.frostlake.http.DatabaseHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the HTTP-transport JDBC surface end to end against a live {@link DatabaseHttpServer}:
 * {@link DatabaseCallableStatement} result getters, the {@link DatabaseResultSet} typed-getter and
 * navigation matrix, the {@link DatabasePreparedStatement} setter matrix, {@link DatabaseMetaData}
 * queries and {@link DatabaseConnection} transaction/savepoint behavior.
 */
public class HttpJdbcSurfaceTest {

    private static final Logger logger = LoggerFactory.getLogger(HttpJdbcSurfaceTest.class);

    private static DatabaseHttpServer server;
    private static String jdbcUrl;

    private Connection connection;
    private Statement statement;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        jdbcUrl = "jdbc:frostlake://localhost:" + port;
        logger.info("HTTP JDBC surface server on {}", jdbcUrl);
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void setup() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE OR REPLACE DATABASE http_surface_db");
        statement.execute("USE DATABASE http_surface_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void teardown() throws SQLException {
        try {
            statement.execute("DROP DATABASE IF EXISTS http_surface_db");
        } catch (final SQLException e) {
            // best-effort cleanup
        }
        statement.close();
        connection.close();
    }

    @Test
    public void callableNumericReturnCoversEveryNumericGetter() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE PROCEDURE ADD_ONE(N FLOAT) RETURNS FLOAT LANGUAGE SQL AS
            $$ BEGIN RETURN N + 1; END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL ADD_ONE(?)")) {
            cs.registerOutParameter(1, Types.DOUBLE);
            cs.setDouble(1, 41);
            cs.execute();
            assertEquals(42.0, cs.getDouble(1));
            assertEquals(42.0f, cs.getFloat(1));
            assertEquals(42, cs.getInt(1));
            assertEquals(42L, cs.getLong(1));
            assertEquals((short) 42, cs.getShort(1));
            assertEquals((byte) 42, cs.getByte(1));
            assertEquals(0, cs.getBigDecimal(1).compareTo(new BigDecimal("42")));
            assertNotNull(cs.getObject(1));
            assertFalse(cs.wasNull());
        }
    }

    @Test
    public void callableVarcharBooleanAndNullReturns() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_TAG() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN 'frost'; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE IS_ON() RETURNS BOOLEAN LANGUAGE SQL AS
            $$ BEGIN RETURN TRUE; END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_NULL() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN NULL; END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_TAG()")) {
            cs.execute();
            assertEquals("frost", cs.getString(1));
            assertFalse(cs.wasNull());
        }
        try (final CallableStatement cs = connection.prepareCall("CALL IS_ON()")) {
            cs.execute();
            assertTrue(cs.getBoolean(1));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_NULL()")) {
            cs.execute();
            assertNull(cs.getString(1));
            assertTrue(cs.wasNull());
        }
    }

    @Test
    public void callableTemporalReturns() throws SQLException {
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_TS() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS
            $$ BEGIN RETURN TO_TIMESTAMP('2026-01-15 10:30:00'); END $$
            """);
        statement.execute("""
            CREATE OR REPLACE PROCEDURE GIVE_DATE() RETURNS DATE LANGUAGE SQL AS
            $$ BEGIN RETURN TO_DATE('2026-01-15'); END $$
            """);
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_TS()")) {
            cs.execute();
            final Timestamp ts = cs.getTimestamp(1);
            assertNotNull(ts);
            assertTrue(ts.toString().startsWith("2026-01-15 10:30:00"));
        }
        try (final CallableStatement cs = connection.prepareCall("CALL GIVE_DATE()")) {
            cs.execute();
            assertEquals(Date.valueOf("2026-01-15").toString(), cs.getDate(1).toString());
        }
    }

    @Test
    public void resultSetTypedGetterMatrixByIndexAndLabel() throws SQLException {
        statement.execute("""
            CREATE TABLE rs_types (i INTEGER, l BIGINT, d DOUBLE, n NUMBER(10,2), s VARCHAR,
                                   b BOOLEAN, dt DATE, tm TIME, ts TIMESTAMP_NTZ, nul VARCHAR)
            """);
        statement.execute("""
            INSERT INTO rs_types VALUES (42, 9876543210, 3.5, 12.34, 'frost', TRUE,
                                         '2026-01-15', '10:30:00', '2026-01-15 10:30:00', NULL)
            """);
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM rs_types")) {
            assertTrue(rs.isBeforeFirst());
            assertTrue(rs.next());
            assertEquals(1, rs.getRow());

            assertEquals(42, rs.getInt(1));
            assertEquals(42, rs.getInt("I"));
            assertEquals((short) 42, rs.getShort(1));
            assertEquals((byte) 42, rs.getByte(1));
            assertEquals(9876543210L, rs.getLong(2));
            assertEquals(9876543210L, rs.getLong("L"));
            assertEquals(3.5, rs.getDouble(3));
            assertEquals(3.5f, rs.getFloat(3));
            assertEquals(0, rs.getBigDecimal(4).compareTo(new BigDecimal("12.34")));
            assertEquals(0, rs.getBigDecimal("N").compareTo(new BigDecimal("12.34")));
            assertEquals("frost", rs.getString(5));
            assertEquals("frost", rs.getString("S"));
            assertTrue(rs.getBoolean(6));
            assertTrue(rs.getBoolean("B"));
            assertEquals("2026-01-15", rs.getDate(7).toString());
            assertEquals("2026-01-15", rs.getDate("DT").toString());
            assertEquals("10:30:00", rs.getTime(8).toString());
            assertEquals("10:30:00", rs.getTime("TM").toString());
            assertTrue(rs.getTimestamp(9).toString().startsWith("2026-01-15 10:30:00"));
            assertTrue(rs.getTimestamp("TS").toString().startsWith("2026-01-15 10:30:00"));
            assertNotNull(rs.getObject(1));

            assertNull(rs.getString(10));
            assertTrue(rs.wasNull());
            assertEquals(5, rs.findColumn("S"));

            final ResultSetMetaData md = rs.getMetaData();
            assertEquals(10, md.getColumnCount());
            assertEquals("I", md.getColumnName(1));
            assertEquals(Types.VARCHAR, md.getColumnType(5));

            assertFalse(rs.next());
            assertTrue(rs.isAfterLast());
        }
    }

    @Test
    public void resultSetFindColumnUnknownThrows() throws SQLException {
        statement.execute("CREATE TABLE fc_t (a INTEGER)");
        statement.execute("INSERT INTO fc_t VALUES (1)");
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM fc_t")) {
            assertTrue(rs.next());
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    rs.findColumn("NO_SUCH_COLUMN");
                }
            });
        }
    }

    @Test
    public void preparedStatementSetterMatrix() throws SQLException {
        statement.execute("""
            CREATE TABLE ps_types (b BOOLEAN, bt NUMBER(3,0), sh NUMBER(5,0), i INTEGER, l BIGINT,
                                   f DOUBLE, d DOUBLE, dec NUMBER(10,2), s VARCHAR, dt DATE,
                                   tm TIME, ts TIMESTAMP_NTZ, o VARCHAR, nul INTEGER)
            """);
        final String insert = "INSERT INTO ps_types VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (final PreparedStatement ps = connection.prepareStatement(insert)) {
            ps.setBoolean(1, true);
            ps.setByte(2, (byte) 7);
            ps.setShort(3, (short) 300);
            ps.setInt(4, 42);
            ps.setLong(5, 9876543210L);
            ps.setFloat(6, 1.5f);
            ps.setDouble(7, 2.5);
            ps.setBigDecimal(8, new BigDecimal("12.34"));
            ps.setString(9, "frost");
            ps.setDate(10, Date.valueOf("2026-01-15"));
            ps.setTime(11, Time.valueOf("10:30:00"));
            ps.setTimestamp(12, Timestamp.valueOf("2026-01-15 10:30:00"));
            ps.setObject(13, "as-object");
            ps.setNull(14, Types.INTEGER);
            assertEquals(1, ps.executeUpdate());
        }
        try (final ResultSet rs = statement.executeQuery("SELECT * FROM ps_types")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
            assertEquals(7, rs.getInt(2));
            assertEquals(300, rs.getInt(3));
            assertEquals(42, rs.getInt(4));
            assertEquals("frost", rs.getString(9));
            assertEquals("as-object", rs.getString(13));
            rs.getObject(14);
            assertTrue(rs.wasNull());
        }
    }

    @Test
    public void preparedStatementReuseAndClearParameters() throws SQLException {
        statement.execute("CREATE TABLE ps_reuse (i INTEGER, s VARCHAR)");
        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO ps_reuse VALUES (?, ?)")) {
            ps.setInt(1, 1);
            ps.setString(2, "one");
            assertEquals(1, ps.executeUpdate());
            ps.clearParameters();
            ps.setInt(1, 2);
            ps.setString(2, "two");
            assertEquals(1, ps.executeUpdate());
        }
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM ps_reuse")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    public void preparedStatementQueryAndExecuteForms() throws SQLException {
        statement.execute("CREATE TABLE ps_q (i INTEGER)");
        statement.execute("INSERT INTO ps_q VALUES (1), (2), (3)");
        try (final PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM ps_q WHERE i >= ?")) {
            ps.setInt(1, 2);
            try (final ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            ps.setInt(1, 1);
            assertTrue(ps.execute());
            try (final ResultSet rs = ps.getResultSet()) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }
        }
    }

    @Test
    public void databaseMetaDataSampling() throws SQLException {
        statement.execute("CREATE TABLE md_probe (id INTEGER, name VARCHAR)");
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        assertNotNull(md.getDatabaseProductName());
        assertNotNull(md.getDatabaseProductVersion());
        assertNotNull(md.getDriverName());
        assertNotNull(md.getDriverVersion());
        assertNotNull(md.getURL());
        assertNotNull(md.getIdentifierQuoteString());

        boolean sawProbe = false;
        try (final ResultSet tables = md.getTables(null, null, "%", null)) {
            while (tables.next()) {
                if ("MD_PROBE".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    sawProbe = true;
                }
            }
        }
        assertTrue(sawProbe, "getTables must list MD_PROBE");

        boolean sawName = false;
        try (final ResultSet cols = md.getColumns(null, null, "MD_PROBE", "%")) {
            while (cols.next()) {
                if ("NAME".equalsIgnoreCase(cols.getString("COLUMN_NAME"))) {
                    sawName = true;
                }
            }
        }
        assertTrue(sawName, "getColumns must list NAME");

        try (final ResultSet schemas = md.getSchemas()) {
            assertNotNull(schemas);
        }
        try (final ResultSet catalogs = md.getCatalogs()) {
            assertNotNull(catalogs);
        }
    }

    // ── getSchemas(catalog, schemaPattern) over HTTP ──────────────────────────────────────────
    // This is the transport a desktop SQL client actually connects on, and the overload it calls to
    // fill a catalog's schema list. It used to throw SQLFeatureNotSupportedException, leaving a
    // freshly created database showing no schemas at all.

    @Test
    public void getSchemasForCatalogListsPublicAndInformationSchema() throws SQLException {
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        try (final ResultSet rs = md.getSchemas("HTTP_SURFACE_DB", null)) {
            final ResultSetMetaData rsMeta = rs.getMetaData();
            assertEquals(2, rsMeta.getColumnCount());
            assertEquals("TABLE_SCHEM", rsMeta.getColumnLabel(1).toUpperCase());
            assertEquals("TABLE_CATALOG", rsMeta.getColumnLabel(2).toUpperCase());

            boolean sawPublic = false;
            boolean sawInformationSchema = false;
            while (rs.next()) {
                assertEquals("HTTP_SURFACE_DB", rs.getString("TABLE_CATALOG"));
                final String schema = rs.getString("TABLE_SCHEM");
                if ("PUBLIC".equals(schema)) {
                    sawPublic = true;
                } else if ("INFORMATION_SCHEMA".equals(schema)) {
                    sawInformationSchema = true;
                }
            }
            assertTrue(sawPublic, "a fresh database has PUBLIC");
            assertTrue(sawInformationSchema, "a fresh database has INFORMATION_SCHEMA");
        }
    }

    @Test
    public void getSchemasOverHttpHonoursPatternAndUnknownCatalog() throws SQLException {
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        try (final ResultSet rs = md.getSchemas("HTTP_SURFACE_DB", "PUB%")) {
            assertTrue(rs.next());
            assertEquals("PUBLIC", rs.getString("TABLE_SCHEM"));
            assertFalse(rs.next());
        }
        try (final ResultSet rs = md.getSchemas("HTTP_SURFACE_DB", "%")) {
            assertTrue(rs.next(), "a \"%\" pattern matches everything");
        }
        // Live Snowflake answers zero rows for a catalog no database has, rather than raising.
        try (final ResultSet rs = md.getSchemas("NO_SUCH_DATABASE_FL160", null)) {
            assertFalse(rs.next());
        }
    }

    @Test
    public void getSchemasOverHttpKeepsDatabasesApart() throws SQLException {
        statement.execute("CREATE OR REPLACE DATABASE http_fl160_alpha");
        statement.execute("CREATE SCHEMA http_fl160_alpha.alpha_only");
        statement.execute("CREATE OR REPLACE DATABASE http_fl160_beta");
        statement.execute("CREATE SCHEMA http_fl160_beta.beta_only");
        try {
            final java.sql.DatabaseMetaData md = connection.getMetaData();
            assertEquals(List.of("ALPHA_ONLY", "INFORMATION_SCHEMA", "PUBLIC"),
                schemasOf(md, "HTTP_FL160_ALPHA"));
            assertEquals(List.of("BETA_ONLY", "INFORMATION_SCHEMA", "PUBLIC"),
                schemasOf(md, "HTTP_FL160_BETA"));
        } finally {
            statement.execute("DROP DATABASE IF EXISTS http_fl160_alpha");
            statement.execute("DROP DATABASE IF EXISTS http_fl160_beta");
        }
    }

    /** The schema names one catalog reports, in the order the driver returned them. */
    private List<String> schemasOf(final java.sql.DatabaseMetaData md, final String catalog) throws SQLException {
        final List<String> names = new ArrayList<>();
        try (final ResultSet rs = md.getSchemas(catalog, null)) {
            while (rs.next()) {
                assertEquals(catalog, rs.getString("TABLE_CATALOG"));
                names.add(rs.getString("TABLE_SCHEM"));
            }
        }
        return names;
    }

    @Test
    public void getTablesOverHttpReadsNonCurrentCatalog() throws SQLException {
        statement.execute("CREATE OR REPLACE DATABASE http_fl160_other");
        statement.execute("CREATE TABLE http_fl160_other.public.other_probe (id INTEGER)");
        try {
            statement.execute("USE DATABASE http_surface_db");
            statement.execute("USE SCHEMA PUBLIC");
            final java.sql.DatabaseMetaData md = connection.getMetaData();
            boolean sawProbe = false;
            try (final ResultSet rs = md.getTables("HTTP_FL160_OTHER", "PUBLIC", "%", null)) {
                while (rs.next()) {
                    if ("OTHER_PROBE".equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        sawProbe = true;
                    }
                }
            }
            assertTrue(sawProbe, "getTables must see a table in a catalog other than the current one");
        } finally {
            statement.execute("DROP DATABASE IF EXISTS http_fl160_other");
        }
    }

    // ── getTables / getColumns shape over HTTP ────────────────────────────────────────────────
    // HTTP is the transport a desktop SQL client connects on, so the object types it can filter by
    // and the column labels it reads have to be right here, not just in-process.

    /** The TABLE_TYPE each object reports, keyed by name, for one getTables call. */
    private Map<String, String> tableTypesOf(final java.sql.DatabaseMetaData md, final String[] types)
            throws SQLException {
        final Map<String, String> found = new TreeMap<>();
        try (final ResultSet rs = md.getTables("HTTP_SURFACE_DB", "PUBLIC", "FL162%", types)) {
            while (rs.next()) {
                found.put(rs.getString("TABLE_NAME").toUpperCase(), rs.getString("TABLE_TYPE"));
            }
        }
        return found;
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

    @Test
    public void getTablesOverHttpMapsTypesAndListsViews() throws SQLException {
        statement.execute("CREATE TABLE fl162_tbl (id INTEGER, name VARCHAR)");
        statement.execute("CREATE VIEW fl162_vw AS SELECT id FROM fl162_tbl");
        final java.sql.DatabaseMetaData md = connection.getMetaData();

        final Map<String, String> unfiltered = tableTypesOf(md, null);
        assertEquals("TABLE", unfiltered.get("FL162_TBL"),
            "the catalog's own BASE TABLE is not a type getTableTypes() offers");
        assertEquals("VIEW", unfiltered.get("FL162_VW"), "a view used to be absent entirely");

        assertEquals(Set.of("FL162_TBL"), tableTypesOf(md, new String[] {"TABLE"}).keySet());
        assertEquals(Set.of("FL162_VW"), tableTypesOf(md, new String[] {"VIEW"}).keySet());
        assertEquals(Set.of("FL162_TBL", "FL162_VW"),
            tableTypesOf(md, new String[] {"TABLE", "VIEW"}).keySet());
        assertTrue(tableTypesOf(md, new String[0]).isEmpty(), "an empty array matches nothing");
        assertTrue(tableTypesOf(md, new String[] {"BASE TABLE"}).isEmpty(),
            "the raw catalog value is not part of the advertised vocabulary");
    }

    /** COLUMN_NAME → TYPE_NAME for one getColumns call over HTTP, in projection order. */
    private Map<String, String> columnTypesOf(final java.sql.DatabaseMetaData md, final String table)
            throws SQLException {
        final Map<String, String> found = new LinkedHashMap<>();
        try (final ResultSet rs = md.getColumns("HTTP_SURFACE_DB", "PUBLIC", table, "%")) {
            while (rs.next()) {
                found.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getString("TYPE_NAME"));
            }
        }
        return found;
    }

    @Test
    public void getColumnsOverHttpReportsAViewsColumns() throws SQLException {
        // HTTP is the transport a desktop SQL client connects on, and expanding a view in its
        // navigator is exactly this call. It used to come back empty for every view.
        statement.execute("CREATE TABLE fl164_base (id INTEGER, label VARCHAR(30), amount NUMBER(12,4))");
        statement.execute("CREATE VIEW fl164_star AS SELECT * FROM fl164_base");
        statement.execute("CREATE VIEW fl164_named (k, v) AS SELECT id, label FROM fl164_base");
        statement.execute("CREATE VIEW fl164_expr AS SELECT UPPER(label) AS shout, "
            + "OBJECT_CONSTRUCT('k', label) AS obj FROM fl164_base");
        final java.sql.DatabaseMetaData md = connection.getMetaData();

        assertEquals(List.of("ID", "LABEL", "AMOUNT"),
            new ArrayList<>(columnTypesOf(md, "FL164_STAR").keySet()));
        assertEquals(Map.of("ID", "NUMBER", "LABEL", "VARCHAR", "AMOUNT", "NUMBER"),
            columnTypesOf(md, "FL164_STAR"));
        assertEquals(Map.of("K", "NUMBER", "V", "VARCHAR"), columnTypesOf(md, "FL164_NAMED"),
            "an explicit column list renames the projection but keeps its types");
        assertEquals(Map.of("SHOUT", "VARCHAR", "OBJ", "OBJECT"), columnTypesOf(md, "FL164_EXPR"));

        // Size and scale survive the HTTP round trip, so the client's column grid is populated.
        try (final ResultSet rs = md.getColumns("HTTP_SURFACE_DB", "PUBLIC", "FL164_STAR", "%")) {
            while (rs.next()) {
                final String column = rs.getString("COLUMN_NAME").toUpperCase();
                if ("LABEL".equals(column)) {
                    assertEquals(30, rs.getInt("COLUMN_SIZE"));
                    assertEquals(Types.VARCHAR, rs.getInt("DATA_TYPE"));
                } else if ("AMOUNT".equals(column)) {
                    assertEquals(12, rs.getInt("COLUMN_SIZE"));
                    assertEquals(4, rs.getInt("DECIMAL_DIGITS"));
                }
            }
        }
        // The table's own columns are untouched by any of this.
        assertEquals(List.of("ID", "LABEL", "AMOUNT"),
            new ArrayList<>(columnTypesOf(md, "FL164_BASE").keySet()));
    }

    @Test
    public void getTablesAndGetColumnsOverHttpUseTheSpecifiedLabels() throws SQLException {
        statement.execute("CREATE TABLE fl162_shape (id INTEGER)");
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        try (final ResultSet rs = md.getTables("HTTP_SURFACE_DB", "PUBLIC", "%", null)) {
            assertEquals(List.of("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS",
                "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION"),
                labelsOf(rs));
        }
        try (final ResultSet rs = md.getColumns("HTTP_SURFACE_DB", "PUBLIC", "FL162_SHAPE", "%")) {
            assertEquals(List.of("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE",
                "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "NUM_PREC_RADIX",
                "NULLABLE", "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB",
                "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG",
                "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT",
                "IS_GENERATEDCOLUMN"), labelsOf(rs));
        }
    }

    @Test
    public void getColumnsOverHttpReportsDataTypeAsAJavaSqlTypesCode() throws SQLException {
        statement.execute("""
            CREATE TABLE fl162_types (
                c_int INT, c_number NUMBER(10,2), c_varchar VARCHAR(50), c_bool BOOLEAN,
                c_date DATE, c_ts TIMESTAMP_NTZ, c_bin BINARY(16))
            """);
        final Map<String, Integer> codes = new TreeMap<>();
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        try (final ResultSet rs = md.getColumns("HTTP_SURFACE_DB", "PUBLIC", "FL162_TYPES", "%")) {
            while (rs.next()) {
                // rs.getInt is the point: DATA_TYPE used to be a type-name string, which no client
                // could read as the int the JDBC contract promises.
                codes.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getInt("DATA_TYPE"));
            }
        }
        assertEquals(Types.BIGINT, codes.get("C_INT"));
        assertEquals(Types.DECIMAL, codes.get("C_NUMBER"));
        assertEquals(Types.VARCHAR, codes.get("C_VARCHAR"));
        assertEquals(Types.BOOLEAN, codes.get("C_BOOL"));
        assertEquals(Types.DATE, codes.get("C_DATE"));
        assertEquals(Types.TIMESTAMP, codes.get("C_TS"));
        assertEquals(Types.BINARY, codes.get("C_BIN"));
    }

    @Test
    public void connectionTransactionsAndSavepointsUnsupported() throws SQLException {
        statement.execute("CREATE TABLE tx_t (i INTEGER)");
        connection.setAutoCommit(false);
        assertFalse(connection.getAutoCommit());
        statement.execute("INSERT INTO tx_t VALUES (1)");
        connection.rollback();
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tx_t")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
        statement.execute("INSERT INTO tx_t VALUES (2)");
        connection.commit();
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tx_t")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
        connection.setAutoCommit(true);

        assertTrue(connection.isValid(2));
        assertFalse(connection.isClosed());

        // Savepoints do not exist in Snowflake; the driver must refuse them.
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.setSavepoint();
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.setSavepoint("sp1");
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.rollback(null);
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                connection.releaseSavepoint(null);
            }
        });
    }
}
