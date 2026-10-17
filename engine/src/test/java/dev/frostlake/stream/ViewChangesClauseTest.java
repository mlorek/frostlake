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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.query.MeasuredCellsTestSupport;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A CHANGES clause over a view reads the view's changes, as a stream on the view reports them, and a view tracks its
 * changes once a stream is created on it or it is created with CHANGE_TRACKING = TRUE (live-verified).
 */
public class ViewChangesClauseTest extends MeasuredCellsTestSupport {

    private static final String DEFAULT_AT_CSV = " CHANGES (INFORMATION => DEFAULT) AT (STREAM => 'csv')";

    private void viewWithStream() {
        answer("CREATE OR REPLACE TABLE mt (id INTEGER, s STRING)");
        answer("CREATE OR REPLACE VIEW cv AS SELECT id, s FROM mt");
        answer("CREATE OR REPLACE VIEW cv2 AS SELECT id, s FROM mt");
        answer("CREATE OR REPLACE STREAM csv ON VIEW cv");
        answer("INSERT INTO mt VALUES (1, 'a'), (2, 'b')");
    }

    @Test
    public void theViewsChangesAreReadFromAStreamsOffset() {
        viewWithStream();
        assertCells(new String[][] {
            {"SELECT id, s, METADATA$ACTION, METADATA$ISUPDATE FROM cv" + DEFAULT_AT_CSV + " ORDER BY id",
                "1|a|INSERT|false ;; 2|b|INSERT|false"},
            {"SELECT id FROM cv CHANGES (INFORMATION => APPEND_ONLY) AT (STREAM => 'csv') ORDER BY id", "1 ;; 2"},
            {"SELECT LENGTH(METADATA$ROW_ID) > 0 FROM cv" + DEFAULT_AT_CSV + " ORDER BY id", "true ;; true"},
        });
        assertEquals("ID:NUMBER(38,0), S:VARCHAR(16777216), METADATA$ROW_ID:VARCHAR(134217728), "
            + "METADATA$ACTION:VARCHAR(134217728), METADATA$ISUPDATE:BOOLEAN",
            shape("SELECT * FROM cv" + DEFAULT_AT_CSV));
        answer("UPDATE mt SET s = 'z' WHERE id = 1");
        answer("DELETE FROM mt WHERE id = 2");
        assertCells(new String[][] {
            {"SELECT id, s, METADATA$ACTION, METADATA$ISUPDATE FROM cv" + DEFAULT_AT_CSV
                + " ORDER BY id, METADATA$ACTION", "1|z|INSERT|false"},
            {"SELECT id, s, METADATA$ACTION FROM cv CHANGES (INFORMATION => APPEND_ONLY) AT (STREAM => 'csv') "
                + "ORDER BY id", "1|a|INSERT ;; 2|b|INSERT"},
        });
        answer("CREATE OR REPLACE TABLE sink AS SELECT id FROM csv");
        answer("INSERT INTO mt VALUES (3, 'c')");
        answer("UPDATE mt SET s = 'y' WHERE id = 1");
        assertCells(new String[][] {
            {"SELECT id, s, METADATA$ACTION, METADATA$ISUPDATE FROM cv" + DEFAULT_AT_CSV
                + " ORDER BY id, METADATA$ACTION", "1|z|DELETE|true ;; 1|y|INSERT|true ;; 3|c|INSERT|false"},
            {"SELECT id, s, METADATA$ACTION, METADATA$ISUPDATE FROM csv ORDER BY id, METADATA$ACTION",
                "1|z|DELETE|true ;; 1|y|INSERT|true ;; 3|c|INSERT|false"},
        });
    }

