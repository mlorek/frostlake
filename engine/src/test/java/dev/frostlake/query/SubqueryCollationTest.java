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
 * A subquery compares a column under the column's collation wherever the column is (live-verified): a correlated
 * EXISTS, NOT EXISTS or scalar subquery reads an outer column's collation, through an alias, a bare name and a
 * join, as the comparison beside the subquery does; a row-value IN, NOT IN, = ANY and <> ALL against a subquery or
 * a list of rows compare each place under the collation of its two sides; and two collations that differ are
 * refused as incompatible.
 */
public class SubqueryCollationTest extends MeasuredCellsTestSupport {

    @Test
    public void aCorrelatedSubqueryReadsTheOuterColumnsCollation() {
        engine.execute("CREATE OR REPLACE TABLE o2 (id INT, s VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO o2 VALUES (1, 'abc')");
        engine.execute("CREATE OR REPLACE TABLE i2 (id INT, s VARCHAR)");
        engine.execute("INSERT INTO i2 VALUES (1, 'ABC')");
        assertAnswer("true", "SELECT o2.s = i2.s FROM o2, i2");
        assertAnswer("1", "SELECT COUNT(*) FROM o2 JOIN i2 ON i2.id = o2.id AND i2.s = o2.s");
        assertAnswer("1|true", "SELECT o2.id, EXISTS (SELECT 1 FROM i2 WHERE i2.s = o2.s) e1 FROM o2");
        assertAnswer("1|true",
            "SELECT o2.id, EXISTS (SELECT 1 FROM i2 WHERE i2.id = o2.id AND i2.s = o2.s) e2 FROM o2");
        assertAnswer("1|1", "SELECT o2.id, (SELECT COUNT(*) FROM i2 WHERE i2.id = o2.id AND i2.s = o2.s) c2 FROM o2");
        assertAnswer("1|true",
            "SELECT i2.id, EXISTS (SELECT 1 FROM o2 WHERE o2.id = i2.id AND o2.s = i2.s) e4 FROM i2");
        assertAnswer("1", "SELECT o2.id FROM o2 WHERE o2.s IN (SELECT s FROM i2)");
        assertAnswer("1|1", "SELECT o2.id, (SELECT MAX(i2.id) FROM i2 WHERE i2.s = o2.s) m FROM o2");
        assertAnswer("", "SELECT o2.id FROM o2 WHERE NOT EXISTS (SELECT 1 FROM i2 WHERE i2.s = o2.s)");
        assertAnswer("1", "SELECT COUNT(*) FROM o2 WHERE EXISTS (SELECT 1 FROM i2 WHERE i2.s = o2.s)");
    }

    @Test
    public void aRowValueInComparesEachPlaceUnderItsCollation() {
        engine.execute("CREATE OR REPLACE TABLE ro (id INT, s VARCHAR)");
        engine.execute("INSERT INTO ro VALUES (1, 'abc')");
        engine.execute("CREATE OR REPLACE TABLE ri (id INT, s VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO ri VALUES (1, 'ABC')");
        assertAnswer("1", "SELECT ro.id FROM ro WHERE ro.s IN (SELECT s FROM ri)");
        assertAnswer("1", "SELECT ro.id FROM ro WHERE (ro.id, ro.s) IN (SELECT id, s FROM ri)");
        assertAnswer("1", "SELECT ro.id FROM ro WHERE (ro.s, ro.id) IN (SELECT s, id FROM ri)");
        assertAnswer("", "SELECT ro.id FROM ro WHERE (ro.id, ro.s) NOT IN (SELECT id, s FROM ri)");
        assertAnswer("1", "SELECT ro.id FROM ro WHERE (ro.id, ro.s) = ANY (SELECT id, s FROM ri)");
        assertAnswer("true", "SELECT (ro.id, ro.s) IN (SELECT id, s FROM ri) FROM ro");
        assertAnswer("", "SELECT ro.id FROM ro WHERE (ro.id, ro.s) IN ((1, 'ABC'))");
        assertAnswer("1", "SELECT ro.id FROM ro, ri WHERE (ro.id, ro.s) = (ri.id, ri.s)");
    }

