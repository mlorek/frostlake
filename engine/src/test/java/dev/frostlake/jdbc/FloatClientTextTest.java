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

/**
 * A FLOAT's text through the DRIVER: Java's own {@code Double.toString} of the double, as the account's driver
 * reads it on its default results, and not the SQL text.
 *
 * <p>★ TWO SURFACES, TWO SPELLINGS. TO_VARCHAR writes a square root as {@code 1.414213562}, Snowflake's width;
 * the driver's getString hands back {@code 1.4142135623730951}, because on the ARROW results it asks for by
 * default the account's driver is sent the double and formats it itself. On JSON results it hands back the
 * server's text instead, so this class puts the live session on the default (see {@link DefaultResultFormat}).
 * These cells are read through the driver on BOTH engines, so the comparison is driver-to-driver.
 *
 * <p>★ THE DECLARED TYPE DECIDES, not the value's carrier: a FLOAT-declared value can still arrive in an exact
 * carrier — computed exactly, or restored from an older snapshot — and asking the object what it is would spell it
 * the wrong way.
 *
 * <p>NEGATIVE ZERO and the carrier itself are pinned by the FLOAT carrier cells beside this class.
 */
public class FloatClientTextTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        DefaultResultFormat.use(statement);
    }

    /** One FLOAT expression as the DRIVER renders it. */
    private String text(final String expression) throws SQLException {
        final ResultSet rs = statement.executeQuery("SELECT " + expression + " AS v");
        rs.next();
        final String value = rs.getString(1);
        rs.close();
        return value;
    }

    /** ★ The shortest text that reads back as the double, where the SQL text keeps ten digits. */
    @Test
    public void theTextIsJavasOwnOfTheDouble() throws SQLException {
        assertEquals("1.4142135623730951", text("SQRT(2)::FLOAT"),
            "the driver formats the double it is sent");
        assertEquals("1.414213562", text("TO_VARCHAR(SQRT(2))"), "★ while the SQL text keeps Snowflake's width");
        assertEquals("0.30000000000000004", text("0.1::FLOAT + 0.2::FLOAT"));
        assertEquals("0.1", text("0.1::FLOAT"));
        assertEquals("0.001", text("0.001::FLOAT"));
        assertEquals("1.0", text("1.0::FLOAT"), "★ a whole value keeps its point and one zero");
        assertEquals("9999999.0", text("9999999::FLOAT"));
    }

    /** ★ Below a thousandth and from ten million up, Java's exponent form takes over. */
    @Test
    public void largeAndSmallMagnitudesTakeJavasExponent() throws SQLException {
        assertEquals("1.0E7", text("10000000::FLOAT"), "an upper-case E with no sign, after a point");
        assertEquals("1.0E18", text("1e18::FLOAT"));
        assertEquals("1.0E-4", text("0.0001::FLOAT"));
        assertEquals("1.5E-7", text("1.5e-7::FLOAT"));
        assertEquals("1.7976931348623157E308", text("1.7976931348623157e308::FLOAT"),
            "every digit of the largest double");
    }

    /** The non-finites, in Java's words for them. */
    @Test
    public void theNonFinitesAreJavasWords() throws SQLException {
        assertEquals("NaN", text("'NaN'::FLOAT"));
        assertEquals("Infinity", text("'inf'::FLOAT"), "where TO_VARCHAR writes inf");
        assertEquals("-Infinity", text("'-inf'::FLOAT"));
    }

    /** ★ A stored FLOAT COLUMN reads the same way. */
    @Test
    public void aStoredColumnReadsTheSameWay() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE fct (f FLOAT)");
        statement.execute("INSERT INTO fct SELECT SQRT(2)::FLOAT");
        final ResultSet rs = statement.executeQuery("SELECT f FROM fct");
        rs.next();
        assertEquals("1.4142135623730951", rs.getString(1),
            "★ the column's declared type says how to spell it");
        rs.close();
        statement.execute("DROP TABLE fct");
    }
}
