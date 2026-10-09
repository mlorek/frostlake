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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A stored relation needs a name of its own for every column: a view, a materialized view and a CREATE TABLE
 * … AS SELECT whose query projects one name twice are refused with the first name that repeats an earlier one,
 * whether the items are written out or come from a star. A column list that names the columns apart makes the
 * same query storable, a list that repeats a name is refused itself, and a name that differs in case alone
 * repeats nothing. An unnamed expression is refused first, as a missing column specification. An INSERT names
 * no column and takes such a query as it is. Every answer is live-verified.
 */
public class DuplicateColumnNameAtCreateTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE h (id INT, v INT)");
        engine.execute("INSERT INTO h VALUES (5, 1), (7, 2)");
    }

    @Test
    public void aViewWhoseQueryRepeatsAName() {
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE VIEW vdup AS SELECT 1 a, 2 a");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'VDUP' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VDUP.",
            "SELECT * FROM vdup");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE VIEW vdup4 AS SELECT * FROM (SELECT 1 a, 2 a)");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'ID'",
            "CREATE OR REPLACE VIEW vdup5 AS SELECT fz.id, g.id FROM fz JOIN g ON fz.id = g.id");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE SECURE VIEW vdup7 AS SELECT 1 a, 2 a");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'B'",
            "CREATE OR REPLACE VIEW vdup9 AS SELECT 1 a, 2 b, 3 b, 4 a");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE MATERIALIZED VIEW mvdup AS SELECT id AS a, id AS a FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'V'",
            "CREATE OR REPLACE VIEW vu AS SELECT * FROM h a JOIN h b USING (id)");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'a'",
            "CREATE OR REPLACE VIEW vq AS SELECT 1 \"a\", 2 \"a\"");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE VIEW vun AS SELECT 1 a, 2 a UNION ALL SELECT 3, 4");
    }

    @Test
    public void aColumnListNamesTheColumnsApart() {
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'X'",
            "CREATE OR REPLACE VIEW vdup2 (x, x) AS SELECT 1, 2");
        assertAnswer(
            "View VDUP3 successfully created.",
            "CREATE OR REPLACE VIEW vdup3 (x, y) AS SELECT 1 a, 2 a");
        assertAnswer(
            "1|2",
            "SELECT * FROM vdup3");
        assertAnswer(
            "View VL successfully created.",
            "CREATE OR REPLACE VIEW vl (x, y) AS SELECT * FROM (SELECT 1 a, 2 a)");
        assertAnswer(
            "1|2",
            "SELECT * FROM vl");
        assertAnswer(
            "View VDUP6 successfully created.",
            "CREATE OR REPLACE VIEW vdup6 AS SELECT 1 \"a\", 2 a");
        assertAnswer(
            "View VDUP10 successfully created.",
            "CREATE OR REPLACE VIEW vdup10 AS WITH c AS (SELECT 1 a, 2 a) SELECT COUNT(*) AS n FROM c");
    }

    @Test
    public void aTableCreatedFromAQuery() {
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE TABLE tdup AS SELECT 1 a, 2 a");
        assertAnswer(
            "Table TDUP2 successfully created.",
            "CREATE OR REPLACE TABLE tdup2 (x, y) AS SELECT 1 a, 2 a");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'X'",
            "CREATE OR REPLACE TABLE tdup3 (x, x) AS SELECT 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE TABLE ts AS SELECT * FROM (SELECT 1 a, 2 a)");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'A'",
            "CREATE OR REPLACE TEMPORARY TABLE tt AS SELECT 1 a, 2 a");
        assertAnswer(
            "REFUSED SQL compilation error: | duplicate column name 'ID'",
            "CREATE OR REPLACE TABLE tj AS SELECT * FROM fz JOIN h ON fz.id = h.id");
        assertAnswer(
            "Table TU successfully created.",
            "CREATE OR REPLACE TABLE tu AS SELECT * FROM fz JOIN h USING (id)");
        assertAnswer(
            "5|true|1 ;; 7|false|2",
            "SELECT * FROM tu ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing column specification",
            "CREATE OR REPLACE TABLE tm AS SELECT 1 a, 2 a, 3 + 3");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing column specification",
            "CREATE OR REPLACE VIEW vdup8 AS SELECT 1 + 1, 1 + 1");
    }

    @Test
    public void anInsertNamesNoColumn() {
        assertAnswer(
            "1",
            "INSERT INTO g SELECT 1 a, 2 a");
        assertAnswer(
            "1",
            "INSERT OVERWRITE INTO g SELECT 1 a, 2 a");
    }
}
