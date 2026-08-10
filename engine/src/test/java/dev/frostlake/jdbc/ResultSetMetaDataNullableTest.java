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
 * ResultSetMetaData.isNullable does NOT follow the rule the catalog surfaces follow, which is the
 * whole point of this test: the driver answers {@code columnNullable} only for a column it KNOWS
 * accepts NULL, and {@code columnNoNulls} for everything else — a NOT NULL column, an expression over
 * one, and a bare literal alike. So {@code SELECT UPPER(v)} reads columnNoNulls here while
 * INFORMATION_SCHEMA.COLUMNS reports that same column nullable.
 *
 * <p>Measured against the account's own driver, which is what a caller compares Frostlake to.
 */
public class ResultSetMetaDataNullableTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE TABLE jn_t (k NUMBER NOT NULL, v VARCHAR(4) NOT NULL, w VARCHAR(4))");
        statement.execute("CREATE VIEW jn_v AS SELECT k, w FROM jn_t");
    }

    /** The isNullable codes of one query's columns, in order, as readable names. */
    private String nullabilityOf(final String query) throws SQLException {
        final ResultSet rs = statement.executeQuery(query);
        final ResultSetMetaData md = rs.getMetaData();
        final StringBuilder out = new StringBuilder();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            out.append(i == 1 ? "" : "|").append(name(md.isNullable(i)));
        }
        return out.toString();
    }

    private String name(final int code) {
        if (code == ResultSetMetaData.columnNoNulls) {
            return "NoNulls";
        }
        return code == ResultSetMetaData.columnNullable ? "Nullable" : "Unknown";
    }

    /** A base column reports its own declaration. */
    @Test
    public void aBaseColumnReportsItsDeclaration() throws SQLException {
        assertEquals("NoNulls|NoNulls|Nullable", nullabilityOf("SELECT k, v, w FROM jn_t"));
    }

    /** An expression over a column reads columnNoNulls, NOT nullable — the driver's own rule. */
    @Test
    public void anExpressionColumnReadsNoNulls() throws SQLException {
        assertEquals("NoNulls|NoNulls", nullabilityOf("SELECT UPPER(v) AS uv, k + 1 AS kp FROM jn_t"));
    }

    /** A view's columns carry the same answers as the base table's. */
    @Test
    public void aViewCarriesTheSameAnswers() throws SQLException {
        assertEquals("NoNulls|Nullable", nullabilityOf("SELECT k, w FROM jn_v"));
    }

    /** And a bare literal reads columnNoNulls too. */
    @Test
    public void aLiteralReadsNoNulls() throws SQLException {
        assertEquals("NoNulls|NoNulls", nullabilityOf("SELECT 1 AS one, 'a' AS ltr FROM jn_t"));
    }
}
