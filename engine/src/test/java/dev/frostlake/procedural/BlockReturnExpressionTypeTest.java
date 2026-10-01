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

package dev.frostlake.procedural;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block's result column takes the static type of the expression its RETURN names, over the names in scope:
 * a cursor record's field is its query column's type ({@code rec.c + 1} over a NUMBER(5,2) is NUMBER(6,2)),
 * SQLROWCOUNT is a text that arithmetic reads as a NUMBER(18,5) ({@code SQLROWCOUNT + 1} is NUMBER(19,5)
 * and returns 2.00000) while a bare RETURN of it declares a NUMBER(0,0), and a whole RETURN expression that
 * multiplies a name by 1 is that name's type ({@code i * 1} over a FOR counter is NUMBER(9,0), {@code i * 1
 * + 0} NUMBER(11,0)). An exact value comes back at its column's scale. Every cell is live-verified through the
 * driver's metadata.
 */
public class BlockReturnExpressionTypeTest extends BaseJdbcTest {

    /** The result column's type name, precision and scale, then the value, space-separated. */
    private String returned(final String body) throws SQLException {
        try (ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$")) {
            final ResultSetMetaData md = rs.getMetaData();
            rs.next();
            return md.getColumnTypeName(1) + " " + md.getPrecision(1) + " " + md.getScale(1) + " " + rs.getString(1);
        }
    }

    /** A text result column's type name and the value, its width left out. */
    private String returnedText(final String body) throws SQLException {
        try (ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$")) {
            final ResultSetMetaData md = rs.getMetaData();
            rs.next();
            return md.getColumnTypeName(1) + " " + rs.getString(1);
        }
    }

    @Test
    public void aCursorRecordsFieldIsItsQueryColumnsType() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t (a INT, n NUMBER(5,2), s VARCHAR(10), d DATE)");
        statement.execute("INSERT INTO t VALUES (1, 1.25, 'ab', '2024-01-01')");
        final String typed = "DECLARE c1 CURSOR FOR SELECT 1.25::NUMBER(5,2) AS c; BEGIN FOR rec IN c1 DO RETURN ";
        final String overTable = "DECLARE c1 CURSOR FOR SELECT n, a, s, d FROM t; BEGIN FOR rec IN c1 DO RETURN ";
        final String end = "; END FOR; END;";
        assertEquals("NUMBER 6 2 2.25", returned(typed + "rec.c + 1" + end));
        assertEquals("NUMBER 5 2 1.25", returned(typed + "rec.c" + end));
        assertEquals("NUMBER 6 2 2.50", returned(overTable + "rec.n * 2" + end));
        assertEquals("NUMBER 38 0 2", returned(overTable + "rec.a + 1" + end));
        assertEquals("NUMBER 38 0 1", returned(overTable + "rec.a" + end));
        assertEquals("DATE 10 0 2024-01-02", returned(overTable + "DATEADD(day, 1, rec.d)" + end));
        assertEquals("VARCHAR abx", returnedText(overTable + "rec.s || 'x'" + end));
    }

    @Test
    public void aProductOfANameAndOneIsTheNamesType() throws SQLException {
        final String[][] cells = {
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i * 1; END FOR; END;", "NUMBER 9 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN 1 * i; END FOR; END;", "NUMBER 9 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN (i * 1); END FOR; END;", "NUMBER 9 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i * 1.0; END FOR; END;", "NUMBER 9 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i * 2; END FOR; END;", "NUMBER 10 0 2"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i * 1 + 0; END FOR; END;", "NUMBER 11 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i * i; END FOR; END;", "NUMBER 18 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i / 2; END FOR; END;", "NUMBER 15 6 0.500000"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN -i; END FOR; END;", "NUMBER 9 0 -1"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i * 1.5; END FOR; END;", "NUMBER 11 1 1.5"},
            {"DECLARE x NUMBER(9,0) DEFAULT 5; BEGIN RETURN x * 1; END;", "NUMBER 9 0 5"},
            {"DECLARE x NUMBER(9,0) DEFAULT 5; BEGIN RETURN x * 1 * 1; END;", "NUMBER 9 0 5"},
            {"DECLARE x NUMBER(5,2) DEFAULT 5; BEGIN RETURN x * 1; END;", "NUMBER 5 2 5.00"},
            {"BEGIN RETURN 5 * 1; END;", "NUMBER 2 0 5"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], returned(cell[0]), cell[0]);
        }
    }

    @Test
    public void sqlRowCountIsATextToArithmetic() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t (a INT)");
        final String inserted = "BEGIN INSERT INTO t VALUES (1); RETURN ";
        assertEquals("NUMBER 19 5 2.00000", returned(inserted + "SQLROWCOUNT + 1; END;"));
        assertEquals("NUMBER 18 5 1.00000", returned(inserted + "SQLROWCOUNT * 1; END;"));
        assertEquals("NUMBER 19 5 1.50000", returned(inserted + "SQLROWCOUNT + 0.5; END;"));
        assertEquals("NUMBER 19 5 2.00000", returned(inserted + ":SQLROWCOUNT + 1; END;"));
        assertEquals("NUMBER 0 0 1", returned(inserted + "SQLROWCOUNT; END;"));
        assertEquals("VARCHAR 1", returnedText(inserted + ":SQLROWCOUNT; END;"));
        assertEquals("VARCHAR 1", returnedText(inserted + "SQLROWCOUNT::VARCHAR; END;"));
        assertEquals("NUMBER 19 5 null", returned("BEGIN RETURN SQLROWCOUNT + 1; END;"));
        assertEquals("NUMBER 0 0 null", returned("BEGIN SELECT 1; RETURN SQLROWCOUNT; END;"));
        assertEquals("NUMBER 19 5 2.00000", returned("BEGIN LET x := '1'; RETURN x + 1; END;"));
        assertEquals("NUMBER 19 5 2.00000", returned("DECLARE x VARCHAR DEFAULT '1'; BEGIN RETURN x + 1; END;"));
    }
}
