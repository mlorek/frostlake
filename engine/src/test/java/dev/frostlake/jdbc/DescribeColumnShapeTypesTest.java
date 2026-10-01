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
 * The thirteen columns DESCRIBE answers for a table, a view, a materialized view and a dynamic table, as the
 * driver's metadata types them: every one VARCHAR but the privacy domain, which is OBJECT, and a RESULT_SCAN
 * over the answer keeps it OBJECT. A dynamic table's column types are spelled canonically, as a table's are.
 * Measured against the account's own driver.
 */
public class DescribeColumnShapeTypesTest extends BaseJdbcTest {

    private static final String SHAPE = "name:VARCHAR|type:VARCHAR|kind:VARCHAR|null?:VARCHAR|default:VARCHAR"
        + "|primary key:VARCHAR|unique key:VARCHAR|check:VARCHAR|expression:VARCHAR|comment:VARCHAR"
        + "|policy name:VARCHAR|privacy domain:OBJECT|write default:VARCHAR";

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE TABLE dcs_t (n NUMBER(5,2), s VARCHAR, s10 VARCHAR(10), ts TIMESTAMP_NTZ(3))");
    }

    /** Each column's name and type name, the columns joined by "|". */
    private String shapeOf(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        final ResultSetMetaData md = rs.getMetaData();
        final StringBuilder out = new StringBuilder();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            out.append(i == 1 ? "" : "|").append(md.getColumnName(i)).append(':').append(md.getColumnTypeName(i));
        }
        return out.toString();
    }

    /** The {@code type} cell of every row, joined by "|". */
    private String typesOf(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            out.append(out.length() == 0 ? "" : "|").append(rs.getString("type"));
        }
        return out.toString();
    }

    @Test
    public void aTableAndAViewDescribeThePrivacyDomainAsAnObject() throws SQLException {
        assertEquals(SHAPE, shapeOf("DESCRIBE TABLE dcs_t"));
        assertEquals(SHAPE, shapeOf("DESCRIBE TABLE dcs_t TYPE = COLUMNS"));
        statement.execute("CREATE VIEW dcs_v AS SELECT n FROM dcs_t");
        assertEquals(SHAPE, shapeOf("DESCRIBE VIEW dcs_v"));
        statement.execute("CREATE MATERIALIZED VIEW dcs_mv AS SELECT n FROM dcs_t");
        assertEquals(SHAPE, shapeOf("DESCRIBE MATERIALIZED VIEW dcs_mv"));
        statement.executeQuery("DESCRIBE TABLE dcs_t");
        assertEquals("privacy domain:OBJECT",
            shapeOf("SELECT \"privacy domain\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void aDynamicTableDescribesTheSameThirteenColumns() throws SQLException {
        statement.execute("CREATE WAREHOUSE IF NOT EXISTS dcs_wh WITH WAREHOUSE_SIZE = 'XSMALL' AUTO_SUSPEND = 60"
            + " INITIALLY_SUSPENDED = TRUE");
        try {
            statement.execute("CREATE DYNAMIC TABLE dcs_dt TARGET_LAG = '1 day' WAREHOUSE = dcs_wh REFRESH_MODE = FULL"
                + " INITIALIZE = ON_SCHEDULE AS SELECT n, s, s10, ts, n + 1 AS n1, UPPER(s10) AS u FROM dcs_t");
            assertEquals(SHAPE, shapeOf("DESCRIBE DYNAMIC TABLE dcs_dt"));
            assertEquals("NUMBER(5,2)|VARCHAR(16777216)|VARCHAR(10)|TIMESTAMP_NTZ(3)|NUMBER(6,2)|VARCHAR(30)",
                typesOf("DESCRIBE DYNAMIC TABLE dcs_dt"));
        } finally {
            statement.execute("DROP DYNAMIC TABLE IF EXISTS dcs_dt");
            statement.execute("DROP WAREHOUSE IF EXISTS dcs_wh");
        }
    }
}
