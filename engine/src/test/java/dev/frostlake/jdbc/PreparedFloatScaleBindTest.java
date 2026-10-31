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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A double or a float bound as a rounding scale is a FLOAT scale: ROUND(1.2345, ?) over setDouble(2.0) rounds in
 * FLOAT arithmetic and answers the FLOAT 1.23, as TRUNC does, where an exact scale keeps the NUMBER (all
 * live-verified). Each answer is read on the driver's default results (see {@link DefaultResultFormat}).
 */
public class PreparedFloatScaleBindTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        DefaultResultFormat.use(statement);
    }

    /** The first column of the statement's one row, each placeholder bound to the value. */
    private String bound(final String sql, final Object value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            final int count = sql.length() - sql.replace("?", "").length();
            for (int i = 1; i <= count; i++) {
                ps.setObject(i, value);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    public void aDoubleScaleRoundsInFloatArithmetic() throws SQLException {
        statement.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertEquals("1.23", bound("SELECT ROUND(1.2345, ?) AS r", Double.valueOf(2.0)));
        assertEquals("FLOAT[DOUBLE]", bound("SELECT SYSTEM$TYPEOF(ROUND(1.2345, ?)) AS t", Double.valueOf(2.0)));
        assertEquals("1.23", bound("SELECT TRUNC(1.2345, ?) AS r", Double.valueOf(2.0)));
        assertEquals("1.23", bound("SELECT ROUND(1.2345, ?) AS r /* f */", Float.valueOf(2.0f)));
        assertEquals("1.2332882874656679", bound("SELECT ROUND(1.2345, ?) AS r /* 2.5 */", Double.valueOf(2.5)));
    }

    @Test
    public void anExactScaleKeepsTheNumber() throws SQLException {
        statement.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertEquals("1.23", bound("SELECT ROUND(1.2345, ?) AS r /* i */", Integer.valueOf(2)));
        assertEquals("1.23", bound("SELECT ROUND(1.2345, ?) AS r /* d */", new BigDecimal("2")));
        assertEquals("1.2300", bound("SELECT ROUND(1.2345, ?) AS r /* s */", "2"));
    }
}
