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
 * A derived table, a CTE or a view whose select list holds a window call and whose QUALIFY reads none answers
 * whatever the query around it reads of its columns: the window call the QUALIFY needs stays computed when the
 * columns nobody reads are dropped (all live-verified).
 */
public class DerivedQualifyPruningTest extends MeasuredCellsTestSupport {

    @Test
    public void aQualifyKeepsItsWindowWhenTheReaderReadsNone() {
        engine.execute("CREATE OR REPLACE TABLE people (id NUMBER, name VARCHAR)");
        engine.execute("INSERT INTO people VALUES (1, 'ann'), (2, 'bob')");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY 1 = 1)");
        assertAnswer("2",
            "SELECT COUNT(id) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY 1 = 1)");
        assertAnswer("1 ;; 1",
            "SELECT 1 FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY id > 0)");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY TRUE)");
        assertAnswer("2",
            "SELECT COUNT(rn) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY 1 = 1)");
        assertAnswer("1|1 ;; 2|2",
            "SELECT * FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY 1 = 1) ORDER BY id");
        assertAnswer("1",
            "SELECT COUNT(*) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY rn = 1)");
        assertAnswer("2",
            "WITH d AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY 1 = 1) SELECT COUNT(*) "
                + "FROM d");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 44 | found QUALIFY clause but no window "
                + "function.",
            "SELECT COUNT(*) FROM (SELECT id FROM people QUALIFY 1 = 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | found QUALIFY clause but no window "
                + "function.",
            "SELECT COUNT(*) FROM (SELECT id, name FROM people QUALIFY id > 0)");
        assertAnswer("2", "SELECT id FROM (SELECT id, SUM(id) OVER () AS s FROM people QUALIFY id > 1)");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) FROM people QUALIFY 1 = 1)");
        assertAnswer("bob",
            "SELECT name FROM (SELECT name, id, RANK() OVER (ORDER BY id) AS r FROM people QUALIFY id = 2)");
        assertAnswer("View DQV successfully created.",
            "CREATE OR REPLACE VIEW dqv AS SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY 1 = 1");
        assertAnswer("2", "SELECT COUNT(*) FROM dqv");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT * FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people "
                + "QUALIFY 1 = 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT id, MAX(id) OVER () AS m FROM people QUALIFY m > 0)");
    }
}
