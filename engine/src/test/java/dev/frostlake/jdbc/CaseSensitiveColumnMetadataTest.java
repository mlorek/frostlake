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
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ResultSetMetaData.isCaseSensitive} answers by the column's TYPE: true for the text and
 * semi-structured families — VARCHAR, which is what CHAR and TEXT report as, plus VARIANT, OBJECT and
 * ARRAY — and false for every other, the numerics, BOOLEAN, BINARY, the temporals and both intervals
 * included. Both transports answered a constant before, and each was wrong for half the types.
 */
public class CaseSensitiveColumnMetadataTest extends BaseJdbcTest {

    /** What the driver answers for the one column {@code expression} projects. */
    private boolean caseSensitive(final String expression) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT " + expression + " AS c")) {
            return rs.getMetaData().isCaseSensitive(1);
        }
    }

    /** The text families are case-sensitive. */
    @Test
    public void theTextFamiliesAreCaseSensitive() throws SQLException {
        assertEquals(true, caseSensitive("CAST('a' AS VARCHAR(5))"));
        assertEquals(true, caseSensitive("CAST('a' AS CHAR(1))"));
        assertEquals(true, caseSensitive("CAST('a' AS TEXT)"));
    }

    /** So are the semi-structured ones. */
    @Test
    public void theSemiStructuredFamiliesAreCaseSensitive() throws SQLException {
        assertEquals(true, caseSensitive("TO_VARIANT(1)"));
        assertEquals(true, caseSensitive("OBJECT_CONSTRUCT('k', 1)"));
        assertEquals(true, caseSensitive("ARRAY_CONSTRUCT(1)"));
    }

    /** Every other family is not. */
    @Test
    public void theOtherFamiliesAreNot() throws SQLException {
        assertEquals(false, caseSensitive("TO_BINARY('AB', 'HEX')"));
        assertEquals(false, caseSensitive("CAST(1 AS NUMBER(10,2))"));
        assertEquals(false, caseSensitive("CAST(1 AS INT)"));
        assertEquals(false, caseSensitive("CAST(1 AS FLOAT)"));
        assertEquals(false, caseSensitive("TRUE"));
        assertEquals(false, caseSensitive("CURRENT_DATE()"));
        assertEquals(false, caseSensitive("CURRENT_TIME()"));
        assertEquals(false, caseSensitive("CAST('2020-01-01' AS TIMESTAMP_NTZ)"));
        assertEquals(false, caseSensitive("CAST('2020-01-01' AS TIMESTAMP_LTZ)"));
        assertEquals(false, caseSensitive("CAST('2020-01-01' AS TIMESTAMP_TZ)"));
        assertEquals(false, caseSensitive("INTERVAL '1' DAY"));
        assertEquals(false, caseSensitive("INTERVAL '1' YEAR"));
    }
}
