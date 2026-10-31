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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every getter of a result set refuses a label no column carries and an index past the columns with an
 * SQLException, as the JDBC contract and the account's driver do: the label getters with and without a Calendar, and
 * the index getters at 0 and past the last column. Both of Frostlake's transports answer so.
 */
public class GetterRefusalTest extends BaseDatabaseTest {

    private static final String QUERY = "SELECT '2024-01-02'::DATE AS \"d\", 1 AS n";

    @Test
    public void anUnknownLabelOrIndexIsAnSqlException() throws SQLException, IOException {
        if (isLiveSnowflake()) {
            try (Connection live = LiveSnowflake.openUnprepared()) {
                assertGettersRefuse(live);
            }
            return;
        }
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:getter_refusal")) {
            assertGettersRefuse(direct);
        }
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port)) {
            assertGettersRefuse(http);
        } finally {
            server.stop();
        }
    }

    private static void assertGettersRefuse(final Connection connection) throws SQLException {
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(QUERY)) {
            rows.next();
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getTimestamp("nope");
                }
            });
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getTimestamp("nope", utc);
                }
            });
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getTime("nope", utc);
                }
            });
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getString("nope");
                }
            });
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getTimestamp(0);
                }
            });
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getString(9);
                }
            });
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    rows.getObject(3);
                }
            });
        }
    }
}
