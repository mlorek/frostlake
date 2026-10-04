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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dynamic table created with a quoted name keeps it, as a table does: {@code "dt"} and {@code DT} are two dynamic
 * tables, each reached by its own spelling wherever a dynamic table is named. The name was upper-cased at CREATE and
 * matched ignoring case, so {@code "dt"} was stored as DT, a later CREATE DYNAMIC TABLE DT was refused as existing,
 * and SHOW and INFORMATION_SCHEMA listed DT.
 */
public class DynamicTableQuotedNameTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("INSERT INTO src VALUES (1), (2)");
        engine.execute("CREATE DYNAMIC TABLE \"dt\" TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT a FROM src");
        engine.execute("CREATE DYNAMIC TABLE DT TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH"
            + " AS SELECT a * 10 a FROM src");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private static String answer(final DatabaseEngine on, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : on.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "," : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** SHOW DYNAMIC TABLES' names, with one more column of the listing beside each. */
    private static String listed(final DatabaseEngine on, final String column) {
        on.executeQuery("SHOW DYNAMIC TABLES IN SCHEMA p.PUBLIC");
        return answer(on, "SELECT \"name\", \"" + column + "\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    @Test
    public void twoDynamicTablesDifferOnlyInCase() {
        assertEquals("DT,P | dt,P", listed(engine, "database_name"));
        assertEquals("DT | dt", answer(engine, "SELECT TABLE_NAME FROM p.INFORMATION_SCHEMA.TABLES"
            + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN ('dt', 'DT') ORDER BY 1"));
        engine.execute("ALTER DYNAMIC TABLE \"dt\" REFRESH");
        engine.execute("ALTER DYNAMIC TABLE DT REFRESH");
        assertEquals("3", answer(engine, "SELECT SUM(a) FROM \"dt\""));
        assertEquals("30", answer(engine, "SELECT SUM(a) FROM DT"));
        assertEquals("30", answer(engine, "SELECT SUM(a) FROM dt"));
    }

    @Test
    public void eachIsReachedByItsOwnSpelling() {
        assertEquals("A", answer(engine, "DESCRIBE DYNAMIC TABLE \"dt\"").split(",")[0]);
        assertTrue(answer(engine, "SELECT GET_DDL('TABLE', '\"dt\"')")
            .startsWith("create or replace dynamic table \"dt\""));
        engine.execute("ALTER DYNAMIC TABLE \"dt\" SUSPEND");
        assertEquals("DT,ACTIVE | dt,SUSPENDED", listed(engine, "scheduling_state"));
        engine.execute("COMMENT ON DYNAMIC TABLE \"dt\" IS 'lower'");
        assertEquals("DT,null | dt,lower", answer(engine, "SELECT TABLE_NAME, COMMENT FROM p.INFORMATION_SCHEMA.TABLES"
            + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN ('dt', 'DT') ORDER BY 1"));
    }

    @Test
    public void dropAndUndropMatchTheNameExactly() {
        engine.execute("DROP DYNAMIC TABLE \"dt\"");
        assertEquals("DT,P", listed(engine, "database_name"));
        assertTrue(refusal("DROP DYNAMIC TABLE \"dt\"").contains("does not exist"));
        engine.execute("UNDROP DYNAMIC TABLE \"dt\"");
        assertEquals("DT,P | dt,P", listed(engine, "database_name"));
        engine.execute("DROP DYNAMIC TABLE DT");
        assertEquals("dt,P", listed(engine, "database_name"));
        engine.execute("UNDROP DYNAMIC TABLE DT");
        assertEquals("DT,P | dt,P", listed(engine, "database_name"));
    }

    @Test
    public void aStreamOnTheQuotedOneNamesIt() {
        engine.execute("CREATE STREAM s ON DYNAMIC TABLE \"dt\"");
        engine.executeQuery("SHOW STREAMS LIKE 'S' IN SCHEMA p.PUBLIC");
        assertEquals("P.PUBLIC.dt", answer(engine, "SELECT \"table_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void aSnapshotKeepsTheQuotedName() throws IOException, ClassNotFoundException {
        final DatabaseEngine copy = engine.cloneInstance();
        try {
            assertEquals("DT,P | dt,P", listed(copy, "database_name"));
            copy.execute("ALTER DYNAMIC TABLE p.PUBLIC.\"dt\" REFRESH");
            assertEquals("3", answer(copy, "SELECT SUM(a) FROM p.PUBLIC.\"dt\""));
        } finally {
            copy.shutdown();
        }
    }
}
