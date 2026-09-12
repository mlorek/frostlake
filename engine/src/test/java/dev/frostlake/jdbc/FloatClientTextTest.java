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
 * A FLOAT's text through the DRIVER — the same width the SQL text uses.
 *
 * <p>★ ONE VALUE, ONE SPELLING. TO_VARCHAR over a FLOAT already used Snowflake's width; the driver's
 * getString did not, and returned Java's {@code Double.toString} instead — so the same value read two
 * ways gave two answers and neither could claim to be its text. Live's driver returns the SQL text,
 * and these cells are read through the driver on BOTH engines so the comparison is driver-to-driver
 * rather than engine-to-driver.
 *
 * <p>★ THE DECLARED TYPE DECIDES, not the value's carrier: a FLOAT-declared value can still arrive in
 * an exact carrier — computed exactly, or restored from an older snapshot — and asking the object what
 * it is would spell it the wrong way.
 *
 * <p>NEGATIVE ZERO and the carrier itself are pinned by the FLOAT carrier cells beside this class.
 */
public class FloatClientTextTest extends BaseJdbcTest {

    /** One FLOAT expression as the DRIVER renders it. */
    private String text(final String expression) throws SQLException {
        final ResultSet rs = statement.executeQuery("SELECT " + expression + " AS v");
        rs.next();
        final String value = rs.getString(1);
        rs.close();
        return value;
    }

    /** ★ The ordinary range: ten significant digits, no exponent. */
    @Test
    public void theplainRangeKeepsTenDigits() throws SQLException {
        assertEquals("1.414213562", text("SQRT(2)::FLOAT"),
            "the driver returns the SQL text, not Java's seventeen-digit shortest form");
        assertEquals("0.1", text("0.1::FLOAT"));
        assertEquals("0.0001", text("0.0001::FLOAT"));
        assertEquals("1", text("1.0::FLOAT"),
            "★ a whole value carries no decimal point at all");
    }

    /** ★ Past the plain range the exponent form takes over, with its own width. */
    @Test
    public void thescientificRangeWidensToFifteen() throws SQLException {
        assertEquals("1.4142135623731e+16", text("1.4142135623730951e16::FLOAT"),
            "lower-case e and an explicit sign, with trailing zeros dropped");
        assertEquals("1.4142135623731e+18", text("1.4142135623730951e18::FLOAT"));
        assertEquals("1.23456789012346e+18", text("1234567890123456789::FLOAT"));
        assertEquals("1.414213562e-16", text("1.4142135623730951e-16::FLOAT"),
            "★ and a small magnitude is back to ten digits — the width follows the exponent");
    }

    /** The non-finites, which the driver spells as the SQL text does. */
    @Test
    public void thenonFinitesKeepTheirWords() throws SQLException {
        assertEquals("NaN", text("'NaN'::FLOAT"));
        assertEquals("inf", text("'inf'::FLOAT"), "lower case, as TO_VARCHAR writes it");
        assertEquals("-inf", text("'-inf'::FLOAT"));
    }

    /** ★ A stored FLOAT COLUMN reads the same way. */
    @Test
    public void astoredColumnReadsTheSameWay() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE fct (f FLOAT)");
        statement.execute("INSERT INTO fct SELECT SQRT(2)::FLOAT");
        final ResultSet rs = statement.executeQuery("SELECT f FROM fct");
        rs.next();
        assertEquals("1.414213562", rs.getString(1),
            "★ the column's declared type says how to spell it");
        rs.close();
        statement.execute("DROP TABLE fct");
    }
}
