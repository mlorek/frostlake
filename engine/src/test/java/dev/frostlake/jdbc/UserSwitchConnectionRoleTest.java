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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatabaseEngine.setCurrentUser leaves no role in use that the new user may not use, the primary role included, which
 * gives way to PUBLIC: a JDBC connection's own role, set by USE ROLE, follows the switch at its next statement as the
 * engine's own session's does. The connection kept the former user's role, and the new user read what only that role
 * may. Engine only: it switches users through the embedding API.
 */
public class UserSwitchConnectionRoleTest {

    private static String cell(final Statement statement, final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void theFormerUsersRoleGivesWay() throws SQLException {
        final String url = "jdbc:frostlake:direct:userswitch" + System.nanoTime();
        try (Connection connection = DriverManager.getConnection(url)) {
            final DatabaseEngine engine = connection.unwrap(DatabaseEngine.class);
            final Statement statement = connection.createStatement();
            for (final String sql : new String[] {"CREATE DATABASE usw", "CREATE TABLE usw.public.payroll (who STRING)",
                    "INSERT INTO usw.public.payroll VALUES ('ceo')", "CREATE ROLE hr", "CREATE ROLE clerk",
                    "CREATE USER u_hr", "CREATE USER u_clerk", "GRANT ROLE hr TO USER u_hr",
                    "GRANT ROLE clerk TO USER u_clerk", "GRANT USAGE ON DATABASE usw TO ROLE hr",
                    "GRANT USAGE ON SCHEMA usw.public TO ROLE hr",
                    "GRANT SELECT ON TABLE usw.public.payroll TO ROLE hr"}) {
                statement.execute(sql);
            }
            engine.setCurrentUser("U_HR");
            statement.execute("USE ROLE hr");
            assertEquals("HR", cell(statement, "SELECT CURRENT_ROLE()"));
            engine.setCurrentUser("U_CLERK");
            assertEquals("U_CLERK", cell(statement, "SELECT CURRENT_USER()"));
            assertEquals("PUBLIC", cell(statement, "SELECT CURRENT_ROLE()"));
            assertTrue(!cell(statement, "SELECT CURRENT_AVAILABLE_ROLES()").contains("HR"));
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    cell(statement, "SELECT * FROM usw.public.payroll");
                }
            });
            assertTrue(refused.getMessage().contains("PAYROLL"), refused.getMessage());
            statement.execute("USE ROLE clerk");
            assertEquals("CLERK", cell(statement, "SELECT CURRENT_ROLE()"));
        }
    }
}