    @Test
    public void anAliasAJoinANestingAndAListKeepTheCollation() {
        engine.execute("CREATE OR REPLACE TABLE o3 (id INT, s VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO o3 VALUES (1, 'abc'), (2, 'xyz')");
        engine.execute("CREATE OR REPLACE TABLE i3 (id INT, s VARCHAR)");
        engine.execute("INSERT INTO i3 VALUES (1, 'ABC'), (2, 'Xyz')");
        engine.execute("CREATE OR REPLACE TABLE j3 (id INT, t VARCHAR)");
        engine.execute("INSERT INTO j3 VALUES (1, 'q'), (2, 'r')");
        engine.execute("CREATE OR REPLACE TABLE k3 (id INT, s VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO k3 VALUES (1, 'ABC')");
        assertAnswer("1|true ;; 2|true",
            "SELECT x.id, EXISTS (SELECT 1 FROM i3 WHERE i3.s = x.s) e FROM o3 x ORDER BY 1");
        assertAnswer("1|true ;; 2|false",
            "SELECT o3.id, EXISTS (SELECT 1 FROM j3 WHERE j3.id = o3.id AND 'ABC' = s) e FROM o3 ORDER BY 1");
        assertAnswer("1|true ;; 2|true",
            "SELECT o3.id, EXISTS (SELECT 1 FROM i3 WHERE i3.s = o3.s) e FROM o3 JOIN j3 ON j3.id = o3.id ORDER BY 1");
        assertAnswer("1|true ;; 2|false",
            "SELECT o3.id, EXISTS (SELECT 1 FROM j3 jj WHERE jj.id = o3.id AND s = 'ABC') e FROM o3 JOIN j3 ON j3.id "
                + "= o3.id ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT o3.id, EXISTS (SELECT 1 FROM j3 WHERE j3.id = o3.id AND EXISTS (SELECT 1 FROM i3 WHERE i3.s = "
                + "o3.s)) e FROM o3 ORDER BY 1");
        assertAnswer("1|2 ;; 2|null",
            "SELECT o3.id, (SELECT MAX(i3.id) FROM i3 WHERE i3.s > o3.s) m FROM o3 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Incompatible collations: 'en-ci' and 'en-cs'",
            "SELECT o3.id, EXISTS (SELECT 1 FROM k3 WHERE k3.s = o3.s) e FROM o3 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'",
            "SELECT id FROM o3 WHERE s IN (SELECT s FROM k3) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'",
            "SELECT id FROM o3 WHERE (id, s) IN (SELECT id, s FROM k3) ORDER BY 1");
        assertAnswer("1", "SELECT id FROM o3 WHERE (id, s) IN ((1, 'ABC')) ORDER BY 1");
        assertAnswer("1", "SELECT id FROM i3 WHERE (id, s) IN ((1, 'abc' COLLATE 'en-ci')) ORDER BY 1");
        assertAnswer("1 ;; 2", "SELECT id FROM i3 WHERE (id, s) = ANY (SELECT id, s FROM o3) ORDER BY 1");
        assertAnswer("", "SELECT id FROM i3 WHERE (id, s) <> ALL (SELECT id, s FROM o3) ORDER BY 1");
        assertAnswer("1|1 ;; 2|1",
            "SELECT o3.id, (SELECT COUNT(*) FROM i3 WHERE i3.s = UPPER(o3.s)) c FROM o3 ORDER BY 1");
        assertAnswer("1|0 ;; 2|0",
            "SELECT o3.id, (SELECT COUNT(*) FROM i3 WHERE i3.s = o3.s COLLATE 'en-cs') c FROM o3 ORDER BY 1");
    }
}
