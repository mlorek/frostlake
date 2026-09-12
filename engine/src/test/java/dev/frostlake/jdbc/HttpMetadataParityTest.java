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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP transport's {@code java.sql.DatabaseMetaData} must answer exactly like the direct
 * transport's: both connections receive the same DDL and every metadata call is compared cell by
 * cell. The direct answers are live-verified elsewhere ({@code DatabaseMetaDataTest} runs against
 * Snowflake under SF_LIVE), so parity here transitively pins the HTTP transport to the same
 * live-measured behaviour without needing a server-per-live-run.
 */
public class HttpMetadataParityTest {
    private static final Logger logger = LoggerFactory.getLogger(HttpMetadataParityTest.class);

    private static DatabaseHttpServer server;
    private static String httpUrl;

    private Connection http;
    private Connection direct;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        httpUrl = "jdbc:frostlake://localhost:" + port;
        logger.info("metadata parity server on {}", httpUrl);
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void setup() throws SQLException {
        http = DriverManager.getConnection(httpUrl);
        direct = DriverManager.getConnection("jdbc:frostlake:direct:meta_parity");
        for (final Connection c : new Connection[] { http, direct }) {
            try (final Statement s = c.createStatement()) {
                s.execute("CREATE OR REPLACE DATABASE meta_par");
                s.execute("USE DATABASE meta_par");
                s.execute("USE SCHEMA PUBLIC");
                s.execute("CREATE OR REPLACE TABLE parent (id INTEGER PRIMARY KEY, name VARCHAR)");
                s.execute("CREATE OR REPLACE TABLE child (id INTEGER PRIMARY KEY,"
                    + " parent_id INTEGER REFERENCES parent(id), amount NUMBER(10,2), d DATE)");
                s.execute("CREATE OR REPLACE VIEW v_child AS SELECT id, amount FROM child");
                s.execute("CREATE OR REPLACE FUNCTION f_meta(x INTEGER) RETURNS INTEGER"
                    + " LANGUAGE SQL AS 'x * 2'");
                s.execute("CREATE OR REPLACE PROCEDURE p_meta() RETURNS VARCHAR LANGUAGE SQL"
                    + " AS 'BEGIN RETURN ''ok''; END'");
            }
        }
    }

    @AfterEach
    public void teardown() throws SQLException {
        for (final Connection c : new Connection[] { http, direct }) {
            if (c != null) {
                try (final Statement s = c.createStatement()) {
                    s.execute("DROP DATABASE IF EXISTS meta_par");
                } catch (final SQLException cleanupFailure) {
                    // best-effort cleanup
                }
                c.close();
            }
        }
    }