    @Test
    public void aStreamOnTheTableTheViewReadsGivesTheOffsetToo() {
        answer("CREATE OR REPLACE TABLE mt (id INTEGER, s STRING)");
        answer("CREATE OR REPLACE VIEW cv AS SELECT id, s FROM mt");
        answer("CREATE OR REPLACE STREAM csv ON VIEW cv");
        answer("CREATE OR REPLACE STREAM tst ON TABLE mt");
        answer("INSERT INTO mt VALUES (1, 'a'), (2, 'b')");
        assertAnswer("1|INSERT ;; 2|INSERT", "SELECT id, METADATA$ACTION FROM cv CHANGES (INFORMATION => DEFAULT) "
            + "AT (STREAM => 'tst') ORDER BY id");
    }

    @Test
    public void aFilteredAndAUnionView() {
        answer("CREATE OR REPLACE TABLE mt (id INTEGER, s STRING)");
        answer("CREATE OR REPLACE VIEW fv AS SELECT id, UPPER(s) AS u FROM mt WHERE id > 1");
        answer("CREATE OR REPLACE STREAM fsv ON VIEW fv");
        answer("CREATE OR REPLACE VIEW uv AS SELECT id, s FROM mt UNION ALL SELECT id, s FROM mt");
        answer("CREATE OR REPLACE STREAM usv ON VIEW uv");
        answer("INSERT INTO mt VALUES (4, 'd'), (0, 'e')");
        assertCells(new String[][] {
            {"SELECT id, u, METADATA$ACTION FROM fv CHANGES (INFORMATION => DEFAULT) AT (STREAM => 'fsv') "
                + "ORDER BY id", "4|D|INSERT"},
            {"SELECT id, s, METADATA$ACTION FROM uv CHANGES (INFORMATION => DEFAULT) AT (STREAM => 'usv') "
                + "ORDER BY id", "0|e|INSERT ;; 0|e|INSERT ;; 4|d|INSERT ;; 4|d|INSERT"},
        });
    }

    @Test
    public void whatTheClauseRefuses() {
        viewWithStream();
        assertCells(new String[][] {
            {"SELECT * FROM cv2" + DEFAULT_AT_CSV, "REFUSED SQL compilation error: Change tracking is not enabled or "
                + "has been missing for the time range requested on table 'TEST_DB.TEST_SCHEMA.CV2'."},
            {"SELECT COUNT(*) FROM cv CHANGES (INFORMATION => DEFAULT) AT (STREAM => 'nosuch')",
                "REFUSED Stream 'nosuch' not found."},
            {"SELECT id FROM cv CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP => DATEADD(second, -1, "
                + "CURRENT_TIMESTAMP()))", "REFUSED SQL compilation error: line 1 at position 88: | Query contains "
                + "time travel to a version containing function 'CURRENT_TIMESTAMP', but change tracking is not "
                + "supported on queries with non-deterministic functions in versions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.CV')."},
        });
    }

    @Test
    public void aViewTracksItsChangesOnceAStreamIsCreatedOnIt() {
        viewWithStream();
        answer("CREATE OR REPLACE VIEW cv3 CHANGE_TRACKING = TRUE AS SELECT id FROM mt");
        answer("SHOW VIEWS LIKE 'CV%'");
        assertAnswer("CV|ON ;; CV2|OFF ;; CV3|ON",
            "SELECT \"name\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        answer("DROP STREAM csv");
        answer("SHOW VIEWS LIKE 'CV'");
        assertAnswer("CV|ON", "SELECT \"name\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void anEngineCopyKeepsTheFlag() throws Exception {
        assumeFalse(isLiveSnowflake(), "an engine copy is the embedded engine's own");
        viewWithStream();
        final DatabaseEngine copy = engine.cloneInstance();
        try {
            copy.executeQuery("USE SCHEMA test_db.test_schema");
            copy.executeQuery("SHOW VIEWS LIKE 'CV%'");
            final ResultSet listed = copy.executeQuery(
                "SELECT \"name\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            final StringBuilder seen = new StringBuilder();
            while (listed.next()) {
                seen.append(seen.length() == 0 ? "" : " ;; ").append(listed.getValue(0)).append('|')
                    .append(listed.getValue(1));
            }
            assertEquals("CV|ON ;; CV2|OFF", seen.toString());
        } finally {
            copy.shutdown();
        }
    }
}
