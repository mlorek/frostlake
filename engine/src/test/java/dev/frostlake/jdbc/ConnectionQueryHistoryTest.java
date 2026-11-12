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

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each in-process JDBC connection is a session with its own query history, as an HTTP session is:
 * RESULT_SCAN(LAST_QUERY_ID()) on one connection reads that connection's last result, whatever another connection to
 * the same engine ran since. The connections shared one history, so it read the other connection's rows. Engine only:
 * it opens in-process connections.
 */
public class ConnectionQueryHistoryTest {

    private static String cell(final Statement statement, final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void eachConnectionReadsItsOwnLastResult() throws SQLException {
        final String url = "jdbc:frostlake:direct:queryhistory" + System.nanoTime();
        try (Connection first = DriverManager.getConnection(url);
             Connection second = DriverManager.getConnection(url)) {
            final Statement one = first.createStatement();
            final Statement two = second.createStatement();
            assertEquals("conn1-first", cell(one, "SELECT 'conn1-first' AS x"));
            assertEquals("conn2-first", cell(two, "SELECT 'conn2-first' AS x"));
            assertEquals("conn1-second", cell(one, "SELECT 'conn1-second' AS x"));
            assertEquals("conn2-first", cell(two, "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            assertEquals("conn1-second", cell(one, "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        }
    }
}
