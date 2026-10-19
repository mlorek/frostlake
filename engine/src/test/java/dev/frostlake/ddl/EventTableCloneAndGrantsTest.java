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
 * CREATE EVENT TABLE … CLONE clones an event table, and only one — a table is refused as "Cannot clone an Event
 * table from a table" —, takes no property beside the clone, and keeps the event table's fixed columns; SHOW GRANTS
 * ON MATERIALIZED VIEW finds the relation of the name whatever its kind (all live-verified).
 */
public class EventTableCloneAndGrantsTest extends MeasuredCellsTestSupport {

    @Test
    public void anEventTableClonesAnEventTable() {
        engine.execute("CREATE OR REPLACE EVENT TABLE et_e");
        engine.execute("CREATE OR REPLACE TABLE et_t (a INT)");
        engine.execute("CREATE OR REPLACE TABLE al_base (a INT)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW al_mv AS SELECT a FROM al_base");
        assertAnswer("Table ET_E2 successfully created.", "CREATE OR REPLACE EVENT TABLE et_e2 CLONE et_e");
        answer("SHOW EVENT TABLES LIKE 'ET_E2'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier '\"kind\"'",
            "SELECT \"name\", \"kind\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Table ET_E3 successfully created.", "CREATE EVENT TABLE et_e3 CLONE et_e");
        assertAnswer("REFUSED SQL compilation error: | Object 'ET_E3' already exists.",
            "CREATE EVENT TABLE et_e3 CLONE et_e");
        assertAnswer("ET_E3 already exists, statement succeeded.", "CREATE EVENT TABLE IF NOT EXISTS et_e3 CLONE et_e");
        assertAnswer("REFUSED SQL compilation error: | Invalid operation Cannot clone an Event table from a table.",
            "CREATE EVENT TABLE et_e4 CLONE et_t");
        assertAnswer("REFUSED SQL compilation error: | Object 'NO_SUCH_ET' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_ET.",
            "CREATE EVENT TABLE et_e5 CLONE no_such_et");
        answer("CREATE OR REPLACE EVENT TABLE et_e6 CLONE et_e AT (OFFSET => -1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'COMMENT'.",
            "CREATE OR REPLACE EVENT TABLE et_e7 CLONE et_e COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected "
                + "'CHANGE_TRACKING'.",
            "CREATE OR REPLACE EVENT TABLE et_e8 CLONE et_e CHANGE_TRACKING = TRUE");
        assertAnswer("TIMESTAMP|TIMESTAMP_NTZ(9)|COLUMN|Y|null|N|N|null|null|timestamp when event record was added|"
                + "null|null|null ;; START_TIMESTAMP|TIMESTAMP_NTZ(9)|COLUMN|Y|null|N|N|null|null|event period "
                + "starting timestamp for metrics and spans|null|null|null ;; OBSERVED_TIMESTAMP|TIMESTAMP_NTZ(9)|"
                + "COLUMN|Y|null|N|N|null|null|used when capturing logs that do not have an accompanying timestamp|"
                + "null|null|null ;; TRACE|OBJECT|COLUMN|Y|null|N|N|null|null|tracing context|null|null|null ;; "
                + "RESOURCE|OBJECT|COLUMN|Y|null|N|N|null|null|for future use|null|null|null ;; RESOURCE_ATTRIBUTES|"
                + "OBJECT|COLUMN|Y|null|N|N|null|null|attributes that identify the source of an event|null|null|null "
                + ";; SCOPE|OBJECT|COLUMN|Y|null|N|N|null|null|scope for signals|null|null|null ;; SCOPE_ATTRIBUTES|"
                + "OBJECT|COLUMN|Y|null|N|N|null|null|for future use|null|null|null ;; RECORD_TYPE|VARCHAR(16777216)|"
                + "COLUMN|Y|null|N|N|null|null|type of the value in the RECORD field|null|null|null ;; RECORD|OBJECT|"
                + "COLUMN|Y|null|N|N|null|null|fixed fields for each signal type|null|null|null ;; RECORD_ATTRIBUTES|"
                + "OBJECT|COLUMN|Y|null|N|N|null|null|variable attributes for each signal type|null|null|null ;; "
                + "VALUE|VARIANT|COLUMN|Y|null|N|N|null|null|primary event value|null|null|null ;; EXEMPLARS|ARRAY|"
                + "COLUMN|Y|null|N|N|null|null|exemplars for metrics|null|null|null",
            "DESCRIBE TABLE et_e2");
        answer("SHOW GRANTS ON MATERIALIZED VIEW al_mv");
        assertAnswer("OWNERSHIP|MATERIALIZED_VIEW|TEST_DB.TEST_SCHEMA.AL_MV",
            "SELECT \"privilege\", \"granted_on\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Materialized view 'NO_SUCH_MV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_MV.",
            "SHOW GRANTS ON MATERIALIZED VIEW no_such_mv");
        answer("SHOW GRANTS ON VIEW al_mv");
        answer("SHOW GRANTS ON TABLE al_mv");
        answer("SHOW GRANTS ON MATERIALIZED VIEW al_base");
    }
}
