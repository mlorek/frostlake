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

import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BOOLEAN has two spellings, one per surface: the driver's {@code getString} prints the account's
 * upper-case {@code TRUE} / {@code FALSE} for every BOOLEAN-typed column — a literal, a comparison, a
 * stored column, an aggregate — while every SQL conversion of the same value to text (TO_VARCHAR, the
 * VARCHAR cast, concatenation, a VARIANT's JSON) is lower-case {@code true} / {@code false}, which is
 * what the driver then prints for THOSE columns, being VARCHAR or VARIANT. {@code getObject} is a
 * Boolean and {@code getBoolean} agrees on both surfaces.
 */
public class BooleanTextJdbcTest extends BaseJdbcTest {

    private ResultSet row(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        assertTrue(rs.next(), sql);
        return rs;
    }

    @Test
    public void aBooleanColumnPrintsUpperCase() throws SQLException {
        final ResultSet rs = row("SELECT TRUE AS t, FALSE AS f, 1 = 1 AS c, NULL::BOOLEAN AS n");
        assertEquals("TRUE", rs.getString(1));
        assertEquals("FALSE", rs.getString(2));
        assertEquals("TRUE", rs.getString("c"));
        assertNull(rs.getString(4));
        assertTrue(rs.wasNull());
        assertEquals(Boolean.TRUE, rs.getObject(1));
        assertEquals(Boolean.FALSE, rs.getObject("f"));
        assertTrue(rs.getBoolean(1));
        assertFalse(rs.getBoolean(2));
        rs.close();
    }

    @Test
    public void aStoredColumnAndItsAggregatesPrintUpperCase() throws SQLException {
        statement.execute("CREATE TABLE bt (id INT, b BOOLEAN, v VARIANT, s VARCHAR)");
        statement.execute("INSERT INTO bt SELECT 1, TRUE, TO_VARIANT(FALSE), TRUE");
        statement.execute("INSERT INTO bt SELECT 2, FALSE, TO_VARIANT(TRUE), FALSE");
        final ResultSet rs = row("SELECT b, v, s FROM bt WHERE id = 1");
        assertEquals("TRUE", rs.getString(1), "the BOOLEAN column");
        assertEquals("false", rs.getString(2), "a VARIANT holding a boolean is its JSON text");
        assertEquals("true", rs.getString(3), "a boolean stored into VARCHAR was converted to lower-case text");
        assertEquals(Boolean.TRUE, rs.getObject(1));
        rs.close();
        final ResultSet agg = row("SELECT BOOLOR_AGG(b), BOOLAND_AGG(b), ANY_VALUE(b) FROM bt WHERE id = 1");
        assertEquals("TRUE", agg.getString(1));
        assertEquals("TRUE", agg.getString(2));
        assertEquals("TRUE", agg.getString(3));
        agg.close();
        final ResultSet rows = statement.executeQuery("SELECT b FROM bt ORDER BY id");
        assertTrue(rows.next());
        assertEquals("TRUE", rows.getString(1));
        assertTrue(rows.next());
        assertEquals("FALSE", rows.getString(1));
        rows.close();
        final ResultSet values = statement.executeQuery("SELECT $1 FROM VALUES (TRUE), (FALSE)");
        assertTrue(values.next());
        assertEquals("TRUE", values.getString(1));
        assertTrue(values.next());
        assertEquals("FALSE", values.getString(1));
        values.close();
    }

    @Test
    public void theSqlTextOfABooleanIsLowerCase() throws SQLException {
        final ResultSet rs = row("""
            SELECT TO_VARCHAR(TRUE), TRUE::VARCHAR, TRUE || '', CONCAT(FALSE, 'x'), TO_CHAR(FALSE),
                   TRUE::TEXT, LOWER(TRUE), UPPER(FALSE), REPLACE(TRUE, 't', 'X'),
                   TRUE::VARCHAR = 'true', TRUE::VARCHAR = 'TRUE', LENGTH(TRUE)
            """);
        assertEquals("true", rs.getString(1));
        assertEquals("true", rs.getString(2));
        assertEquals("true", rs.getString(3));
        assertEquals("falsex", rs.getString(4));
        assertEquals("false", rs.getString(5));
        assertEquals("true", rs.getString(6));
        assertEquals("true", rs.getString(7));
        assertEquals("FALSE", rs.getString(8), "UPPER of the lower-case text");
        assertEquals("Xrue", rs.getString(9));
        assertEquals("TRUE", rs.getString(10), "the text really is lower-case: this comparison holds");
        assertEquals("FALSE", rs.getString(11), "and the upper-case one does not");
        assertEquals("4", rs.getString(12));
        rs.close();
    }

    @Test
    public void aBooleanInsideSemiStructuredDataIsItsJsonText() throws SQLException {
        final ResultSet rs = row("""
            SELECT TO_VARIANT(TRUE), TRUE::VARIANT, PARSE_JSON('true'), PARSE_JSON('{"a":true}'):a,
                   GET(PARSE_JSON('[true]'), 0), TO_JSON(TRUE), OBJECT_CONSTRUCT('a', TRUE),
                   ARRAY_CONSTRUCT(TRUE, FALSE), ARRAY_TO_STRING(ARRAY_CONSTRUCT(TRUE, FALSE), ','),
                   TO_VARIANT(TRUE)::VARCHAR, TO_VARIANT(TRUE) = TRUE
            """);
        assertEquals("true", rs.getString(1));
        assertEquals("true", rs.getString(2));
        assertEquals("true", rs.getString(3));
        assertEquals("true", rs.getString(4));
        assertEquals("true", rs.getString(5));
        assertEquals("true", rs.getString(6));
        assertEquals("{\"a\":true}", rs.getString(7));
        assertEquals("[true,false]", rs.getString(8));
        assertEquals("true,false", rs.getString(9));
        assertEquals("true", rs.getString(10));
        assertEquals("TRUE", rs.getString(11), "the comparison is BOOLEAN again");
        rs.close();
    }
}
