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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream and a CHANGES clause type their columns as the table does: SYSTEM$TYPEOF used to read NULL[LOB] for
 * every one, the table's own and the METADATA$ ones, although a CTAS over the stream declared them right. An exact
 * number's storage tag follows the change rows the relation holds, as a table column's follows its rows.
 * METADATA$ACTION and METADATA$ROW_ID are VARCHARs with no width of their own: DESCRIBE RESULT reads them as
 * VARCHAR(134217728) and METADATA$ISUPDATE as BOOLEAN, after the table's columns (live-verified); SYSTEM$TYPEOF
 * names them bare and a CTAS stores them at the 16MB default, as for every widthless VARCHAR (not measured live).
 */
public class StreamColumnTypeofTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ts (a INT, s VARCHAR(5), n NUMBER(10,2))");
        engine.execute("ALTER TABLE ts SET CHANGE_TRACKING = TRUE");
    }

    /** The first row's cells, as text, joined by a bar. */
    private String firstRow(final String sql) {
        final List<Row> rows = engine.executeQuery(sql).getRows();
        final List<String> cells = new ArrayList<>();
        for (final Object value : rows.get(0).getValues()) {
            cells.add(String.valueOf(value));
        }
        return String.join(" | ", cells);
    }

    private static final String TYPES = "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(n),"
        + " SYSTEM$TYPEOF(METADATA$ACTION), SYSTEM$TYPEOF(METADATA$ISUPDATE), SYSTEM$TYPEOF(METADATA$ROW_ID) FROM ";

    private static final String EXPECTED = "NUMBER(38,0)[SB1] | VARCHAR(5)[LOB] | NUMBER(10,2)[SB2]"
        + " | VARCHAR[LOB] | BOOLEAN[SB1] | VARCHAR[LOB]";

    @Test
    public void aTableStreamTypesItsColumnsAsTheTableDoes() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        engine.execute("CREATE STREAM st_ao ON TABLE ts APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        assertEquals("NUMBER(38,0)[SB1] | VARCHAR(5)[LOB] | NUMBER(10,2)[SB2]",
            firstRow("SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(n) FROM ts"));
        assertEquals(EXPECTED, firstRow(TYPES + "st"));
        assertEquals(EXPECTED, firstRow(TYPES + "st_ao"));
        assertEquals("NUMBER(38,0)[SB1] | VARCHAR[LOB]",
            firstRow("SELECT SYSTEM$TYPEOF(x.a), SYSTEM$TYPEOF(x.METADATA$ACTION) FROM st x"));
    }

    @Test
    public void theStorageTagFollowsTheChangeRows() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        engine.execute("INSERT INTO ts VALUES (1000, 'x', 1.5), (NULL, 'y', NULL)");
        assertEquals("NUMBER(38,0)[SB2]", firstRow("SELECT SYSTEM$TYPEOF(a) FROM st"));
    }

    @Test
    public void aChangesClauseTypesItsColumnsAsTheTableDoes() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        final String start = String.valueOf(engine.executeQuery("SELECT CURRENT_TIMESTAMP()::VARCHAR")
            .getRows().get(0).getValue(0));
        engine.executeQuery("SELECT SYSTEM$WAIT(1)");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        final String end = String.valueOf(engine.executeQuery("SELECT CURRENT_TIMESTAMP()::VARCHAR")
            .getRows().get(0).getValue(0));
        assertEquals(EXPECTED, firstRow(TYPES + "ts CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => '"
            + start + "'::TIMESTAMP_LTZ)"));
        assertEquals(EXPECTED, firstRow(TYPES + "ts CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => '"
            + start + "'::TIMESTAMP_LTZ) END(TIMESTAMP => '" + end + "'::TIMESTAMP_LTZ)"));
        assertEquals(EXPECTED, firstRow(TYPES + "ts CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'st')"));
    }

    @Test
    public void aViewStreamTypesItsColumnsAsTheViewDoes() {
        engine.execute("CREATE TABLE tu (a INT, s VARCHAR(5), n NUMBER(10,2))");
        engine.execute("ALTER TABLE tu SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE VIEW vs AS SELECT a, s, a + 1 AS a1 FROM ts");
        engine.execute("CREATE STREAM st_v ON VIEW vs");
        engine.execute("CREATE VIEW vu AS SELECT a, s, n FROM ts UNION ALL SELECT a, s, n FROM tu");
        engine.execute("CREATE STREAM st_vu ON VIEW vu");
        engine.execute("CREATE VIEW vj AS SELECT ts.a, tu.s FROM ts JOIN tu ON ts.a = tu.a");
        engine.execute("CREATE STREAM st_vj ON VIEW vj");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        engine.execute("INSERT INTO tu VALUES (1, 'z', 2.5)");
        assertEquals("NUMBER(38,0)[SB1] | VARCHAR(5)[LOB] | NUMBER(38,0)[SB1] | BOOLEAN[SB1]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(a1), SYSTEM$TYPEOF(METADATA$ISUPDATE) FROM st_v"));
        assertEquals("NUMBER(38,0)[SB1] | NUMBER(10,2)[SB2] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(n), SYSTEM$TYPEOF(METADATA$ROW_ID) FROM st_vu"));
        assertEquals("NUMBER(38,0)[SB1] | VARCHAR(5)[LOB] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(METADATA$ACTION) FROM st_vj"));
    }

    @Test
    public void aDynamicTableStreamTypesItsColumns() {
        engine.execute("CREATE WAREHOUSE w");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 minute' WAREHOUSE = w AS SELECT a, s FROM ts");
        engine.execute("CREATE STREAM st_dt ON DYNAMIC TABLE dt");
        engine.execute("INSERT INTO ts VALUES (7, 'x', 1.5)");
        engine.execute("ALTER DYNAMIC TABLE dt REFRESH");
        assertEquals("NUMBER(38,0)[SB1] | VARCHAR(5)[LOB] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(METADATA$ACTION) FROM st_dt"));
    }

    @Test
    public void describeResultTypesTheMetadataColumnsAsTheAccountDoes() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        for (final String source : new String[] {"st", "ts CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'st')"}) {
            engine.executeQuery("SELECT * FROM " + source);
            final List<Row> described = engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()").getRows();
            final List<String> types = new ArrayList<>();
            for (final Row row : described) {
                types.add(row.getValue(0) + " " + row.getValue(1));
            }
            assertEquals("[A NUMBER(38,0), S VARCHAR(5), N NUMBER(10,2), METADATA$ACTION VARCHAR(134217728),"
                + " METADATA$ISUPDATE BOOLEAN, METADATA$ROW_ID VARCHAR(134217728)]", types.toString(), source);
        }
        engine.execute("CREATE TABLE cst AS SELECT * FROM st");
        assertEquals("VARCHAR(16777216)", describeCell("cst", "METADATA$ACTION", "type"));
        assertEquals("BOOLEAN", describeCell("cst", "METADATA$ISUPDATE", "type"));
        assertEquals("VARCHAR(16777216)", describeCell("cst", "METADATA$ROW_ID", "type"));
    }
}
