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

package dev.frostlake.features;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An integer literal past a long's range but within NUMBER(38,0)'s 38 digits is an exact number on every
 * path that reads a literal token itself, not only in a query: a block's RETURN, LET, DECLARE default and
 * arithmetic keep every digit at the literal's own precision, a NUMBER(38,0) variable holds it whole, a
 * column DEFAULT fills it and a session variable takes it. Past 38 digits of a result the block refuses the
 * value's range. A session parameter takes no whole number past 32 bits, which is refused as the
 * parameter's invalid value, echoed as written. Every cell is live-verified.
 */
public class WideIntegerLiteralPathTest extends BaseJdbcTest {

    private static final String WIDE = "12345678901234567890123";

    /** The block's result column type name, precision and scale, then its value as text, or the refusal on one line. */
    private String returned(final String body) {
        try (ResultSet rs = statement.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$")) {
            final ResultSetMetaData md = rs.getMetaData();
            rs.next();
            return md.getColumnTypeName(1) + " " + md.getPrecision(1) + " " + md.getScale(1) + " " + rs.getString(1);
        } catch (final SQLException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The statement's refusal on one line, or "accepted". */
    private String refusal(final String sql) {
        try {
            statement.execute(sql);
            return "accepted";
        } catch (final SQLException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String firstCell(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void aBlockKeepsEveryDigitOfAWideLiteral() {
        final String[][] cells = {
            {"BEGIN RETURN " + WIDE + " * 10; END;", "NUMBER 25 0 123456789012345678901230"},
            {"BEGIN RETURN " + WIDE + "; END;", "NUMBER 23 0 " + WIDE},
            {"BEGIN RETURN " + WIDE + " + 1; END;", "NUMBER 24 0 12345678901234567890124"},
            {"BEGIN RETURN " + WIDE + " - 1; END;", "NUMBER 24 0 12345678901234567890122"},
            {"BEGIN RETURN -" + WIDE + " * 10; END;", "NUMBER 25 0 -123456789012345678901230"},
            {"BEGIN RETURN " + WIDE + " / 10; END;", "NUMBER 29 6 1234567890123456789012.300000"},
            {"BEGIN RETURN " + WIDE + " % 7; END;", "NUMBER 23 0 3"},
            {"BEGIN RETURN ABS(-" + WIDE + "); END;", "NUMBER 23 0 " + WIDE},
            {"BEGIN RETURN " + WIDE + " * 1.5; END;", "NUMBER 25 1 18518518351851851835184.5"},
            {"BEGIN RETURN 9223372036854775808; END;", "NUMBER 19 0 9223372036854775808"},
            {"BEGIN RETURN -9223372036854775808 - 1; END;", "NUMBER 20 0 -9223372036854775809"},
            {"BEGIN RETURN 99999999999999999999999999999999999999; END;",
                "NUMBER 38 0 99999999999999999999999999999999999999"},
            {"BEGIN RETURN -99999999999999999999999999999999999999 - 1; END;",
                "NUMBER 38 0 -100000000000000000000000000000000000000"},
            {"BEGIN IF (" + WIDE + " * 10 > 1) THEN RETURN 1; END IF; END;", "NUMBER 1 0 1"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], returned(cell[0]), cell[0]);
        }
    }

    @Test
    public void aScriptingVariableHoldsAWideLiteralWhole() {
        final String[][] cells = {
            {"BEGIN LET x := " + WIDE + "; RETURN x; END;", "NUMBER 23 0 " + WIDE},
            {"BEGIN LET x := " + WIDE + "; RETURN x * 10; END;", "NUMBER 38 0 123456789012345678901230"},
            {"BEGIN LET x := " + WIDE + " * 10; RETURN x; END;", "NUMBER 25 0 123456789012345678901230"},
            {"BEGIN LET x NUMBER(38,0) := " + WIDE + " * 10; RETURN x; END;", "NUMBER 38 0 123456789012345678901230"},
            {"DECLARE x NUMBER(38,0) DEFAULT " + WIDE + "; BEGIN RETURN x; END;", "NUMBER 38 0 " + WIDE},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], returned(cell[0]), cell[0]);
        }
    }

    @Test
    public void aResultPastThirtyEightDigitsIsOutOfRange() {
        final String uncaught = "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "Number out of representable range: type FIXED[SB16](38,0){not null}, value ";
        assertEquals(uncaught + "1e+39", returned("BEGIN RETURN 99999999999999999999999999999999999999 * 10; END;"));
        assertEquals(uncaught + "1.52416e+44", returned("BEGIN RETURN " + WIDE + " * " + WIDE + "; END;"));
    }

    @Test
    public void aColumnDefaultAndASessionVariableTakeAWideLiteral() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t (a NUMBER(38,0) DEFAULT " + WIDE + ", b INT)");
        statement.execute("INSERT INTO t (b) VALUES (1)");
        assertEquals(WIDE, firstCell("SELECT a FROM t"));
        statement.execute("SET wide_literal_value = " + WIDE);
        assertEquals(WIDE, firstCell("SELECT $wide_literal_value"));
    }

    @Test
    public void aSessionParameterTakesNoWholeNumberPastThirtyTwoBits() {
        final String invalid = "SQL compilation error:|invalid value [%s] for parameter '%s'";
        try {
            assertEquals(String.format(invalid, WIDE, "LOCK_TIMEOUT"), refusal("ALTER SESSION SET LOCK_TIMEOUT = " + WIDE));
            assertEquals(String.format(invalid, "9223372036854775808", "LOCK_TIMEOUT"),
                refusal("ALTER SESSION SET LOCK_TIMEOUT = 9223372036854775808"));
            assertEquals(String.format(invalid, "9223372036854775807", "LOCK_TIMEOUT"),
                refusal("ALTER SESSION SET LOCK_TIMEOUT = 9223372036854775807"));
            assertEquals(String.format(invalid, "2147483648", "LOCK_TIMEOUT"),
                refusal("ALTER SESSION SET LOCK_TIMEOUT = 2147483648"));
            assertEquals(String.format(invalid, "2147483648", "JSON_INDENT"), refusal("ALTER SESSION SET JSON_INDENT = 2147483648"));
            assertEquals(String.format(invalid, WIDE, "WEEK_START"), refusal("ALTER SESSION SET WEEK_START = " + WIDE));
            assertEquals("accepted", refusal("ALTER SESSION SET LOCK_TIMEOUT = 2147483647"));
        } finally {
            refusal("ALTER SESSION UNSET LOCK_TIMEOUT");
        }
    }
}
