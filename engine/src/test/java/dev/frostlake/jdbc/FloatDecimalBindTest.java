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
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Float bound with setObject as DECIMAL or NUMERIC binds its own decimal text, as every other way of binding it
 * does: 1.21f is 1.21, so it matches a NUMBER(10,2) holding 1.21 and needs no rounding at scale 2. Widened to a
 * double first it bound 1.2100000381469727, which matched nothing and was refused at a scale. The account's driver
 * binds String.valueOf(x) under the target type (documented in its source); unmeasured on live.
 */
public class FloatDecimalBindTest extends BaseJdbcTest {

    /** The first cell of the statement's one row, its placeholder bound as given. */
    private String answer(final String sql, final Object value, final int type, final Integer scale)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (scale == null) {
                ps.setObject(1, value, type);
            } else {
                ps.setObject(1, value, type, scale.intValue());
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    public void aFloatBoundAsADecimalMatchesItsValue() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE fdb (n NUMBER(10,2))");
        statement.execute("INSERT INTO fdb VALUES (1.21)");
        final String count = "SELECT COUNT(*) FROM fdb WHERE n = ?";
        assertEquals("1", answer(count, Float.valueOf(1.21f), Types.DECIMAL, null));
        assertEquals("1", answer(count, Float.valueOf(1.21f), Types.NUMERIC, null));
        assertEquals("1", answer(count, Float.valueOf(1.21f), Types.DECIMAL, Integer.valueOf(2)));
        assertEquals("1", answer(count, Float.valueOf(1.21f), Types.NUMERIC, Integer.valueOf(2)));
        assertEquals("1.21", answer("SELECT ?::VARCHAR", Float.valueOf(1.21f), Types.DECIMAL, null));
    }

    @Test
    public void aDoubleBoundAsADecimalKeepsItsValue() throws SQLException {
        assertEquals("1.21", answer("SELECT ?::VARCHAR", Double.valueOf(1.21), Types.DECIMAL, null));
        assertEquals("1.21", answer("SELECT ?::VARCHAR", Double.valueOf(1.21), Types.NUMERIC, Integer.valueOf(2)));
    }
}
