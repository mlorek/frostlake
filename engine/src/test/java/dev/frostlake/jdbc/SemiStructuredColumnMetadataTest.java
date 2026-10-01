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
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseJdbcTest;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A semi-structured column reaches a JDBC client as text, as Snowflake's driver hands it over (live-verified):
 * <ul>
 *   <li>a VARIANT, an OBJECT or an ARRAY — structured or not — is type code VARCHAR and {@code java.lang.String},
 *       a MAP is named OBJECT, and {@code getObject} reads the text;</li>
 *   <li>a VECTOR is named without its parameters, reports the driver's own code 50003, has no class name to give,
 *       and reads as text too.</li>
 * </ul>
 */
public class SemiStructuredColumnMetadataTest extends BaseJdbcTest {

    /** Each column's name, type code and class name, then its first row's object class, a bar between columns. */
    private String metadata(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            assertTrue(rs.next());
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (i > 1) {
                    out.append(" | ");
                }
                final Object value = rs.getObject(i);
                out.append(md.getColumnTypeName(i)).append(' ').append(md.getColumnType(i)).append(' ')
                    .append(md.getColumnClassName(i)).append(' ').append(value == null ? "null" : value.getClass().getName());
            }
            return out.toString();
        }
    }

    @Test
    public void semiStructuredColumnsAreText() throws SQLException {
        final String text = " " + Types.VARCHAR + " java.lang.String java.lang.String";
        assertEquals("VARIANT" + text + " | OBJECT" + text + " | ARRAY" + text,
            metadata("SELECT TO_VARIANT(1), OBJECT_CONSTRUCT('a', 1), ARRAY_CONSTRUCT(1)"));
        assertEquals("OBJECT" + text + " | ARRAY" + text + " | OBJECT" + text,
            metadata("SELECT {'a': 1}::MAP(VARCHAR, INT), [1,2]::ARRAY(INT), {'a': 1}::OBJECT(a INT)"));
        statement.execute("CREATE OR REPLACE TABLE ssm (v VARIANT, o OBJECT, a ARRAY)");
        statement.execute("INSERT INTO ssm SELECT PARSE_JSON('\"x\"'), OBJECT_CONSTRUCT(), ARRAY_CONSTRUCT()");
        assertEquals("VARIANT" + text + " | OBJECT" + text + " | ARRAY" + text, metadata("SELECT v, o, a FROM ssm"));
        assertEquals("VARIANT " + Types.VARCHAR + " java.lang.String null", metadata("SELECT NULL::VARIANT"));
    }

    @Test
    public void aVectorHasTheDriversOwnCodeAndNoClass() throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT [1,2]::VECTOR(INT, 2), [1.5]::VECTOR(FLOAT, 1)")) {
            final ResultSetMetaData md = rs.getMetaData();
            assertTrue(rs.next());
            assertEquals("VECTOR", md.getColumnTypeName(1));
            assertEquals("VECTOR", md.getColumnTypeName(2));
            assertEquals(50003, md.getColumnType(1));
            assertEquals(50003, md.getColumnType(2));
            final SQLFeatureNotSupportedException refused = assertThrows(SQLFeatureNotSupportedException.class,
                new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        md.getColumnClassName(1);
                    }
                });
            assertEquals("No corresponding Java type is found for java.sql.Type: 50003", refused.getMessage());
            assertEquals(String.class, rs.getObject(1).getClass());
            assertEquals(String.class, rs.getObject(2).getClass());
        }
    }
}
