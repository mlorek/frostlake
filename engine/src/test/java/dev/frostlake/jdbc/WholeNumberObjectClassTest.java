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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * getObject returns the class getColumnClassName names (documented, JDBC: "the fully-qualified name of the class ...
 * if the method ResultSet.getObject is called"): for a scale-0 NUMBER that fits, java.lang.Long, whatever Java type the
 * engine computed the cell as. LENGTH() and INFORMATION_SCHEMA's counts came back as Integers, so a getColumns
 * listing mixed Integers and Longs in one column.
 */
public class WholeNumberObjectClassTest extends BaseJdbcTest {

    @Test
    public void aComputedWholeNumberIsALong() throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT LENGTH('abc') AS n, 7 AS m")) {
            rs.next();
            assertEquals("java.lang.Long", rs.getMetaData().getColumnClassName(1));
            assertEquals(Long.class, rs.getObject(1).getClass());
            assertEquals(Long.valueOf(3L), rs.getObject(1));
            assertEquals(Long.class, rs.getObject(2).getClass());
        }
    }

    @Test
    public void aColumnListingAnswersTheClassItsMetadataNames() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE wnc (a VARCHAR(10), b NUMBER(10,2), c INT)");
        final DatabaseMetaData metaData = connection.getMetaData();
        int rows = 0;
        try (ResultSet columns = metaData.getColumns(null, null, "WNC", null)) {
            final int size = columns.findColumn("COLUMN_SIZE");
            final int position = columns.findColumn("ORDINAL_POSITION");
            while (columns.next()) {
                rows++;
                for (final int column : new int[] {size, position}) {
                    assertEquals(columns.getMetaData().getColumnClassName(column),
                        columns.getObject(column).getClass().getName(), columns.getString("COLUMN_NAME"));
                }
            }
        }
        assertEquals(3, rows);
        try (ResultSet rs = statement.executeQuery("""
                SELECT ordinal_position FROM information_schema.columns
                WHERE table_name = 'WNC' ORDER BY ordinal_position""")) {
            while (rs.next()) {
                assertEquals(Long.class, rs.getObject(1).getClass());
            }
        }
    }
}
