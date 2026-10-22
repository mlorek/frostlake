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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Driver surfaces as the account's driver offers them: no cell reads as a {@link java.sql.Array} or through a type
 * map; setObject binds its value as the target type it names, at the scale a DECIMAL names; setMaxRows bounds the
 * rows a result hands out (all live-verified).
 */
public class DriverSurfaceAgreementTest extends BaseJdbcTest {

    private void refused(final String sql, final boolean byLabel, final boolean typeMap) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            assertThrows(SQLFeatureNotSupportedException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    if (typeMap) {
                        if (byLabel) {
                            rs.getObject("C", new HashMap<String, Class<?>>());
                        } else {
                            rs.getObject(1, new HashMap<String, Class<?>>());
                        }
                    } else if (byLabel) {
                        rs.getArray("C");
                    } else {
                        rs.getArray(1);
                    }
                }
            }, sql);
        }
    }

    /** The bound value's text and its type, as SYSTEM$TYPEOF names it. */
    private String bound(final Object x, final int type, final Integer scale) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("SELECT ?::VARCHAR, SYSTEM$TYPEOF(?)")) {
            for (int i = 1; i <= 2; i++) {
                if (scale == null) {
                    p.setObject(i, x, type);
                } else {
                    p.setObject(i, x, type, scale.intValue());
                }
            }
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                return rs.getString(1) + " / " + rs.getString(2);
            }
        }
    }

    private String rows(final int max, final boolean prepared) throws SQLException {
        final String sql = "SELECT column1 FROM VALUES (1), (2), (3) ORDER BY 1";
        try (Statement s = prepared ? connection.prepareStatement(sql) : connection.createStatement()) {
            s.setMaxRows(max);
            try (ResultSet rs = prepared ? ((PreparedStatement) s).executeQuery() : s.executeQuery(sql)) {
                int read = 0;
                while (rs.next()) {
                    read++;
                }
                return read + " of max " + s.getMaxRows();
            }
        }
    }

    @Test
    public void noCellReadsAsAnArrayOrThroughATypeMap() throws SQLException {
        refused("SELECT [1, 2]::ARRAY AS c", false, false);
        refused("SELECT NULL::ARRAY AS c", false, false);
        refused("SELECT [1, 2]::ARRAY(NUMBER) AS c", true, false);
        refused("SELECT 1 AS c", false, true);
        refused("SELECT 1 AS c", true, true);
    }

    @Test
    public void setObjectBindsItsTargetType() throws SQLException {
        assertEquals("7.2 / NUMBER(2,1)[SB1]", bound(new BigDecimal("7.20"), Types.DECIMAL, Integer.valueOf(1)));
        assertEquals("7 / NUMBER(1,0)[SB1]", bound(new BigDecimal("7"), Types.DECIMAL, Integer.valueOf(2)));
        assertEquals("7.25 / NUMBER(3,2)[SB2]", bound("7.25", Types.DECIMAL, Integer.valueOf(2)));
        assertEquals("7.25 / VARCHAR(4)[LOB]", bound(new BigDecimal("7.25"), Types.VARCHAR, Integer.valueOf(1)));
        assertEquals("5 / VARCHAR(1)[LOB]", bound(Integer.valueOf(5), Types.VARCHAR, null));
        assertEquals("5 / NUMBER(1,0)[SB1]", bound("5", Types.INTEGER, null));
        assertEquals("7.25 / NUMBER(3,2)[SB2]", bound(new BigDecimal("7.25"), Types.NUMERIC, Integer.valueOf(3)));
        assertEquals("7.25 / NUMBER(3,2)[SB2]", bound(new BigDecimal("7.25"), Types.DOUBLE, Integer.valueOf(1)));
        assertEquals("7.2 / NUMBER(2,1)[SB1]", bound(new BigDecimal("7.2"), Types.DECIMAL, null));
    }

    /** A scale that would round is refused; the account's driver fails there with a raw ArithmeticException. */
    @Test
    public void aScaleThatWouldRoundIsRefused() {
        assumeFalse(LiveSnowflake.enabled(), "the account's driver leaks an ArithmeticException here");
        final SQLException rounded = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                bound(new BigDecimal("7.25"), Types.DECIMAL, Integer.valueOf(1));
            }
        });
        assertEquals("Rounding necessary", rounded.getMessage());
        assertEquals("22003", rounded.getSQLState());
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                bound(Double.valueOf(7.5), Types.DECIMAL, Integer.valueOf(0));
            }
        });
    }

    @Test
    public void setMaxRowsBoundsTheRowsHandedOut() throws SQLException {
        assertEquals("1 of max 1", rows(1, false));
        assertEquals("2 of max 2", rows(2, true));
        assertEquals("3 of max 0", rows(0, false));
        assertEquals("3 of max 5", rows(5, false));
    }
}
