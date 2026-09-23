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

package dev.frostlake.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A dynamic table takes CLUSTER BY among its options and TRANSIENT before its kind, and COMMENT ON DYNAMIC TABLE sets
 * its comment. SHOW DYNAMIC TABLES lists the clustering as LINEAR(…) with automatic clustering ON, re-prints both in
 * the table's text, dates a suspension in last_suspended_on until a RESUME and the table's data in data_timestamp
 * from its initial refresh, where Frostlake refused the syntax and left both stamps empty (live-verified).
 */
public class DynamicTableClusteringTest extends BaseDatabaseTest {

    private static final String OPTIONS =
        " lag = '1 hour' refresh_mode = 'AUTO' initialize = 'ON_SCHEDULE' warehouse = WH687 ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh687 WITH WAREHOUSE_SIZE = 'XSMALL' AUTO_SUSPEND = 60 "
            + "INITIALLY_SUSPENDED = TRUE");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE T (x INT, y INT)");
        engine.execute("INSERT INTO T VALUES (1, 2)");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP WAREHOUSE IF EXISTS wh687");
    }

    /** One dynamic table's SHOW DYNAMIC TABLES cells, as text, through a pipe. */
    private List<Object> cells(final String name, final String columns) {
        return engine.executeQuery("SHOW DYNAMIC TABLES LIKE '" + name + "' ->> SELECT " + columns + " FROM $1")
            .getRows().get(0).getValues();
    }

    private static String text(final Object value) {
        return String.valueOf(value).toUpperCase();
    }

    private void create(final String sql) {
        engine.execute(sql.replace("<WH>", "wh687"));
    }

    @Test
    public void clusterByIsListedAndReprinted() {
        create("CREATE OR REPLACE DYNAMIC TABLE DT4 (a, b) TARGET_LAG = '1 hour' WAREHOUSE = <WH> "
            + "INITIALIZE = ON_SCHEDULE CLUSTER BY (a) AS SELECT x, y FROM T");
        final List<Object> dt4 = cells("DT4", "\"cluster_by\", \"automatic_clustering\", \"text\"");
        assertEquals("LINEAR(a)", dt4.get(0));
        assertEquals("ON", dt4.get(1));
        assertEquals("CREATE OR REPLACE DYNAMIC TABLE DT4 (a, b) CLUSTER BY (a)" + OPTIONS + "AS SELECT x, y FROM T",
            dt4.get(2));
        create("CREATE OR REPLACE DYNAMIC TABLE DT7 CLUSTER BY (x) TARGET_LAG = '1 hour' WAREHOUSE = <WH> "
            + "INITIALIZE = ON_SCHEDULE AS SELECT x FROM T");
        assertEquals(List.of("LINEAR(x)", "CREATE OR REPLACE DYNAMIC TABLE DT7 CLUSTER BY (x)" + OPTIONS
            + "AS SELECT x FROM T"), cells("DT7", "\"cluster_by\", \"text\""));
        create("CREATE OR REPLACE DYNAMIC TABLE DT9 TARGET_LAG = '1 hour' WAREHOUSE = <WH> INITIALIZE = ON_SCHEDULE "
            + "CLUSTER BY (x, y + 1) AS SELECT x, y FROM T");
        assertEquals(List.of("LINEAR(x, y + 1)", "CREATE OR REPLACE DYNAMIC TABLE DT9 CLUSTER BY (x, y + 1)" + OPTIONS
            + "AS SELECT x, y FROM T"), cells("DT9", "\"cluster_by\", \"text\""));
        create("CREATE OR REPLACE DYNAMIC TABLE DT10 TARGET_LAG = '1 hour' WAREHOUSE = <WH> INITIALIZE = ON_SCHEDULE "
            + "CLUSTER BY (a) COMMENT = 'cc' AS SELECT x AS a FROM T");
        assertEquals(List.of("LINEAR(a)", "cc", "CREATE OR REPLACE DYNAMIC TABLE DT10 CLUSTER BY (a) COMMENT = 'cc'"
            + OPTIONS + "AS SELECT x AS a FROM T"), cells("DT10", "\"cluster_by\", \"comment\", \"text\""));
    }

    @Test
    public void aTransientDynamicTableSaysSo() {
        create("CREATE OR REPLACE TRANSIENT DYNAMIC TABLE DT5 TARGET_LAG = '1 hour' WAREHOUSE = <WH> "
            + "INITIALIZE = ON_SCHEDULE AS SELECT x FROM T");
        assertEquals(List.of("", "OFF", "CREATE OR REPLACE TRANSIENT DYNAMIC TABLE DT5" + OPTIONS + "AS SELECT x FROM T"),
            cells("DT5", "\"cluster_by\", \"automatic_clustering\", \"text\""));
        create("CREATE TRANSIENT DYNAMIC TABLE DT8 TARGET_LAG = '1 hour' WAREHOUSE = <WH> INITIALIZE = ON_SCHEDULE "
            + "AS SELECT x FROM T");
        assertEquals(List.of("CREATE TRANSIENT DYNAMIC TABLE DT8" + OPTIONS + "AS SELECT x FROM T"),
            cells("DT8", "\"text\""));
        assertEquals(List.of("BASE TABLE", "YES"), engine.executeQuery("SELECT table_type, is_transient FROM "
            + "information_schema.tables WHERE table_name = 'DT5'").getRows().get(0).getValues());
    }

    @Test
    public void commentOnDynamicTableSetsTheCommentAlone() {
        create("CREATE OR REPLACE DYNAMIC TABLE DT4 TARGET_LAG = '1 hour' WAREHOUSE = <WH> INITIALIZE = ON_SCHEDULE "
            + "AS SELECT x FROM T");
        engine.execute("COMMENT ON DYNAMIC TABLE DT4 IS 'c1'");
        assertEquals(List.of("c1", "CREATE OR REPLACE DYNAMIC TABLE DT4" + OPTIONS + "AS SELECT x FROM T"),
            cells("DT4", "\"comment\", \"text\""));
        engine.execute("COMMENT ON TABLE DT4 IS 'c2'");
        assertEquals(List.of("c2"), cells("DT4", "\"comment\""));
        engine.execute("COMMENT IF EXISTS ON DYNAMIC TABLE nosuch IS 'x'");
        assertEquals(hinted("SQL compilation error:\nDynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
            + "authorized."), assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("COMMENT ON DYNAMIC TABLE nosuch IS 'x'");
                }
            }).getMessage());
    }

    @Test
    public void aSuspensionAndARefreshAreDated() {
        create("CREATE OR REPLACE DYNAMIC TABLE DT4 TARGET_LAG = '1 hour' WAREHOUSE = <WH> INITIALIZE = ON_SCHEDULE "
            + "AS SELECT x FROM T");
        assertEquals(List.of("ACTIVE", "TRUE", "TRUE"), textOf(cells("DT4",
            "\"scheduling_state\", \"last_suspended_on\" IS NULL, \"data_timestamp\" IS NULL")));
        engine.execute("ALTER DYNAMIC TABLE DT4 SUSPEND");
        assertEquals(List.of("SUSPENDED", "FALSE", "TRUE"), textOf(cells("DT4",
            "\"scheduling_state\", \"last_suspended_on\" IS NULL, \"last_suspended_on\" <= CURRENT_TIMESTAMP()")));
        engine.execute("ALTER DYNAMIC TABLE DT4 RESUME");
        assertEquals(List.of("ACTIVE", "TRUE"), textOf(cells("DT4", "\"scheduling_state\", \"last_suspended_on\" IS NULL")));
        engine.execute("ALTER DYNAMIC TABLE DT4 SUSPEND");
        create("CREATE OR REPLACE DYNAMIC TABLE DT6 TARGET_LAG = '1 hour' WAREHOUSE = <WH> AS SELECT x FROM T");
        engine.execute("ALTER DYNAMIC TABLE DT6 SUSPEND");
        assertEquals(List.of("FALSE", "TRUE"), textOf(cells("DT6",
            "\"data_timestamp\" IS NULL, \"data_timestamp\" <= CURRENT_TIMESTAMP()")));
    }

    private static List<String> textOf(final List<Object> values) {
        final List<String> texts = new ArrayList<>();
        for (final Object value : values) {
            texts.add(text(value));
        }
        return texts;
    }
}
