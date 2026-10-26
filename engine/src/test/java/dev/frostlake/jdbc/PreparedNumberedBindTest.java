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
import org.junit.jupiter.api.function.Executable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A prepared or callable statement binds a numbered {@code :n} as its n-th parameter, as it binds the n-th
 * {@code ?}: {@code CALL p(:1)} over setInt(1, 7) passes 7 and {@code SELECT :1 + 1} answers 8, and a number no
 * parameter was set for is a bind variable that is not set, refused at its colon (all live-verified through the
 * account's driver).
 */
public class PreparedNumberedBindTest extends BaseJdbcTest {

    @Test
    public void aCallableStatementBindsItsNumberedParameter() throws SQLException {
        statement.execute("CREATE OR REPLACE PROCEDURE pn414(x NUMBER(10,2)) RETURNS NUMBER(10,2) LANGUAGE SQL AS "
            + "'BEGIN RETURN x; END'");
        try (CallableStatement call = connection.prepareCall("CALL pn414(:1)")) {
            call.setInt(1, 7);
            assertTrue(call.execute());
            try (ResultSet rs = call.getResultSet()) {
                rs.next();
                assertEquals("7.00", rs.getString(1));
            }
        }
    }

    @Test
    public void aPreparedStatementBindsItsNumberedParameter() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT :1 + 1")) {
            ps.setInt(1, 7);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("8", rs.getString(1));
            }
        }
    }

    @Test
    public void aNumberNoParameterWasSetForIsRefused() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT :1 + :2")) {
            ps.setInt(1, 7);
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    ps.executeQuery();
                }
            });
            assertTrue(refused.getMessage().contains("Bind variable :2 not set."), refused.getMessage());
        }
    }
}
