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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A stream on a view over a view, or over a FROM-clause subquery, follows the fully expanded query: filters and
 * projections on every level, a UNION ALL arm through a view, a view over a UNION ALL view, an inner column list,
 * inner expressions, FROM aliases and their column lists, and an inner view in another schema whose names are read
 * where it lives; SHOW STREAMS lists the tables at the bottom, a comma and a space between two, and the stream turns
 * change tracking on for the views below it. A view over a join view stays refused (all live-verified).
 */
public class NestedViewStreamTest extends MeasuredCellsTestSupport {

    @Test
    public void viewOverViewAndSubquery() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.nt (id INT, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.nv AS SELECT id, secret FROM "
            + "test_db.test_schema.nt");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.nvv AS SELECT * FROM test_db.test_schema.nv");
        engine.execute("ALTER VIEW test_db.test_schema.nv SET CHANGE_TRACKING = TRUE");
        engine.execute("ALTER VIEW test_db.test_schema.nvv SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.nvq AS SELECT * FROM (SELECT id FROM "
            + "test_db.test_schema.nt)");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.nv3 AS SELECT id FROM test_db.test_schema.nvv");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.nvg AS SELECT id, COUNT(*) c FROM "
            + "test_db.test_schema.nv GROUP BY id");
        assertAnswer("Stream NS2 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.ns2 ON VIEW test_db.test_schema.nvv");
        assertAnswer("Stream NS4 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.ns4 ON VIEW test_db.test_schema.nvq");
        assertAnswer("Stream NS6 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.ns6 ON VIEW test_db.test_schema.nv3");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 100: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.NVG').",
            "CREATE OR REPLACE STREAM test_db.test_schema.ns5 ON VIEW test_db.test_schema.nvg");
        assertAnswer("1", "INSERT INTO test_db.test_schema.nt VALUES (1, 'a')");
        assertAnswer("1|a|INSERT", "SELECT id, secret, METADATA$ACTION FROM test_db.test_schema.ns2");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM test_db.test_schema.ns4");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM test_db.test_schema.ns6");
        assertAnswer("1|a",
            "SELECT id, secret FROM test_db.test_schema.nvv CHANGES(INFORMATION => DEFAULT) AT(STREAM => "
                + "'test_db.test_schema.ns2')");
        assertAnswer("TEST_DB.TEST_SCHEMA.NVV|View|TEST_DB.TEST_SCHEMA.NT",
            "SHOW STREAMS LIKE 'NS2' IN SCHEMA test_db.test_schema ->> SELECT \"table_name\", \"source_type\", "
                + "\"base_tables\" FROM $1");
        assertAnswer("TEST_DB.TEST_SCHEMA.NVQ|View|TEST_DB.TEST_SCHEMA.NT",
            "SHOW STREAMS LIKE 'NS4' IN SCHEMA test_db.test_schema ->> SELECT \"table_name\", \"source_type\", "
                + "\"base_tables\" FROM $1");
    }

    @Test
    public void nestedShapes() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.at (id INT, secret STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.at2 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.av AS SELECT id, secret FROM "
            + "test_db.test_schema.at");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.au AS SELECT id FROM test_db.test_schema.av UNION "
            + "ALL SELECT id FROM test_db.test_schema.at2");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.au2 AS SELECT id FROM test_db.test_schema.at "
            + "UNION ALL SELECT id FROM test_db.test_schema.at2");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.avu AS SELECT id FROM test_db.test_schema.au2");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aj AS SELECT a.id, b.v FROM "
            + "test_db.test_schema.at a JOIN test_db.test_schema.at2 b ON a.id = b.id");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.avj AS SELECT * FROM test_db.test_schema.aj");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aw1 AS SELECT id, secret FROM "
            + "test_db.test_schema.at WHERE id > 0");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aw2 AS SELECT id FROM test_db.test_schema.aw1 "
            + "WHERE id < 10");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.ac1 (k, s) AS SELECT id, secret FROM "
            + "test_db.test_schema.at");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.ac2 AS SELECT k, UPPER(s) us FROM "
            + "test_db.test_schema.ac1");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.ae1 AS SELECT id * 10 AS x FROM "
            + "test_db.test_schema.at");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.ae2 AS SELECT x + 1 AS y FROM "
            + "test_db.test_schema.ae1");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aq2 AS SELECT id FROM (SELECT id, secret FROM "
            + "test_db.test_schema.at WHERE secret <> 'x') WHERE id > 0");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aqa AS SELECT s.id FROM (SELECT id FROM "
            + "test_db.test_schema.at) s");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aqu AS SELECT id FROM (SELECT id FROM "
            + "test_db.test_schema.at) UNION ALL SELECT id FROM test_db.test_schema.at2");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aql AS SELECT id FROM (SELECT id FROM "
            + "test_db.test_schema.at LIMIT 5)");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aqd AS SELECT id FROM (SELECT DISTINCT id FROM "
            + "test_db.test_schema.at)");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.aqg AS SELECT * FROM (SELECT id, COUNT(*) c FROM "
            + "test_db.test_schema.at GROUP BY id)");
        engine.execute("ALTER VIEW test_db.test_schema.aw2 SET CHANGE_TRACKING = TRUE");
        engine.execute("SET ts771 = CURRENT_TIMESTAMP()");
        assertAnswer("Stream AS1 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as1 ON VIEW test_db.test_schema.au");
        assertAnswer("Stream AS2 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as2 ON VIEW test_db.test_schema.avu");
        answer("CREATE OR REPLACE STREAM test_db.test_schema.as3 ON VIEW test_db.test_schema.avj");
        assertAnswer("Stream AS4 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as4 ON VIEW test_db.test_schema.aw2");
        assertAnswer("Stream AS5 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as5 ON VIEW test_db.test_schema.ac2");
        assertAnswer("Stream AS6 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as6 ON VIEW test_db.test_schema.ae2");
        assertAnswer("Stream AS7 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as7 ON VIEW test_db.test_schema.aq2");
        assertAnswer("Stream AS8 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as8 ON VIEW test_db.test_schema.aqa");
        assertAnswer("Stream AS9 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.as9 ON VIEW test_db.test_schema.aqu");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.AQL').",
            "CREATE OR REPLACE STREAM test_db.test_schema.as10 ON VIEW test_db.test_schema.aql");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 66: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.AQD').",
            "CREATE OR REPLACE STREAM test_db.test_schema.as11 ON VIEW test_db.test_schema.aqd");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 115: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.AQG').",
            "CREATE OR REPLACE STREAM test_db.test_schema.as12 ON VIEW test_db.test_schema.aqg");
        assertAnswer("3", "INSERT INTO test_db.test_schema.at VALUES (1, 'a'), (20, 'b'), (-1, 'x')");
        assertAnswer("2", "INSERT INTO test_db.test_schema.at2 VALUES (1, 'p'), (2, 'q')");
        assertAnswer("-1|INSERT ;; 1|INSERT ;; 1|INSERT ;; 2|INSERT ;; 20|INSERT",
            "SELECT id, METADATA$ACTION FROM test_db.test_schema.as1 ORDER BY id");
        assertAnswer("-1|INSERT ;; 1|INSERT ;; 1|INSERT ;; 2|INSERT ;; 20|INSERT",
            "SELECT id, METADATA$ACTION FROM test_db.test_schema.as2 ORDER BY id");
        answer("SELECT id, v, METADATA$ACTION FROM test_db.test_schema.as3 ORDER BY id");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM test_db.test_schema.as4 ORDER BY id");
        assertAnswer("-1|X|VARCHAR(50331648)[LOB]|INSERT ;; 1|A|VARCHAR(50331648)[LOB]|INSERT ;; 20|B|"
                + "VARCHAR(50331648)[LOB]|INSERT",
            "SELECT k, us, SYSTEM$TYPEOF(us), METADATA$ACTION FROM test_db.test_schema.as5 ORDER BY k");
        assertAnswer("-9|NUMBER(38,0)[SB16]|INSERT ;; 11|NUMBER(38,0)[SB16]|INSERT ;; 201|NUMBER(38,0)[SB16]|INSERT",
            "SELECT y, SYSTEM$TYPEOF(y), METADATA$ACTION FROM test_db.test_schema.as6 ORDER BY y");
        assertAnswer("1|INSERT ;; 20|INSERT", "SELECT id, METADATA$ACTION FROM test_db.test_schema.as7 ORDER BY id");
        assertAnswer("-1|INSERT ;; 1|INSERT ;; 20|INSERT",
            "SELECT id, METADATA$ACTION FROM test_db.test_schema.as8 ORDER BY id");
        assertAnswer("-1|INSERT ;; 1|INSERT ;; 1|INSERT ;; 2|INSERT ;; 20|INSERT",
            "SELECT id, METADATA$ACTION FROM test_db.test_schema.as9 ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM test_db.test_schema.aw2 CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $ts771) ORDER BY "
                + "id");
        assertAnswer("-9 ;; 11 ;; 201",
            "SELECT y FROM test_db.test_schema.ae2 CHANGES(INFORMATION => DEFAULT) AT(STREAM => "
                + "'test_db.test_schema.as6') ORDER BY y");
        answer("SHOW STREAMS LIKE 'AS%' IN SCHEMA test_db.test_schema ->> SELECT \"name\", \"base_tables\" FROM $1 "
            + "ORDER BY \"name\"");
        answer("SHOW VIEWS LIKE 'A%' IN SCHEMA test_db.test_schema ->> SELECT \"name\", \"change_tracking\" FROM $1 "
            + "ORDER BY \"name\"");
    }

    @Test
    public void aliasesAndInnerNames() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.ft (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.ft2 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.fa AS SELECT x.id, x.v FROM "
            + "test_db.test_schema.ft x WHERE x.id > 0");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.fl AS SELECT k FROM (SELECT id FROM "
            + "test_db.test_schema.ft) s(k)");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.fd AS SELECT y FROM (SELECT id * 10 AS y, "
            + "UPPER(v) AS u FROM test_db.test_schema.ft)");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.fu AS SELECT id AS a FROM test_db.test_schema.ft "
            + "UNION ALL SELECT id + 100 AS b FROM test_db.test_schema.ft2");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.fuv AS SELECT a FROM test_db.test_schema.fu WHERE "
            + "a < 150");
        assertAnswer("Stream FS1 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.fs1 ON VIEW test_db.test_schema.fa");
        assertAnswer("Stream FS2 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.fs2 ON VIEW test_db.test_schema.fl");
        assertAnswer("Stream FS3 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.fs3 ON VIEW test_db.test_schema.fd");
        assertAnswer("Stream FS4 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.fs4 ON VIEW test_db.test_schema.fuv");
        assertAnswer("2", "INSERT INTO test_db.test_schema.ft VALUES (1, 'a'), (-1, 'b')");
        assertAnswer("2", "INSERT INTO test_db.test_schema.ft2 VALUES (2, 'c'), (70, 'd')");
        assertAnswer("1|a|INSERT", "SELECT id, v, METADATA$ACTION FROM test_db.test_schema.fs1 ORDER BY id");
        assertAnswer("-1|NUMBER(38,0)[SB16]|INSERT ;; 1|NUMBER(38,0)[SB16]|INSERT",
            "SELECT k, SYSTEM$TYPEOF(k), METADATA$ACTION FROM test_db.test_schema.fs2 ORDER BY k");
        assertAnswer("-10|NUMBER(38,0)[SB16]|INSERT ;; 10|NUMBER(38,0)[SB16]|INSERT",
            "SELECT y, SYSTEM$TYPEOF(y), METADATA$ACTION FROM test_db.test_schema.fs3 ORDER BY y");
        assertAnswer("-1|INSERT ;; 1|INSERT ;; 102|INSERT",
            "SELECT a, METADATA$ACTION FROM test_db.test_schema.fs4 ORDER BY a");
        assertAnswer("3", "SELECT * FROM test_db.test_schema.fs4 ORDER BY 1 ->> SELECT COUNT(*) FROM $1");
    }

    @Test
    public void innerViewInAnotherSchema() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_db.p772");
        engine.execute("CREATE OR REPLACE TABLE test_db.p772.gt (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.gt (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE VIEW test_db.p772.gv AS SELECT id FROM gt");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.gvv AS SELECT id FROM p772.gv");
        assertAnswer("Stream GS1 successfully created.",
            "CREATE OR REPLACE STREAM test_db.test_schema.gs1 ON VIEW test_db.test_schema.gvv");
        assertAnswer("1", "INSERT INTO test_db.p772.gt VALUES (1)");
        assertAnswer("1", "INSERT INTO test_db.test_schema.gt VALUES (2)");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM test_db.test_schema.gs1 ORDER BY id");
        assertAnswer("TEST_DB.P772.GT",
            "SHOW STREAMS LIKE 'GS1' IN SCHEMA test_db.test_schema ->> SELECT \"base_tables\" FROM $1");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.p772");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM test_db.test_schema.gs1 ORDER BY id");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
    }
}
