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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseJdbcTest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A prepared statement's placeholder no value was set for is refused at its place in the text as written,
 * {@code Bind variable ? not set.}, 2049 / 42601, whatever the values before it were set to; one set to NULL is bound
 * (live-verified).
 */
public class UnsetBindRefusalTest extends BaseJdbcTest {

    @Test
    public void anUnsetPlaceholderIsRefusedAtItsPlace() {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                try (PreparedStatement prepared = connection.prepareStatement("SELECT ? AS x, (?) y")) {
                    prepared.setTimestamp(1, Timestamp.valueOf("2024-01-02 03:04:05.123456789"));
                    prepared.executeQuery().close();
                }
            }
        });
        assertEquals("2049|42601|SQL compilation error: error line 1 at position 16\nBind variable ? not set.",
            refused.getErrorCode() + "|" + refused.getSQLState() + "|" + refused.getMessage());
    }

    @Test
    public void aPlaceholderSetToNullIsBound() throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement("SELECT ? AS x, (?) y")) {
            prepared.setInt(1, 1);
            prepared.setNull(2, Types.VARCHAR);
            try (ResultSet rows = prepared.executeQuery()) {
                rows.next();
                assertEquals(1, rows.getInt(1));
                assertEquals(null, rows.getString(2));
            }
        }
    }
}
