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
 * ALTER VIEW and ALTER MATERIALIZED VIEW read SET as a list of properties and UNSET as a list of names, judged before
 * the view is looked for (live-verified): an item without its value is a syntax error where the list begins, a name
 * the view does not take is an invalid property of its kind, the first of a table hashed by name, a value of another
 * type an invalid value, and a parameter is refused as a privilege. A view refuses a repeated name, a materialized view
 * takes the last one written; ALTER TABLE judges its own list the same way before it applies any of it.
 */
public class ViewPropertyListTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewsSetAndUnsetAreJudgedListsOfProperties() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv0 AS SELECT x, y FROM t1");
        engine.execute("CREATE OR REPLACE VIEW v0 AS SELECT x, y FROM t1");
        assertCells(new String[][] {
            {"ALTER MATERIALIZED VIEW mv0 SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL access control error: | Insufficient privileges to operate on materialized_view 'MV0'."},
            {"ALTER MATERIALIZED VIEW mv0 SET SECURE = 1",
                "REFUSED SQL compilation error: | invalid property 'SECURE' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'x' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'x', SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW mv0 UNSET FOO",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 UNSET COMMENT, SECURE",
                "REFUSED SQL compilation error: | invalid property 'SECURE' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'COMMENT'."},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'a' COMMENT = 'b'", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'a' FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET FOO = 1 COMMENT = 'a'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET BAR = 1 FOO = 2",
                "REFUSED SQL compilation error: | invalid property 'BAR' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET FOO = 1 BAR = 2",
                "REFUSED SQL compilation error: | invalid property 'BAR' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on materialized_view 'MV0'."},
            {"ALTER MATERIALIZED VIEW mv0 UNSET CHANGE_TRACKING",
                "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 UNSET DATA_RETENTION_TIME_IN_DAYS",
                "REFUSED SQL access control error: | Insufficient privileges to operate on materialized_view 'MV0'."},
            {"ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER MATERIALIZED VIEW nosuch_mv SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW nosuch_mv SET SECURE = TRUE",
                "REFUSED SQL compilation error: | invalid property 'SECURE' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'c1', COMMENT = 'c2'", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 5 FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET FOO = 1 COMMENT = 5",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'MATERIALIZED_VIEW'"},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'k' DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL access control error: | Insufficient privileges to operate on materialized_view 'MV0'."},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = TRUE",
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'COMMENT'"},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = x", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW mv0 UNSET COMMENT, COMMENT",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"ALTER MATERIALIZED VIEW mv0 UNSET COMMENT", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW mv0 SET COMMENT = 'z'", "Statement executed successfully."},
        });
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'MV0'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "MV0|z|false"},
            {"ALTER VIEW v0 SET SECURE = TRUE",
                "REFUSED SQL compilation error: | invalid property 'SECURE' for 'VIEW'"},
            {"ALTER VIEW v0 SET FOO = 1", "REFUSED SQL compilation error: | invalid property 'FOO' for 'VIEW'"},
            {"ALTER VIEW v0 SET BAR = 1 FOO = 2", "REFUSED SQL compilation error: | invalid property 'BAR' for 'VIEW'"},
            {"ALTER VIEW v0 UNSET FOO", "REFUSED SQL compilation error: | invalid property 'FOO' for 'VIEW'"},
            {"ALTER VIEW v0 SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V0'."},
            {"ALTER VIEW v0 SET TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V0'."},
            {"ALTER VIEW v0 UNSET TIMEZONE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'V0'."},
            {"ALTER VIEW v0 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"ALTER VIEW v0 SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER VIEW v0 SET COMMENT = 'a' COMMENT = 'b'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"ALTER VIEW v0 SET COMMENT = 'c1', COMMENT = 'c2'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"ALTER VIEW v0 UNSET COMMENT, SECURE",
                "REFUSED SQL compilation error: | invalid property 'SECURE' for 'VIEW'"},
            {"ALTER VIEW v0 UNSET SECURE, COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','."},
            {"ALTER VIEW v0 SET COMMENT = 'x' SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'COMMENT'."},
            {"ALTER VIEW v0 SET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'COMMENT'."},
            {"ALTER VIEW IF EXISTS nosuch_v SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'VIEW'"},
            {"ALTER VIEW nosuch_v SET FOO = 1", "REFUSED SQL compilation error: | invalid property 'FOO' for 'VIEW'"},
            {"ALTER VIEW nosuch_v SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
            {"ALTER VIEW v0 SET COMMENT = 'w'", "Statement executed successfully."},
        });
        engine.execute("SHOW VIEWS LIKE 'V0'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\", \"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "V0|w|false"},
            {"ALTER TABLE t1 SET FOO = 1 BAR = 2",
                "REFUSED SQL compilation error: | invalid property 'BAR' for 'TABLE'"},
            {"ALTER TABLE t1 SET COMMENT = 'tc' FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'"},
        });
        engine.execute("SHOW TABLES LIKE 'T1'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "T1|"},
        });
    }
}
