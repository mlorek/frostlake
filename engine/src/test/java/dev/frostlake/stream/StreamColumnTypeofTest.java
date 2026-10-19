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
 * A stream and a CHANGES clause type their columns as the table declares them: SYSTEM$TYPEOF used to read NULL[LOB]
 * for every one, the table's own and the METADATA$ ones, although a CTAS over the stream declared them right.
 *
 * <p>A change set carries no statistics, so an exact number read through it is tagged by its declared precision
 * alone: NUMBER(38,0) holding 1 reads [SB16] through a stream where the table itself reads [SB1], NUMBER(4,0) [SB2],
 * NUMBER(9,0) [SB4] and NUMBER(10,2) [SB8], and so does an expression over such a column. A standard stream on a
 * table sizes its action and row id, VARCHAR(6) and VARCHAR(40); an append-only stream, a stream on a view and a
 * CHANGES clause read both with no width, which a result's description spells VARCHAR(134217728). All live-verified.
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

    /** What a standard stream on a table reads: the sized action and row id. */
    private static final String SIZED = "NUMBER(38,0)[SB16] | VARCHAR(5)[LOB] | NUMBER(10,2)[SB8]"
        + " | VARCHAR(6)[LOB] | BOOLEAN[SB1] | VARCHAR(40)[LOB]";

    /** What every other change set reads: the action and row id with no width. */
    private static final String WIDTHLESS = "NUMBER(38,0)[SB16] | VARCHAR(5)[LOB] | NUMBER(10,2)[SB8]"
        + " | VARCHAR[LOB] | BOOLEAN[SB1] | VARCHAR[LOB]";

    @Test
    public void aTableStreamTypesItsColumnsAsTheTableDeclaresThem() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        engine.execute("CREATE STREAM st_ao ON TABLE ts APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        assertEquals("NUMBER(38,0)[SB1] | VARCHAR(5)[LOB] | NUMBER(10,2)[SB2]",
            firstRow("SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(n) FROM ts"));
        assertEquals(SIZED, firstRow(TYPES + "st"));
        assertEquals(WIDTHLESS, firstRow(TYPES + "st_ao"));
        assertEquals("NUMBER(38,0)[SB16] | VARCHAR(6)[LOB]",
            firstRow("SELECT SYSTEM$TYPEOF(x.a), SYSTEM$TYPEOF(x.METADATA$ACTION) FROM st x"));
    }

    @Test
    public void anExactNumberIsTaggedByItsDeclaredPrecision() {
        engine.execute("CREATE TABLE tn (n2 NUMBER(2,0), n4 NUMBER(4,0), n9 NUMBER(9,0), n18 NUMBER(18,0),"
            + " n19 NUMBER(19,0), f FLOAT, b BOOLEAN, d DATE)");
        engine.execute("CREATE STREAM stn ON TABLE tn");
        engine.execute("INSERT INTO tn VALUES (1, 1, 1, 1, 1, 1.5, TRUE, '2024-01-02'), (NULL, NULL, NULL, NULL, NULL,"
            + " NULL, NULL, NULL)");
        assertEquals("NUMBER(2,0)[SB1] | NUMBER(4,0)[SB2] | NUMBER(9,0)[SB4] | NUMBER(18,0)[SB8] | NUMBER(19,0)[SB16]"
            + " | FLOAT[DOUBLE] | BOOLEAN[SB1] | DATE[SB4]",
            firstRow("SELECT SYSTEM$TYPEOF(n2), SYSTEM$TYPEOF(n4), SYSTEM$TYPEOF(n9), SYSTEM$TYPEOF(n18),"
                + " SYSTEM$TYPEOF(n19), SYSTEM$TYPEOF(f), SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(d) FROM stn"));
        assertEquals("NUMBER(3,0)[SB2] | NUMBER(10,0)[SB8] | NUMBER(9,0)[SB4] | NUMBER(5,0)[SB4]",
            firstRow("SELECT SYSTEM$TYPEOF(n2 * 2), SYSTEM$TYPEOF(n9 + n4), SYSTEM$TYPEOF(-n9),"
                + " SYSTEM$TYPEOF(n19::NUMBER(5,0)) FROM stn"));
        assertEquals("NUMBER(19,0)[SB16] | NUMBER(14,0)[SB8] | NUMBER(18,0)[SB8] | VARCHAR(6)[LOB] | VARCHAR(40)[LOB]",
            firstRow("SELECT SYSTEM$TYPEOF(MAX(n19)), SYSTEM$TYPEOF(SUM(n2)), SYSTEM$TYPEOF(COUNT(*)),"
                + " SYSTEM$TYPEOF(MAX(METADATA$ACTION)), SYSTEM$TYPEOF(MIN(METADATA$ROW_ID)) FROM stn"));
        // Beside the table itself in a join, the stream's side keeps its declared width and the table's its rows'.
        assertEquals("NUMBER(9,0)[SB4] | NUMBER(9,0)[SB1]",
            firstRow("SELECT SYSTEM$TYPEOF(x.n9), SYSTEM$TYPEOF(t.n9) FROM stn x JOIN tn t ON x.n9 = t.n9"));
        assertEquals("NUMBER(9,0)[SB4] | VARCHAR(6)[LOB]",
            firstRow("SELECT SYSTEM$TYPEOF(n9), SYSTEM$TYPEOF(act) FROM (SELECT n9, METADATA$ACTION AS act FROM stn)"));
    }

    @Test
    public void aChangesClauseTypesItsColumnsAsTheTableDeclaresThem() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        final String start = String.valueOf(engine.executeQuery("SELECT CURRENT_TIMESTAMP()::VARCHAR")
            .getRows().get(0).getValue(0));
        engine.executeQuery("SELECT SYSTEM$WAIT(1)");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        final String end = String.valueOf(engine.executeQuery("SELECT CURRENT_TIMESTAMP()::VARCHAR")
            .getRows().get(0).getValue(0));
        assertEquals(WIDTHLESS, firstRow(TYPES + "ts CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => '"
            + start + "'::TIMESTAMP_LTZ)"));
        assertEquals(WIDTHLESS, firstRow(TYPES + "ts CHANGES(INFORMATION => APPEND_ONLY) AT(TIMESTAMP => '"
            + start + "'::TIMESTAMP_LTZ) END(TIMESTAMP => '" + end + "'::TIMESTAMP_LTZ)"));
        assertEquals(WIDTHLESS, firstRow(TYPES + "ts CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'st')"));
    }

    @Test
    public void aViewStreamTypesItsColumnsAsTheViewDoes() {
        engine.execute("CREATE TABLE tu (a INT, s VARCHAR(5), n NUMBER(10,2))");
        engine.execute("ALTER TABLE tu SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE VIEW vs AS SELECT a, s, a + 1 AS a1, UPPER(s) AS us, 'lit' AS l FROM ts");
        engine.execute("CREATE STREAM st_v ON VIEW vs");
        engine.execute("CREATE VIEW vu AS SELECT a, s, n FROM ts UNION ALL SELECT a, s, n FROM tu");
        engine.execute("CREATE STREAM st_vu ON VIEW vu");
        engine.execute("CREATE VIEW vj AS SELECT ts.a, tu.s FROM ts JOIN tu ON ts.a = tu.a");
        engine.execute("CREATE STREAM st_vj ON VIEW vj");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        engine.execute("INSERT INTO tu VALUES (1, 'z', 2.5)");
        assertEquals("NUMBER(38,0)[SB16] | VARCHAR(5)[LOB] | NUMBER(38,0)[SB16] | VARCHAR(15)[LOB] | VARCHAR(3)[LOB]"
            + " | VARCHAR[LOB] | BOOLEAN[SB1] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(a1), SYSTEM$TYPEOF(us), SYSTEM$TYPEOF(l),"
                + " SYSTEM$TYPEOF(METADATA$ACTION), SYSTEM$TYPEOF(METADATA$ISUPDATE), SYSTEM$TYPEOF(METADATA$ROW_ID)"
                + " FROM st_v"));
        assertEquals("NUMBER(38,0)[SB16] | NUMBER(10,2)[SB8] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(n), SYSTEM$TYPEOF(METADATA$ROW_ID) FROM st_vu"));
        assertEquals("NUMBER(38,0)[SB16] | VARCHAR(5)[LOB] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(METADATA$ACTION) FROM st_vj"));
    }

    @Test
    public void aDynamicTableStreamTypesItsColumns() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS w");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 minute' WAREHOUSE = w AS SELECT a, s FROM ts");
        engine.execute("CREATE STREAM st_dt ON DYNAMIC TABLE dt");
        engine.execute("INSERT INTO ts VALUES (7, 'x', 1.5)");
        engine.execute("ALTER DYNAMIC TABLE dt REFRESH");
        assertEquals("NUMBER(38,0)[SB16] | VARCHAR(5)[LOB] | VARCHAR[LOB]", firstRow(
            "SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(s), SYSTEM$TYPEOF(METADATA$ACTION) FROM st_dt"));
    }

    /** The name and type of each column DESCRIBE RESULT lists for the last statement. */
    private String described(final String sql) {
        engine.executeQuery(sql);
        final List<String> types = new ArrayList<>();
        for (final Row row : engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()").getRows()) {
            types.add(row.getValue(0) + " " + row.getValue(1));
        }
        return types.toString();
    }

    @Test
    public void aResultDescribesTheMetadataColumnsAsTheAccountDoes() {
        engine.execute("CREATE STREAM st ON TABLE ts");
        engine.execute("CREATE STREAM st_ao ON TABLE ts APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO ts VALUES (1, 'x', 1.5)");
        assertEquals("[A NUMBER(38,0), S VARCHAR(5), N NUMBER(10,2), METADATA$ACTION VARCHAR(6),"
            + " METADATA$ISUPDATE BOOLEAN, METADATA$ROW_ID VARCHAR(40)]", described("SELECT * FROM st"));
        for (final String source : new String[] {"st_ao", "ts CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'st')"}) {
            assertEquals("[A NUMBER(38,0), S VARCHAR(5), N NUMBER(10,2), METADATA$ACTION VARCHAR(134217728),"
                + " METADATA$ISUPDATE BOOLEAN, METADATA$ROW_ID VARCHAR(134217728)]",
                described("SELECT * FROM " + source), source);
        }
        engine.execute("CREATE TABLE cst AS SELECT * FROM st");
        assertEquals("VARCHAR(6)", describeCell("cst", "METADATA$ACTION", "type"));
        assertEquals("BOOLEAN", describeCell("cst", "METADATA$ISUPDATE", "type"));
        assertEquals("VARCHAR(40)", describeCell("cst", "METADATA$ROW_ID", "type"));
        engine.execute("CREATE TABLE cao AS SELECT * FROM st_ao");
        assertEquals("VARCHAR(16777216)", describeCell("cao", "METADATA$ACTION", "type"));
        assertEquals("VARCHAR(16777216)", describeCell("cao", "METADATA$ROW_ID", "type"));
    }
}
