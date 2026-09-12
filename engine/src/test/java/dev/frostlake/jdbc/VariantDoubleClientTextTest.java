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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The text a client reads for a semi-structured cell spells a DOUBLE the account's way, the
 * fifteen-decimal form: a whole value as much as a member, an element or path read out of a VARIANT as
 * much as the VARIANT itself, through getString and getObject alike. A DECIMAL keeps its digits, a
 * non-finite DOUBLE its bare word, and a value cast out of the VARIANT reads as its FLOAT. Every cell is
 * live-verified.
 */
public class VariantDoubleClientTextTest extends BaseJdbcTest {

    private String text(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private List<String> column(final String sql) throws SQLException {
        final List<String> texts = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                texts.add(rs.getString(1));
            }
        }
        return texts;
    }

    private Object object(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1);
        }
    }

    @Test
    public void aWholeValueDoubleReadsInTheFifteenDecimalForm() throws SQLException {
        assertEquals("1.500000000000000e+00", text("SELECT TO_VARIANT(1.5::FLOAT)"));
        assertEquals("3.000000000000000e+00", text("SELECT TO_VARIANT(3::FLOAT)"));
        assertEquals("1.000000000000000e+02", text("SELECT PARSE_JSON('1e2')"));
        assertEquals("-0.000000000000000e+00", text("SELECT PARSE_JSON('-0e0')"));
        assertEquals("1.414213562373095e+00", text("SELECT TO_VARIANT(SQRT(2))"));
        assertEquals("1.000000000000000e+300", text("SELECT TO_VARIANT(1e300::FLOAT)"));
        assertEquals("1.000000000000000e-07", text("SELECT TO_VARIANT(1e-7::FLOAT)"));
        assertEquals("1.234567891250000e+08", text("SELECT TO_VARIANT(123456789.125::FLOAT)"));
        assertEquals("4.940656458412465e-324", text("SELECT TO_VARIANT(5e-324::FLOAT)"));
        assertEquals("1.797693134862316e+308", text("SELECT TO_VARIANT(1.7976931348623157e308::FLOAT)"));
        assertEquals("1.000000000000000e-01", text("SELECT TO_VARIANT(0.1::FLOAT)"));
        assertEquals("3.000000000000000e-01", text("SELECT TO_VARIANT(0.1::FLOAT + 0.2::FLOAT)"));
    }

    @Test
    public void anElementOrPathReadOutOfAVariantReadsTheSame() throws SQLException {
        assertEquals("2.500000000000000e+00", text("SELECT GET(PARSE_JSON('{\"a\":2.5e0}'), 'a')"));
        assertEquals("1.500000000000000e+00", text("SELECT PARSE_JSON('{\"a\":1.5e0}'):a"));
        assertEquals("1.500000000000000e+00", text("SELECT PARSE_JSON('[1.5e0]')[0]"));
        assertEquals("1.500000000000000e+00", text("SELECT ARRAY_CONSTRUCT(1.5::FLOAT)[0]"));
        // FLATTEN's VALUE: a DOUBLE, an INTEGER and a DECIMAL side by side.
        assertEquals(List.of("1.500000000000000e+00", "2", "2.5"),
            column("SELECT value FROM TABLE(FLATTEN(PARSE_JSON('[1.5e0, 2, 2.5]'))) ORDER BY index"));
        // Cast out of the VARIANT, the value is a FLOAT and reads as one.
        assertEquals("100", text("SELECT PARSE_JSON('{\"a\":1e2}'):a::FLOAT"));
    }

    @Test
    public void aContainerAndTheConversionsAgree() throws SQLException {
        assertEquals("[1.000000000000000e+00,2.500000000000000e+00]",
            text("SELECT ARRAY_CONSTRUCT(1.0::FLOAT, 2.5::FLOAT)"));
        assertEquals("{\"k\":1.000000000000000e+00}", text("SELECT OBJECT_CONSTRUCT('k', 1.0::FLOAT)"));
        assertEquals("1.500000000000000e+00", text("SELECT TO_JSON(TO_VARIANT(1.5::FLOAT))"));
        assertEquals("1.5", text("SELECT TO_VARIANT(1.5::FLOAT)::VARCHAR"));
    }

    @Test
    public void aDecimalKeepsItsDigitsAndANonFiniteDoubleItsWord() throws SQLException {
        assertEquals("1.5", text("SELECT PARSE_JSON('1.5')"));
        assertEquals("1.5", text("SELECT TO_VARIANT(1.5::NUMBER(10,2))"));
        assertEquals("[1.5,2,\"x\",{\"d\":0.1}]", text("SELECT PARSE_JSON('[1.5, 2, \"x\", {\"d\": 0.1}]')"));
        assertEquals("NaN", text("SELECT TO_VARIANT('NaN'::FLOAT)"));
        assertEquals("Infinity", text("SELECT TO_VARIANT('inf'::FLOAT)"));
    }

    @Test
    public void getObjectHandsBackTheSameText() throws SQLException {
        assertEquals("1.500000000000000e+00", object("SELECT TO_VARIANT(1.5::FLOAT)"));
        assertEquals("[1.000000000000000e+00,2.500000000000000e+00]",
            object("SELECT ARRAY_CONSTRUCT(1.0::FLOAT, 2.5::FLOAT)"));
        assertEquals("1.5", object("SELECT PARSE_JSON('1.5')"));
    }
}
