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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ResultSetMetaData names the database, the schema and the table a column projecting a stored table's column as it is
 * reads from (getCatalogName, getSchemaName, getTableName), as the account's driver reads them from each column's row
 * type; a computed column names none. Both drivers answered blanks for every column. Which other expressions the
 * account leaves blank is unmeasured on live.
 */
public class ColumnSourceMetadataTest extends BaseJdbcTest {

    /** The three names of each column of a query, as "catalog.schema.table" or "" for none, joined by " ;; ". */
    private static String sources(final Connection connection, final String sql) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (Statement read = connection.createStatement(); ResultSet rs = read.executeQuery(sql)) {
            final ResultSetMetaData columns = rs.getMetaData();
            for (int i = 1; i <= columns.getColumnCount(); i++) {
                out.append(i == 1 ? "" : " ;; ");
                final String table = columns.getTableName(i);
                out.append(table.isEmpty() ? "" : columns.getCatalogName(i) + "." + columns.getSchemaName(i) + "."
                    + table);
            }
        }
        return out.toString();
    }

    @Test
    public void aStoredColumnNamesItsTable() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE csm (id INT, name VARCHAR(10))");
        statement.execute("INSERT INTO csm VALUES (1, 'abc')");
        assertEquals("TEST_DB.PUBLIC.CSM ;; TEST_DB.PUBLIC.CSM ;; ",
            sources(connection, "SELECT id, name, LENGTH(name) AS len FROM csm"));
    }

    @Test
    public void theHttpDriverNamesItToo() throws SQLException, IOException {
        Assumptions.assumeFalse(LiveSnowflake.enabled(), "starts an HTTP server of its own");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement prepare = http.createStatement()) {
            prepare.execute("CREATE DATABASE csmdb");
            prepare.execute("USE SCHEMA csmdb.public");
            prepare.execute("CREATE TABLE csmt (id INT)");
            assertEquals("CSMDB.PUBLIC.CSMT ;; ", sources(http, "SELECT id, id + 1 AS n FROM csmt"));
        } finally {
            server.stop();
        }
    }
}
