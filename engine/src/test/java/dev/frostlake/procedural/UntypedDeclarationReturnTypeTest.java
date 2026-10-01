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
 * An untyped declaration — {@code LET x := …} or {@code DECLARE x DEFAULT …} — takes a NUMBER(38,0) from any
 * whole-number initialiser, a FOR counter and a NUMBER(10,0) name included, and a FLOAT from a scaled one. A
 * bare RETURN of such a name, anywhere in the block and parenthesised or not, reports the initialiser's own
 * type instead ({@code 1} is NUMBER(1,0), a NUMBER(10,4) name NUMBER(10,4)) and returns the value at that type,
 * until the name is assigned again; bound as {@code :x}, and in any expression, the name is its declared type.
 * Every cell is live-verified through the driver's metadata.
 */
public class UntypedDeclarationReturnTypeTest extends BaseJdbcTest {

    /** The result column's type name, precision and scale, then the value, space-separated. */
    private String returned(final String body) throws SQLException {
        try (ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$")) {
            final ResultSetMetaData md = rs.getMetaData();
            rs.next();
            return md.getColumnTypeName(1) + " " + md.getPrecision(1) + " " + md.getScale(1) + " " + rs.getString(1);
        }
    }

    /** The declared type SYSTEM$TYPEOF reports, without its storage tag. */
    private String typeOf(final String body) throws SQLException {
        try (ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$")) {
            rs.next();
            final String typed = rs.getString(1);
            return typed.substring(0, typed.indexOf('['));
        }
    }

    @Test
    public void aWholeNumberNameDeclaresANumber38() throws SQLException {
        assertEquals("NUMBER(38,0)", typeOf("BEGIN FOR i IN 1 TO 2 DO LET y := i; RETURN SYSTEM$TYPEOF(:y); END FOR; END;"));
        assertEquals("NUMBER(38,0)",
            typeOf("DECLARE x NUMBER(10,0) DEFAULT 5; BEGIN LET y := x; RETURN SYSTEM$TYPEOF(:y); END;"));
        assertEquals("FLOAT", typeOf("DECLARE x NUMBER(10,4) DEFAULT 1.5; BEGIN LET y := x; RETURN SYSTEM$TYPEOF(:y); END;"));
        assertEquals("NUMBER(38,0)", typeOf("BEGIN LET x := 1; LET z := x; RETURN SYSTEM$TYPEOF(:z); END;"));
        assertEquals("NUMBER 38 0 2", returned("BEGIN FOR i IN 1 TO 2 DO LET y := i; RETURN y + 1; END FOR; END;"));
        assertEquals("NUMBER 38 0 1", returned("BEGIN FOR i IN 1 TO 2 DO LET y := i; LET z := y; RETURN z; END FOR; END;"));
        assertEquals("NUMBER 38 0 6", returned("DECLARE x NUMBER(10,0) DEFAULT 5; BEGIN LET y := x; RETURN y + 1; END;"));
        assertEquals("DOUBLE 0 0 2.5", returned("DECLARE x NUMBER(10,4) DEFAULT 1.5; BEGIN LET y := x; RETURN y + 1; END;"));
    }

    @Test
    public void aBareReturnReportsTheInitialisersOwnType() throws SQLException {
        final String[][] cells = {
            {"BEGIN FOR i IN 1 TO 2 DO LET y := i; RETURN y; END FOR; END;", "NUMBER 9 0 1"},
            {"BEGIN LET x := 1; RETURN x; END;", "NUMBER 1 0 1"},
            {"BEGIN LET x := 12345; RETURN x; END;", "NUMBER 5 0 12345"},
            {"BEGIN LET x := -5; RETURN x; END;", "NUMBER 1 0 -5"},
            {"BEGIN LET x := 1.5; RETURN x; END;", "NUMBER 2 1 1.5"},
            {"BEGIN LET x := 1.50; RETURN x; END;", "NUMBER 2 1 1.5"},
            {"BEGIN LET x := 1e2; RETURN x; END;", "NUMBER 3 0 100"},
            {"BEGIN LET x := 1 + 1; RETURN x; END;", "NUMBER 2 0 2"},
            {"BEGIN LET x := (SELECT 1.5); RETURN x; END;", "NUMBER 2 1 1.5"},
            {"DECLARE x NUMBER(10,4) DEFAULT 1.5; BEGIN LET y := x; RETURN y; END;", "NUMBER 10 4 1.5000"},
            {"DECLARE x NUMBER(10,0) DEFAULT 5; BEGIN LET y := x; RETURN y; END;", "NUMBER 10 0 5"},
            {"DECLARE x DEFAULT 1; BEGIN RETURN x; END;", "NUMBER 1 0 1"},
            {"DECLARE x DEFAULT 1.5; BEGIN RETURN x; END;", "NUMBER 2 1 1.5"},
            {"BEGIN LET x := 1; RETURN (x); END;", "NUMBER 1 0 1"},
            {"BEGIN LET x := 1; IF (TRUE) THEN RETURN x; END IF; END;", "NUMBER 1 0 1"},
            {"BEGIN LET x := 1; FOR i IN 1 TO 1 DO RETURN x; END FOR; END;", "NUMBER 1 0 1"},
            {"BEGIN LET x := 1; BEGIN RETURN x; END; END;", "NUMBER 1 0 1"},
            {"BEGIN LET x := 1; LET y := 2; y := 3; RETURN x; END;", "NUMBER 1 0 1"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], returned(cell[0]), cell[0]);
        }
    }

    @Test
    public void aBoundReassignedOrTypedNameReportsItsDeclaredType() throws SQLException {
        final String[][] cells = {
            {"BEGIN LET x := 1; RETURN :x; END;", "NUMBER 38 0 1"},
            {"BEGIN LET x := 1; x := 123456; RETURN x; END;", "NUMBER 38 0 123456"},
            {"BEGIN LET x := 1; x := x; RETURN x; END;", "NUMBER 38 0 1"},
            {"BEGIN FOR i IN 1 TO 2 DO LET y := i; y := 5; RETURN y; END FOR; END;", "NUMBER 38 0 5"},
            {"BEGIN LET x INT := 1; RETURN x; END;", "NUMBER 38 0 1"},
            {"BEGIN LET x := 1; RETURN x + 1; END;", "NUMBER 38 0 2"},
            {"BEGIN LET x := 1; LET z := x; RETURN z; END;", "NUMBER 38 0 1"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], returned(cell[0]), cell[0]);
        }
    }
}
