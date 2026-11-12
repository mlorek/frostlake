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

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JDBC 4.2 long-count API answers as the int forms do: Statement.executeLargeBatch,
 * PreparedStatement.executeLargeBatch and PreparedStatement.executeLargeUpdate (documented: the account's driver
 * implements all three). They threw UnsupportedOperationException, the JDK's default, in both drivers.
 */
public class LargeBatchTest extends BaseJdbcTest {

    @Test
    public void aStatementBatchAnswersLongCounts() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE lbt (n INT)");
        try (Statement batch = connection.createStatement()) {
            batch.addBatch("INSERT INTO lbt VALUES (1), (2)");
            batch.addBatch("INSERT INTO lbt VALUES (3)");
            assertArrayEquals(new long[] {2L, 1L}, batch.executeLargeBatch());
        }
    }

    @Test
    public void aPreparedBatchAndUpdateAnswerLongCounts() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE lbt (n INT)");
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO lbt VALUES (?)")) {
            insert.setInt(1, 1);
            insert.addBatch();
            insert.setInt(1, 2);
            insert.addBatch();
            assertArrayEquals(new long[] {1L, 1L}, insert.executeLargeBatch());
        }
        try (PreparedStatement update = connection.prepareStatement("UPDATE lbt SET n = n + ? WHERE n > 0")) {
            update.setInt(1, 10);
            assertEquals(2L, update.executeLargeUpdate());
        }
    }
}
