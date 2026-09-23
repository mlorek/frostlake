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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a client reads through the driver: getObject(int, Class) routed through the typed getters, and the
 * array functions whose column a client reads as an array rather than as quoted JSON text.
 */
public class TypedObjectReadsTest extends BaseJdbcTest {

    private ResultSet query(final String sql) throws SQLException {
        final Statement statement = connection.createStatement();
        final ResultSet rs = statement.executeQuery(sql);
        rs.next();
        return rs;
    }

    /** A supported class reads the value through its typed getter. */
    @Test
    public void aSupportedClassReadsThroughItsGetter() throws SQLException {
        final ResultSet rs = query("SELECT CAST(7 AS NUMBER(38,0))");
        assertEquals(Long.valueOf(7L), rs.getObject(1, Long.class));
        assertEquals(Integer.valueOf(7), rs.getObject(1, Integer.class));
        assertEquals("7", rs.getObject(1, String.class));
        assertEquals(new BigDecimal("7"), rs.getObject(1, BigDecimal.class));
    }

    /** A NULL reads as the boxed primitive's zero, with wasNull set, and as null for the object classes. */
    @Test
    public void aNullReadsAsTheGettersDefault() throws SQLException {
        final ResultSet rs = query("SELECT CASE WHEN FALSE THEN 7 END");
        assertEquals(Long.valueOf(0L), rs.getObject(1, Long.class));
        assertTrue(rs.wasNull());
        assertEquals(Boolean.FALSE, rs.getObject(1, Boolean.class));
        assertEquals(Double.valueOf(0.0), rs.getObject(1, Double.class));
        assertNull(rs.getObject(1, String.class));
        assertNull(rs.getObject(1, BigDecimal.class));
    }

    /** Every other class is refused, NULL cell or not. */
    @Test
    public void anUnsupportedClassIsRefused() throws SQLException {
        final ResultSet rs = query("SELECT CAST('2020-01-02' AS DATE)");
        final String message = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                rs.getObject(1, LocalDate.class);
            }
        }).getMessage();
        assertEquals("Type passed to 'getObject(int columnIndex,Class<T> type)' is unsupported. "
            + "Type: java.time.LocalDate", message);
    }

    /** SPLIT, STRTOK_TO_ARRAY and REGEXP_SUBSTR_ALL arrive as the array, not as its JSON text quoted. */
    @Test
    public void theArrayFunctionsArriveAsArrays() throws SQLException {
        final ResultSet rs = query("SELECT SPLIT('a,b', ','), STRTOK_TO_ARRAY('a b'), REGEXP_SUBSTR_ALL('ab', '.')");
        assertEquals("[\"a\",\"b\"]", rs.getString(1).replace("\n", "").replace(" ", ""));
        assertEquals("[\"a\",\"b\"]", rs.getString(2).replace("\n", "").replace(" ", ""));
        assertEquals("[\"a\",\"b\"]", rs.getString(3).replace("\n", "").replace(" ", ""));
    }
}
