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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A view's body compiles whole whenever a statement reaches the view, whatever the statement reads of it. A
 * body that no longer compiles — its table lost a column the body names — refuses every read with the body's
 * own refusal, at its place in the view's CREATE statement: through the views over it, for a count, a constant,
 * a read that keeps no row, a join, a subquery, a common table expression nobody reads, an EXPLAIN and the
 * CREATE of another view. A body that reads a relation which is gone is refused under the view's name, the
 * relation named by its full path, and each view in between adds its own name in front. A view over a view that
 * was narrowed is refused only when its own body names what is gone. Every answer is live-verified.
 */
public class BrokenViewBodyTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyThatNoLongerCompilesRefusesEveryRead() {
        engine.execute("CREATE OR REPLACE TABLE d4 (a INT, b INT, c INT)");
        engine.execute("INSERT INTO d4 VALUES (1, 2, 3)");
        engine.execute("CREATE OR REPLACE VIEW d4_named (x, y) AS SELECT a, c FROM d4");
        engine.execute("CREATE OR REPLACE VIEW d4_over AS SELECT x FROM d4_named");
        engine.execute("CREATE OR REPLACE VIEW d4_top AS SELECT x FROM d4_over");
        engine.execute("CREATE OR REPLACE VIEW d4_count AS SELECT COUNT(*) AS n FROM d4_named");
        engine.execute("CREATE OR REPLACE VIEW d4_join AS SELECT t.a, o.x FROM d4 t JOIN d4_named o ON o.x = t.a");
        engine.execute("CREATE OR REPLACE VIEW d4_sub AS SELECT a, (SELECT MAX(x) FROM d4_named) AS m FROM d4");
        engine.execute("CREATE OR REPLACE VIEW d4_cte AS WITH q AS (SELECT x FROM d4_named) SELECT x FROM q");
        engine.execute("CREATE OR REPLACE VIEW d4_union AS SELECT a FROM d4 UNION ALL SELECT x FROM d4_named");
        engine.execute("CREATE OR REPLACE TABLE e4 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW e4_v AS SELECT a FROM e4");
        engine.execute("CREATE OR REPLACE VIEW e4_o AS SELECT a FROM e4_v");
        assertAnswer(
            "1",
            "SELECT * FROM d4_top");
        engine.execute("ALTER TABLE d4 DROP COLUMN c");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_named");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_over");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT x FROM d4_over");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_top");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_count");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT COUNT(*) FROM d4_over");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT 1 FROM d4_over");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_over WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_over LIMIT 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_join");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_sub");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_cte");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_union");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT o.x FROM d4_over o JOIN d4 t ON t.a = o.x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT (SELECT MAX(x) FROM d4_over)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "WITH q AS (SELECT * FROM d4_over) SELECT * FROM q");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT 1 WHERE EXISTS (SELECT 1 FROM d4_over)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            """
                SELECT 1,
                x
                FROM d4_top""");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "CREATE OR REPLACE VIEW d4_new AS SELECT x FROM d4_over");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW d4_over");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "INSERT INTO e4 SELECT x FROM d4_over");
    }

    @Test
    public void aReadThatNeedsNothingOfTheBodyIsRefusedToo() {
        engine.execute("CREATE OR REPLACE TABLE d4 (a INT, b INT, c INT)");
        engine.execute("INSERT INTO d4 VALUES (1, 2, 3)");
        engine.execute("CREATE OR REPLACE VIEW d4_named (x, y) AS SELECT a, c FROM d4");
        engine.execute("CREATE OR REPLACE VIEW d4_over AS SELECT x FROM d4_named");
        engine.execute("CREATE OR REPLACE VIEW d4_unread AS SELECT 1 AS k FROM d4 WHERE EXISTS (SELECT 1 FROM d4_named)");
        engine.execute("ALTER TABLE d4 DROP COLUMN c");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT x FROM d4_named");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT COUNT(*) FROM d4_named");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT 1 FROM d4_named");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT x FROM d4_named WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT x FROM d4_named LIMIT 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT q.x FROM (SELECT x FROM d4_named) q");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "WITH q AS (SELECT x FROM d4_named) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "SELECT * FROM d4_unread");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C'",
            "EXPLAIN USING TEXT SELECT x FROM d4_named");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'D4_NAMED'");
    }

    @Test
    public void aBodyReadingARelationThatIsGone() {
        engine.execute("CREATE OR REPLACE TABLE e4 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW e4_v AS SELECT a FROM e4");
        engine.execute("CREATE OR REPLACE VIEW e4_o AS SELECT a FROM e4_v");
        engine.execute("DROP TABLE e4");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_V': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT * FROM e4_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_O': SQL compilation error: "
                + "| Failure during expansion of view 'E4_V': SQL compilation error: | Object "
                + "'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT * FROM e4_o");
        engine.execute("CREATE OR REPLACE TABLE d4 (a INT, b INT, c INT)");
        engine.execute("INSERT INTO d4 VALUES (1, 2, 3)");
        engine.execute("CREATE OR REPLACE VIEW d4_named (x, y) AS SELECT a, c FROM d4");
        engine.execute("CREATE OR REPLACE VIEW d4_over AS SELECT x FROM d4_named");
        engine.execute("CREATE OR REPLACE VIEW d4_unread AS SELECT 1 AS k FROM d4 WHERE EXISTS (SELECT 1 FROM d4_named)");
        engine.execute("CREATE OR REPLACE TABLE e4 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW e4_v AS SELECT a FROM e4");
        engine.execute("CREATE OR REPLACE VIEW e4_o AS SELECT a FROM e4_v");
        engine.execute("CREATE OR REPLACE VIEW e4_j AS SELECT d4.a FROM d4 JOIN e4_v ON e4_v.a = d4.a");
        engine.execute("ALTER TABLE d4 DROP COLUMN c");
        engine.execute("DROP TABLE e4");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_O': SQL compilation error: "
                + "| Failure during expansion of view 'E4_V': SQL compilation error: | Object "
                + "'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT COUNT(*) FROM e4_o");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_O': SQL compilation error: "
                + "| Failure during expansion of view 'E4_V': SQL compilation error: | Object "
                + "'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT * FROM e4_o WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_J': SQL compilation error: "
                + "| Failure during expansion of view 'E4_V': SQL compilation error: | Object "
                + "'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT * FROM e4_j");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_V': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT COUNT(*) FROM e4_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_V': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT * FROM test_db.test_schema.e4_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_V': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "SELECT (SELECT MAX(a) FROM e4_v)");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'E4_V': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.E4' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.E4.",
            "CREATE OR REPLACE VIEW e4_new AS SELECT a FROM e4_v");
    }

    @Test
    public void aViewOverAViewThatChanged() {
        engine.execute("CREATE OR REPLACE TABLE f4 (a INT, b INT)");
        engine.execute("INSERT INTO f4 VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE VIEW f4_star AS SELECT * FROM f4");
        engine.execute("CREATE OR REPLACE VIEW f4_over AS SELECT a FROM f4_star");
        engine.execute("ALTER TABLE f4 DROP COLUMN b");
        assertAnswer(
            "REFUSED SQL compilation error: | View definition for 'TEST_DB.TEST_SCHEMA.F4_STAR' declared 2 "
                + "column(s), but view query produces 1 column(s).",
            "SELECT * FROM f4_star");
        assertAnswer(
            "REFUSED SQL compilation error: | View definition for 'TEST_DB.TEST_SCHEMA.F4_STAR' declared 2 "
                + "column(s), but view query produces 1 column(s).",
            "SELECT * FROM f4_over");
        assertAnswer(
            "REFUSED SQL compilation error: | View definition for 'TEST_DB.TEST_SCHEMA.F4_STAR' declared 2 "
                + "column(s), but view query produces 1 column(s).",
            "SELECT COUNT(*) FROM f4_over");
        engine.execute("CREATE OR REPLACE TABLE k4 (a INT, b INT)");
        engine.execute("INSERT INTO k4 VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE VIEW k4_v AS SELECT a, b FROM k4");
        engine.execute("CREATE OR REPLACE VIEW k4_o AS SELECT a, b FROM k4_v");
        engine.execute("CREATE OR REPLACE VIEW k4_oa AS SELECT a FROM k4_v");
        engine.execute("CREATE OR REPLACE VIEW k4_star AS SELECT * FROM k4_v");
        engine.execute("CREATE OR REPLACE VIEW k4_v AS SELECT a FROM k4");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'B'",
            "SELECT * FROM k4_o");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'B'",
            "SELECT a FROM k4_o");
        assertAnswer(
            "1",
            "SELECT * FROM k4_oa");
        assertAnswer(
            "REFUSED SQL compilation error: | View definition for 'TEST_DB.TEST_SCHEMA.K4_STAR' declared 2 "
                + "column(s), but view query produces 1 column(s).",
            "SELECT * FROM k4_star");
        engine.execute("CREATE OR REPLACE TABLE k4 (a INT, b INT)");
        engine.execute("INSERT INTO k4 VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE VIEW k4_v AS SELECT a, b FROM k4");
        engine.execute("CREATE OR REPLACE VIEW k4_o AS SELECT a, b FROM k4_v");
        engine.execute("CREATE OR REPLACE VIEW k4_oa AS SELECT a FROM k4_v");
        engine.execute("CREATE OR REPLACE VIEW k4_star AS SELECT * FROM k4_v");
        engine.execute("CREATE OR REPLACE VIEW z4_v AS SELECT a FROM k4");
        engine.execute("CREATE OR REPLACE VIEW z4_o AS SELECT a FROM z4_v");
        engine.execute("CREATE OR REPLACE VIEW k4_v AS SELECT a FROM k4");
        engine.execute("DROP VIEW z4_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'Z4_O': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.Z4_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.Z4_V.",
            "SELECT * FROM z4_o");
        assertAnswer(
            "REFUSED SQL compilation error: | Failure during expansion of view 'Z4_O': SQL compilation error: "
                + "| Object 'TEST_DB.TEST_SCHEMA.Z4_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.Z4_V.",
            "SELECT COUNT(*) FROM z4_o");
        engine.execute("CREATE OR REPLACE TABLE z4_v (a INT)");
        assertAnswer(
            "",
            "SELECT * FROM z4_o");
    }
}
