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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A CREATE EVENT TABLE … CLONE takes no property of its own: the token after the source, or after the point of time
 * the clone is made at, is refused where it stands, and a CLONE with no source at the end of the statement. The point
 * is judged as a table's clone judges it, and a materialized view is no source (all live-verified).
 */
public class EventTableCloneTailTest extends MeasuredCellsTestSupport {

    @Test
    public void theTokenAfterTheSourceIsRefused() {
        engine.execute("CREATE OR REPLACE EVENT TABLE base_et");
        assertCells(new String[][] {
            {"CREATE EVENT TABLE c5 CLONE base_et COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'COMMENT'."},
            {"CREATE EVENT TABLE c9 CLONE base_et CLONE base_et",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'CLONE'."},
            {"CREATE EVENT TABLE c10 CLONE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'."},
            {"CREATE EVENT TABLE c11 CLONE base_et x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'x'."},
            {"CREATE OR REPLACE EVENT TABLE c12 CLONE base_et CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'CHANGE_TRACKING'."},
            {"CREATE EVENT TABLE c13 CLONE base_et DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'."},
            {"CREATE EVENT TABLE IF NOT EXISTS c15 CLONE base_et COPY GRANTS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'COPY'."},
            {"CREATE EVENT TABLE c16 CLONE base_et;", "Table C16 successfully created."},
            {"CREATE EVENT TABLE c17 CLONE base_et (a INT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '('."},
        });
    }

    @Test
    public void aCloneMayBeMadeAtAPointOfTime() {
        engine.execute("CREATE OR REPLACE EVENT TABLE base_et");
        engine.execute("CREATE OR REPLACE TABLE bt (x INT)");
        engine.execute("INSERT INTO bt VALUES (1)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mvs AS SELECT x FROM bt");
        engine.execute("CREATE OR REPLACE VIEW vs AS SELECT x FROM bt");
        assertCells(new String[][] {
            {"CREATE EVENT TABLE c20 CLONE mvs",
                "REFUSED SQL compilation error: | Invalid operation Cannot clone an Event table from a "
                + "materialized view."},
            {"CREATE EVENT TABLE c22 CLONE base_et AT (TIMESTAMP => DATEADD(day, 1, CURRENT_TIMESTAMP()))",
                "REFUSED Future data is not yet available for table BASE_ET."},
            {"CREATE EVENT TABLE c23 CLONE bt",
                "REFUSED SQL compilation error: | Invalid operation Cannot clone an Event table from a table."},
            {"CREATE EVENT TABLE c24 CLONE nosuch_et",
                "REFUSED SQL compilation error: | Object 'NOSUCH_ET' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_ET."},
            {"CREATE EVENT TABLE c25 CLONE base_et AT (OFFSET => -1) COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'COMMENT'."},
            {"CREATE EVENT TABLE c26 CLONE vs", "REFUSED SQL compilation error: Cannot clone from a view object."},
            {"CREATE EVENT TABLE c28 CLONE base_et AT (OFFSET => 'x')",
                "REFUSED SQL compilation error: | Invalid data type ['x'] in AT(OFFSET => 'x')"},
            {"CREATE EVENT TABLE c29 CLONE base_et AT (TIMESTAMP => CURRENT_TIMESTAMP())",
                "Table C29 successfully created."},
            {"CREATE EVENT TABLE c30 CLONE base_et AT (TIMESTAMP => CURRENT_TIMESTAMP()) CLONE base_et",
                "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'CLONE'."},
            {"CREATE EVENT TABLE c31 CLONE base_et AT (TIMESTAMP => CURRENT_TIMESTAMP());",
                "Table C31 successfully created."},
        });
    }
}
