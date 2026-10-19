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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A dynamic table initialized ON_SCHEDULE holds nothing until its first refresh: every query that reads it is
 * refused — through a view, a stream, a CTAS or an INSERT … SELECT too — while CREATE VIEW, CREATE STREAM and
 * DESCRIBE over it are not. A refresh that reads such a table refreshes it first, a clone of it and the table
 * renamed are as uninitialized as it was, and a DOWNSTREAM table waits for a table that reads it (live-verified).
 */
public class DynamicTableInitializationTest extends MeasuredCellsTestSupport {

    private String warehouse;

    @BeforeEach
    public void aSourceTable() {
        final Object current = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        warehouse = current == null ? "COMPUTE_WH" : current.toString();
        answer("CREATE OR REPLACE TABLE src1455 (a INT)");
        answer("INSERT INTO src1455 VALUES (1), (2)");
    }

    private String create(final String name, final String rest) {
        return create(name, "'1 hour'", rest);
    }

    private String create(final String name, final String lag, final String rest) {
        return answer("CREATE OR REPLACE DYNAMIC TABLE " + name + " TARGET_LAG = " + lag + " WAREHOUSE = " + warehouse
            + " " + rest);
    }

    private static String notInitialized(final String name) {
        return "REFUSED Dynamic Table 'TEST_DB.TEST_SCHEMA." + name + "' is not initialized. Please run a manual"
            + " refresh or wait for a scheduled refresh before querying.";
    }

    /** What a refresh tells it changed: the statistics cell of its answer. */
    private String refreshed(final String name) {
        final String[] cells = answer("ALTER DYNAMIC TABLE " + name + " REFRESH").split("\\|");
        return cells.length > 1 ? cells[1] : cells[0];
    }

    @Test
    public void everyReadOfATableThatNeverRefreshedIsRefused() {
        assertEquals("Dynamic table DTI1455 successfully created.",
            create("dti1455", "INITIALIZE = ON_SCHEDULE AS SELECT a FROM src1455"));
        final String refused = notInitialized("DTI1455");
        assertCells(new String[][] {
            {"SELECT * FROM dti1455", refused},
            {"SELECT COUNT(*) FROM dti1455", refused},
            {"SELECT 1 FROM dti1455 WHERE FALSE", refused},
            {"SELECT * FROM dti1455 LIMIT 0", refused},
            {"SELECT (SELECT COUNT(*) FROM dti1455)", refused},
            {"SELECT s.a FROM src1455 s JOIN dti1455 d ON s.a = d.a", refused},
            {"WITH x AS (SELECT * FROM dti1455) SELECT 1", refused},
            {"CREATE OR REPLACE TABLE c1455 AS SELECT * FROM dti1455", refused},
            {"INSERT INTO src1455 SELECT * FROM dti1455", refused},
            {"CREATE OR REPLACE VIEW v1455 AS SELECT * FROM dti1455", "View V1455 successfully created."},
            {"SELECT * FROM v1455", refused},
            {"CREATE OR REPLACE STREAM st1455 ON DYNAMIC TABLE dti1455", "Stream ST1455 successfully created."},
            {"SELECT * FROM st1455", refused},
            {"SELECT SYSTEM$STREAM_HAS_DATA('st1455')", "false"},
        });
        answer("DESCRIBE TABLE dti1455");
        assertAnswer("A|NUMBER(38,0)", "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW DYNAMIC TABLES LIKE 'DTI1455'");
        assertAnswer("DTI1455|0|true", "SELECT \"name\", \"rows\", \"data_timestamp\" IS NULL"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals("{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}", refreshed("dti1455"));
        assertCells(new String[][] {
            {"SELECT * FROM dti1455 ORDER BY 1", "1 ;; 2"},
            {"SELECT a FROM v1455 ORDER BY 1", "1 ;; 2"},
        });
    }

    @Test
    public void aRefreshThatReadsItRefreshesItFirst() {
        create("dti1455", "INITIALIZE = ON_SCHEDULE AS SELECT a FROM src1455");
        assertEquals("Dynamic table DTJ1455 successfully created.", create("dtj1455", "AS SELECT a FROM dti1455"));
        assertCells(new String[][] {
            {"SELECT * FROM dti1455 ORDER BY 1", "1 ;; 2"},
            {"SELECT * FROM dtj1455 ORDER BY 1", "1 ;; 2"},
        });
        assertEquals("No new data", refreshed("dti1455"));

        create("dtk1455", "INITIALIZE = ON_SCHEDULE AS SELECT a FROM src1455");
        create("dtl1455", "INITIALIZE = ON_SCHEDULE AS SELECT a FROM dtk1455");
        assertCells(new String[][] {
            {"SELECT * FROM dtk1455", notInitialized("DTK1455")},
            {"SELECT * FROM dtl1455", notInitialized("DTL1455")},
        });
        assertEquals("{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}", refreshed("dtl1455"));
        assertCells(new String[][] {
            {"SELECT * FROM dtl1455 ORDER BY 1", "1 ;; 2"},
            {"SELECT * FROM dtk1455 ORDER BY 1", "1 ;; 2"},
        });
    }

    @Test
    public void aCloneOrARenameStaysUninitialized() {
        create("dtm1455", "INITIALIZE = ON_SCHEDULE AS SELECT a FROM src1455");
        assertCells(new String[][] {
            {"CREATE OR REPLACE DYNAMIC TABLE dtm1455c CLONE dtm1455", "Table DTM1455C successfully created."},
            {"SELECT * FROM dtm1455c", notInitialized("DTM1455C")},
            {"ALTER DYNAMIC TABLE dtm1455 RENAME TO dtn1455", "Statement executed successfully."},
            {"SELECT * FROM dtn1455", notInitialized("DTN1455")},
        });
    }

    @Test
    public void aDownstreamTableWaitsForATableThatReadsIt() {
        assertEquals("Dynamic table DTO1455 successfully created.",
            create("dto1455", "DOWNSTREAM", "INITIALIZE = ON_SCHEDULE AS SELECT a FROM src1455"));
        assertAnswer(notInitialized("DTO1455"), "SELECT * FROM dto1455");
        assertEquals("Dynamic table DTP1455 successfully created.", create("dtp1455", "AS SELECT a FROM dto1455"));
        assertAnswer("1 ;; 2", "SELECT * FROM dto1455 ORDER BY 1");
    }
}
