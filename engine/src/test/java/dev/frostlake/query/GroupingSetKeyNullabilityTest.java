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

package dev.frostlake.query;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A key a ROLLUP, a CUBE or GROUPING SETS leaves out of some grouping set holds NULL on the super-group rows
 * (Snowflake's GROUP BY ROLLUP documentation), so its column is reported nullable whatever the table's NOT NULL says:
 * as a JavaScript procedure's {@code isColumnNullable} is told, through JDBC's {@code isNullable}, and in a view's
 * DESCRIBE and INFORMATION_SCHEMA. An item that reads such a key is nullable too, and a key every grouping set keeps
 * stays as its column is.
 */
public class GroupingSetKeyNullabilityTest {

    private static final String TABLE = "CREATE TABLE jsn (k INT, knn INT NOT NULL, snn VARCHAR NOT NULL)";

    private static final String ROW = "INSERT INTO jsn VALUES (1, 2, 'a,b')";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute(TABLE);
        engine.execute(ROW);
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** Each column as a JavaScript procedure is told it: 1 for nullable, 0 for not. */
    private String told(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : engine.executeQuery(sql).getColumns()) {
            final boolean nullable = column.getDerivedNullable() != null
                ? column.getDerivedNullable().booleanValue() : column.isNullabilityKnown() && column.isNullable();
            out.append(out.length() == 0 ? "" : " ").append(column.getName()).append('=').append(nullable ? 1 : 0);
        }
        return out.toString();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void aRolledUpKeyIsNullable() {
        assertEquals("C1=1 C2=0", told("SELECT knn AS c1, COUNT(*) AS c2 FROM jsn GROUP BY ROLLUP (knn)"));
        assertEquals("KNN=1 E=1 SNN=1", told("SELECT knn, knn + 1 AS e, snn FROM jsn GROUP BY CUBE (knn, snn)"));
        assertEquals("C1=0 C2=0", told("SELECT knn AS c1, COUNT(*) AS c2 FROM jsn GROUP BY knn"));
    }

    @Test
    public void aKeyEveryGroupingSetKeepsStaysNotNull() {
        assertEquals("KNN=0 SNN=1", told("SELECT knn, snn FROM jsn GROUP BY knn, ROLLUP (snn)"));
        assertEquals("KNN=0 SNN=1", told("SELECT knn, snn FROM jsn GROUP BY GROUPING SETS ((knn), (knn, snn))"));
        assertEquals("KNN=0", told("SELECT knn FROM jsn GROUP BY GROUPING SETS ((knn))"));
    }

    @Test
    public void aKeyNamedByPositionAliasOrExpression() {
        assertEquals("KNN=1", told("SELECT jsn.knn FROM jsn GROUP BY ROLLUP (1)"));
        assertEquals("A=1", told("SELECT knn AS a FROM jsn GROUP BY ROLLUP (a)"));
        assertEquals("E=1", told("SELECT knn + 1 AS e FROM jsn GROUP BY ROLLUP (knn + 1)"));
        assertEquals("KNN=1 SNN=1", told("SELECT knn, snn FROM jsn GROUP BY ROLLUP (jsn.knn, snn)"));
        assertEquals("K=1 KNN=1 SNN=1", told("SELECT * FROM jsn GROUP BY ROLLUP (k, knn, snn)"));
    }

    @Test
    public void aViewDescribesTheRolledKeyNullable() {
        engine.execute("CREATE VIEW rv AS SELECT knn, snn FROM jsn GROUP BY knn, ROLLUP (snn)");
        assertEquals("KNN|NO ;; SNN|YES", rows("SELECT column_name, is_nullable FROM information_schema.columns "
            + "WHERE table_name = 'RV' ORDER BY 1"));
        final StringBuilder described = new StringBuilder();
        for (final Row row : engine.executeQuery("DESCRIBE VIEW rv").getRows()) {
            described.append(described.length() == 0 ? "" : " ;; ").append(row.getValue(0)).append('|')
                .append(row.getValue(3));
        }
        assertEquals("KNN|N ;; SNN|Y", described.toString());
    }

    @Test
    public void jdbcReportsTheRolledKeyNullable() throws SQLException {
        final String url = "jdbc:frostlake:direct:rolled_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE d");
            statement.execute("USE SCHEMA d.PUBLIC");
            statement.execute(TABLE);
            statement.execute(ROW);
            try (ResultSet rs = statement.executeQuery("SELECT knn, COUNT(*) FROM jsn GROUP BY ROLLUP (knn)")) {
                final ResultSetMetaData meta = rs.getMetaData();
                assertEquals(ResultSetMetaData.columnNullable, meta.isNullable(1));
            }
            try (ResultSet rs = statement.executeQuery("SELECT knn, COUNT(*) FROM jsn GROUP BY knn")) {
                assertEquals(ResultSetMetaData.columnNoNulls, rs.getMetaData().isNullable(1));
            }
        }
    }
}
