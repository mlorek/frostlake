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
 * A row access policy filters its table wherever the table is read (live-verified): each relation of a join - the
 * table on either side, a comma join, USING, both sides of a self-join, the null-extended side of an outer join - a
 * view's own policy where the view is joined, the sources of an UPDATE ... FROM and a DELETE ... USING, and the
 * target of an UPDATE or a DELETE, which changes and counts only the rows the policy lets the session see.
 */
public class RowAccessPolicyJoinTest extends MeasuredCellsTestSupport {

    @Test
    public void aPolicyFiltersEveryRelationItProtects() {
        engine.execute("CREATE OR REPLACE TABLE rt (id INT, s VARCHAR)");
        engine.execute("INSERT INTO rt VALUES (1, 'alpha'), (2, 'beta')");
        engine.execute("CREATE OR REPLACE TABLE ro (id INT, s VARCHAR)");
        engine.execute("INSERT INTO ro VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap1 AS (i INT) RETURNS BOOLEAN -> i = 1");
        engine.execute("ALTER TABLE rt ADD ROW ACCESS POLICY rap1 ON (id)");
        engine.execute("CREATE OR REPLACE VIEW rv AS SELECT * FROM rt");
        engine.execute("CREATE OR REPLACE VIEW rv2 AS SELECT * FROM ro");
        engine.execute("ALTER VIEW rv2 ADD ROW ACCESS POLICY rap1 ON (id)");
        assertCells(new String[][] {
            {"SELECT * FROM rt ORDER BY id", "1|alpha"},
            {"SELECT rt.id, rt.s FROM rt JOIN ro ON ro.id = rt.id ORDER BY 1", "1|alpha"},
            {"SELECT ro.id, rt.s FROM ro JOIN rt ON ro.id = rt.id ORDER BY 1", "1|alpha"},
            {"SELECT ro.id, rt.s FROM ro, rt WHERE ro.id = rt.id ORDER BY 1", "1|alpha"},
            {"SELECT * FROM ro JOIN rt USING (id) ORDER BY 1", "1|a|alpha"},
            {"SELECT ro.id, rt.s FROM ro LEFT JOIN rt ON ro.id = rt.id ORDER BY 1", "1|alpha ;; 2|null"},
            {"SELECT rt.id, ro.id FROM rt RIGHT JOIN ro ON ro.id = rt.id ORDER BY 2", "1|1 ;; null|2"},
            {"SELECT COUNT(*) FROM rt JOIN ro ON TRUE", "2"},
            {"SELECT a.id, b.id FROM rt a JOIN rt b ON a.id = b.id ORDER BY 1", "1|1"},
            {"SELECT x.id, ro.s FROM (SELECT * FROM rt) x JOIN ro ON x.id = ro.id ORDER BY 1", "1|a"},
            {"SELECT rv.id, ro.s FROM rv JOIN ro ON rv.id = ro.id ORDER BY 1", "1|a"},
            {"SELECT rv2.id, rt.s FROM rv2 JOIN rt ON rv2.id = rt.id ORDER BY 1", "1|alpha"},
            {"SELECT ro.id, rv2.s FROM ro JOIN rv2 ON rv2.id = ro.id ORDER BY 1", "1|a"},
            {"SELECT COUNT(*) FROM ro WHERE id IN (SELECT id FROM rt)", "1"},
            {"SELECT ro.id, l.s FROM ro, LATERAL (SELECT s FROM rt WHERE rt.id = ro.id) l ORDER BY 1", "1|alpha"},
            {"SELECT ro.id FROM ro JOIN rt ON ro.id = rt.id GROUP BY ro.id ORDER BY 1", "1"},
            {"UPDATE ro SET s = 'hit' FROM rt WHERE ro.id = rt.id", "1|0"},
            {"SELECT id, s FROM ro ORDER BY id", "1|hit ;; 2|b"},
        });
        engine.execute("UPDATE ro SET s = CASE id WHEN 1 THEN 'a' ELSE 'b' END");
        assertCells(new String[][] {
            {"DELETE FROM ro USING rt WHERE ro.id = rt.id", "1"},
            {"SELECT id, s FROM ro ORDER BY id", "2|b"},
            {"UPDATE rt SET s = 'upd'", "1|0"},
            {"DELETE FROM rt", "1"},
        });
    }
}
