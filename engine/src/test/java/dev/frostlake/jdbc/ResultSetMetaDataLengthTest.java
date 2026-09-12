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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ResultSetMetaData answers a text or binary column's LENGTH as both its precision and its display size:
 * VARCHAR(9) reads 9 and BINARY(5) 5, a table's bare VARCHAR 16777216 and bare BINARY 8388608, a
 * concatenation of two 16MB columns 33554432, a cast to bare VARCHAR 134217728, and a binary the plan never
 * sized — TO_BINARY, a cast to bare BINARY — the 64MB maximum, 67108864.
 *
 * <p>Measured against the account's own driver, which is what a caller compares Frostlake to.
 */
public class ResultSetMetaDataLengthTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE TABLE len_t (u VARCHAR, v9 VARCHAR(9), c CHAR, s STRING, b BINARY, b5 BINARY(5))");
    }

    /** Each column's precision and display size as "precision/display", the columns joined by "|". */
    private String lengthsOf(final String query) throws SQLException {
        final ResultSet rs = statement.executeQuery(query);
        final ResultSetMetaData md = rs.getMetaData();
        final StringBuilder out = new StringBuilder();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            out.append(i == 1 ? "" : "|").append(md.getPrecision(i)).append('/').append(md.getColumnDisplaySize(i));
        }
        return out.toString();
    }

    /** A table column reads the length it was declared with, a bare one its family's full width. */
    @Test
    public void aTableColumnReadsItsDeclaredLength() throws SQLException {
        assertEquals("16777216/16777216|9/9|1/1|16777216/16777216|8388608/8388608|5/5",
            lengthsOf("SELECT u, v9, c, s, b, b5 FROM len_t"));
    }

    /** An expression reads the length its plan declares for it, which may exceed any column's. */
    @Test
    public void anExpressionReadsTheLengthItsPlanDeclares() throws SQLException {
        assertEquals("33554432/33554432|16777216/16777216|67108864/67108864|67108864/67108864",
            lengthsOf("SELECT u || u AS uu, SUBSTR(u, 1, 2) AS sub, TO_BINARY(u) AS tb,"
                + " CAST(b AS BINARY) AS cb FROM len_t"));
    }

    /** A cast literal reads the length of the type it was cast to. */
    @Test
    public void aCastLiteralReadsItsTypesLength() throws SQLException {
        assertEquals("9/9|134217728/134217728|3/3|5/5|67108864/67108864",
            lengthsOf("SELECT 'x'::VARCHAR(9) AS v9, CAST(1 AS VARCHAR) AS cv, 'x'::CHAR(3) AS c3,"
                + " TO_BINARY('AB', 'HEX')::BINARY(5) AS b5, TO_BINARY('AB', 'HEX') AS bu"));
    }
}