    /** The isNullable codes of one query's columns on one connection, as readable names. */
    private String nullabilityOn(final Connection connection, final String query) throws SQLException {
        try (final Statement s = connection.createStatement()) {
            final ResultSet rs = s.executeQuery(query);
            final java.sql.ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                final int code = md.isNullable(i);
                out.append(i == 1 ? "" : "|").append(code == java.sql.ResultSetMetaData.columnNoNulls
                    ? "NoNulls" : code == java.sql.ResultSetMetaData.columnNullable
                        ? "Nullable" : "Unknown");
            }
            return out.toString();
        }
    }

    /**
     * ResultSetMetaData.isNullable agrees across the two transports, and says what live says: a NOT
     * NULL column reads columnNoNulls, a nullable one columnNullable, and an EXPRESSION over either
     * reads columnNoNulls rather than nullable. The HTTP transport learned this from a wire field; it
     * used to answer columnNullableUnknown for everything.
     */
    @Test
    public void isNullableAgreesAcrossTransports() throws SQLException {
        for (final Connection c : new Connection[] { http, direct }) {
            try (final Statement s = c.createStatement()) {
                s.execute("CREATE OR REPLACE TABLE nn_t (k NUMBER NOT NULL, w VARCHAR(4))");
            }
        }
        final String columns = "SELECT k, w FROM nn_t";
        final String expression = "SELECT UPPER(w) AS uw FROM nn_t";
        assertEquals("NoNulls|Nullable", nullabilityOn(direct, columns));
        assertEquals(nullabilityOn(direct, columns), nullabilityOn(http, columns));
        assertEquals("NoNulls", nullabilityOn(direct, expression));
        assertEquals(nullabilityOn(direct, expression), nullabilityOn(http, expression));
    }

    /** Each column's precision and display size on one connection, as "precision/display" joined by "|". */
    private String lengthsOn(final Connection connection, final String query) throws SQLException {
        try (final Statement s = connection.createStatement()) {
            final ResultSet rs = s.executeQuery(query);
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.append(i == 1 ? "" : "|").append(md.getPrecision(i)).append('/')
                    .append(md.getColumnDisplaySize(i));
            }
            return out.toString();
        }
    }

    /**
     * A text or binary column's length crosses the wire, so both transports answer it as the column's
     * precision and display size alike; the HTTP transport used to read 0 and 255.
     */
    @Test
    public void textAndBinaryLengthsAgreeAcrossTransports() throws SQLException {
        for (final Connection c : new Connection[] { http, direct }) {
            try (final Statement s = c.createStatement()) {
                s.execute("CREATE OR REPLACE TABLE len_t (u VARCHAR, v9 VARCHAR(9), b BINARY, b5 BINARY(5))");
            }
        }
        final String query = "SELECT u, v9, b, b5, TO_BINARY(u) AS tb FROM len_t";
        assertEquals("16777216/16777216|9/9|8388608/8388608|5/5|67108864/67108864", lengthsOn(direct, query));
        assertEquals(lengthsOn(direct, query), lengthsOn(http, query));
    }

    /** Render selected columns of a metadata result set for order-insensitive comparison. */
    private List<String> render(final ResultSet rs, final String... columns) throws SQLException {
        final List<String> rows = new ArrayList<>();
        while (rs.next()) {
            final StringBuilder b = new StringBuilder();
            for (final String column : columns) {
                b.append(rs.getString(column)).append('|');
            }
            rows.add(b.toString());
        }
        rs.close();
        rows.sort(null);
        return rows;
    }

    @Test
    public void productAndDriverIdentityAgree() throws SQLException {
        final DatabaseMetaData h = http.getMetaData();
        final DatabaseMetaData d = direct.getMetaData();
        assertEquals(d.getDatabaseProductName(), h.getDatabaseProductName());
        assertEquals(d.getDatabaseProductVersion(), h.getDatabaseProductVersion());
        assertEquals(d.getDriverName(), h.getDriverName());
        assertEquals(d.getDriverVersion(), h.getDriverVersion());
        assertEquals(d.getIdentifierQuoteString(), h.getIdentifierQuoteString());
        assertEquals(d.getSQLKeywords(), h.getSQLKeywords());
        assertEquals(d.getSearchStringEscape(), h.getSearchStringEscape());
        assertEquals(d.getCatalogSeparator(), h.getCatalogSeparator());
        assertFalse(h.getDatabaseProductName().isEmpty());
    }

    @Test
    public void capabilityBooleansAgree() throws SQLException {
        final DatabaseMetaData h = http.getMetaData();
        final DatabaseMetaData d = direct.getMetaData();
        assertEquals(d.supportsTransactions(), h.supportsTransactions());
        assertEquals(d.supportsMultipleResultSets(), h.supportsMultipleResultSets());
        assertEquals(d.supportsBatchUpdates(), h.supportsBatchUpdates());
        assertEquals(d.supportsOuterJoins(), h.supportsOuterJoins());
        assertEquals(d.supportsUnionAll(), h.supportsUnionAll());
        assertEquals(d.supportsGroupBy(), h.supportsGroupBy());
        assertEquals(d.supportsSubqueriesInExists(), h.supportsSubqueriesInExists());
        assertEquals(d.storesUpperCaseIdentifiers(), h.storesUpperCaseIdentifiers());
        assertEquals(d.supportsMixedCaseIdentifiers(), h.supportsMixedCaseIdentifiers());
        assertEquals(d.nullsAreSortedHigh(), h.nullsAreSortedHigh());
    }

    @Test
    public void catalogsAndSchemasAgree() throws SQLException {
        final List<String> hCatalogs = render(http.getMetaData().getCatalogs(), "TABLE_CAT");
        final List<String> dCatalogs = render(direct.getMetaData().getCatalogs(), "TABLE_CAT");
        assertTrue(hCatalogs.contains("META_PAR|"));
        // catalog lists may differ in unrelated databases (shared direct engine); the created one
        // must appear on both sides, spelled identically
        assertTrue(dCatalogs.contains("META_PAR|"));

        final List<String> hSchemas = render(
            http.getMetaData().getSchemas("META_PAR", "%"), "TABLE_SCHEM", "TABLE_CATALOG");
        final List<String> dSchemas = render(
            direct.getMetaData().getSchemas("META_PAR", "%"), "TABLE_SCHEM", "TABLE_CATALOG");
        assertEquals(dSchemas, hSchemas);
        assertTrue(hSchemas.toString().contains("PUBLIC"));
    }

    @Test
    public void tablesAndViewsAgree() throws SQLException {
        final String[] cols = { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE" };
        final List<String> h = render(
            http.getMetaData().getTables("META_PAR", "PUBLIC", "%", null), cols);
        final List<String> d = render(
            direct.getMetaData().getTables("META_PAR", "PUBLIC", "%", null), cols);
        assertEquals(d, h);
        assertEquals(3, h.size()); // parent, child, v_child
        final List<String> viewsOnly = render(
            http.getMetaData().getTables("META_PAR", "PUBLIC", "%", new String[] { "VIEW" }), cols);
        assertEquals(1, viewsOnly.size());
        assertTrue(viewsOnly.get(0).contains("V_CHILD"));
    }

    @Test
    public void columnsAgreeIncludingTypes() throws SQLException {
        final String[] cols = { "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME",
            "COLUMN_SIZE", "DECIMAL_DIGITS", "NULLABLE", "ORDINAL_POSITION", "IS_NULLABLE" };
        final List<String> h = render(
            http.getMetaData().getColumns("META_PAR", "PUBLIC", "CHILD", "%"), cols);
        final List<String> d = render(
            direct.getMetaData().getColumns("META_PAR", "PUBLIC", "CHILD", "%"), cols);
        assertEquals(d, h);
        assertEquals(4, h.size());
    }

    @Test
    public void keysAgree() throws SQLException {
        final String[] pk = { "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ" };
        assertEquals(
            render(direct.getMetaData().getPrimaryKeys("META_PAR", "PUBLIC", "CHILD"), pk),
            render(http.getMetaData().getPrimaryKeys("META_PAR", "PUBLIC", "CHILD"), pk));
        final String[] fk = { "PKTABLE_NAME", "PKCOLUMN_NAME", "FKTABLE_NAME", "FKCOLUMN_NAME" };
        final List<String> h = render(
            http.getMetaData().getImportedKeys("META_PAR", "PUBLIC", "CHILD"), fk);
        assertEquals(
            render(direct.getMetaData().getImportedKeys("META_PAR", "PUBLIC", "CHILD"), fk), h);
        assertEquals(1, h.size());
        assertTrue(h.get(0).startsWith("PARENT|ID|CHILD|PARENT_ID"));
    }

    @Test
    public void routinesAgree() throws SQLException {
        final String[] pc = { "PROCEDURE_NAME" };
        assertEquals(
            render(direct.getMetaData().getProcedures("META_PAR", "PUBLIC", "%"), pc),
            render(http.getMetaData().getProcedures("META_PAR", "PUBLIC", "%"), pc));
        final String[] fn = { "FUNCTION_NAME" };
        final List<String> h = render(
            http.getMetaData().getFunctions("META_PAR", "PUBLIC", "F_META"), fn);
        assertEquals(
            render(direct.getMetaData().getFunctions("META_PAR", "PUBLIC", "F_META"), fn), h);
        assertEquals(1, h.size());
    }

    @Test
    public void typeInfoAgrees() throws SQLException {
        final String[] cols = { "TYPE_NAME", "DATA_TYPE" };
        assertEquals(
            render(direct.getMetaData().getTypeInfo(), cols),
            render(http.getMetaData().getTypeInfo(), cols));
    }

    @Test
    public void resultSetMetaDataAgreesOverBothTransports() throws SQLException {
        final String sql = "SELECT id, amount, d, id IS NULL AS flag FROM child";
        try (final Statement hs = http.createStatement();
             final Statement ds = direct.createStatement()) {
            final ResultSetMetaData hm = hs.executeQuery(sql).getMetaData();
            final ResultSetMetaData dm = ds.executeQuery(sql).getMetaData();
            assertEquals(dm.getColumnCount(), hm.getColumnCount());
            for (int i = 1; i <= dm.getColumnCount(); i++) {
                assertEquals(dm.getColumnName(i), hm.getColumnName(i), "name col " + i);
                assertEquals(dm.getColumnType(i), hm.getColumnType(i), "type col " + i);
                assertEquals(dm.getColumnTypeName(i), hm.getColumnTypeName(i), "typeName col " + i);
                assertEquals(dm.getPrecision(i), hm.getPrecision(i), "precision col " + i);
                assertEquals(dm.getScale(i), hm.getScale(i), "scale col " + i);
            }
        }
    }
}
