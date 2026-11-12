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
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The colon between an object constant's key and its value is no numbered bind: in a prepared statement and in
 * EXECUTE IMMEDIATE … USING, {@code {'k': 1}} keeps its value 1 though a first parameter is bound. Its {@code : 1} was
 * taken for the bind {@code :1}, and the statement failed with a syntax error.
 */
public class ObjectConstantColonBindTest extends BaseJdbcTest {

    private static String row(final ResultSet rs) throws SQLException {
        rs.next();
        return rs.getString(1) + "|" + rs.getString(2);
    }

    @Test
    public void aPreparedStatementKeepsItsObjectConstant() throws SQLException {
        for (final String constant : new String[] {"{'k': 1}", "{'k':1}"}) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT TO_JSON(" + constant + ") o, ? x")) {
                ps.setString(1, "val");
                try (ResultSet rs = ps.executeQuery()) {
                    assertEquals("{\"k\":1}|val", row(rs), constant);
                }
            }
        }
    }

    @Test
    public void aUsingListKeepsTheObjectConstantToo() throws SQLException {
        for (final String value : new String[] {"1", "3"}) {
            try (ResultSet rs = statement.executeQuery("DECLARE r RESULTSET; v VARCHAR DEFAULT 'z'; BEGIN "
                    + "r := (EXECUTE IMMEDIATE 'SELECT TO_JSON({''a'': " + value + "}) o, ? x' USING (v)); "
                    + "RETURN TABLE(r); END;")) {
                assertEquals("{\"a\":" + value + "}|z", row(rs), value);
            }
        }
    }
}
