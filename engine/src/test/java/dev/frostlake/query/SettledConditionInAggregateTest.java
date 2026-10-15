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
 * A condition the statistics settle is folded inside an aggregate's argument of a subquery read as a value: the
 * outer name in it correlates nothing, and the subquery is answered (live-verified).
 */
public class SettledConditionInAggregateTest extends MeasuredCellsTestSupport {

    @Test
    public void aSettledConditionInAnAggregatesArgument() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        answer(
            "INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        answer(
            "CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO one VALUES (5, TRUE)");
        assertAnswer(
            "5|B ;; 7|B",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|x ;; 7|x",
            "SELECT id, (SELECT MAX(IFF(tt.id < fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT MAX(IFF(tt.id + 4 = fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|0 ;; 7|0",
            "SELECT id, (SELECT SUM(IFF(tt.id = fz.id, 1, 0)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|0 ;; 7|0",
            "SELECT id, (SELECT COUNT_IF(tt.id = fz.id) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|B ;; 7|B",
            "SELECT id, (SELECT MAX(CASE WHEN tt.id = fz.id THEN 'x' ELSE value END) FROM tt) AS x FROM fz ORDER "
                + "BY id");
        assertAnswer(
            "5|A ;; 7|A",
            "SELECT id, (SELECT MIN(IFF(tt.id > fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|A,B ;; 7|A,B",
            "SELECT id, (SELECT LISTAGG(IFF(tt.id = fz.id, 'x', value), ',') WITHIN GROUP (ORDER BY tt.id) FROM "
                + "tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|B ;; 7|B",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id, 'x', value)) FROM tt WHERE tt.id > 0) AS x FROM fz ORDER "
                + "BY id");
        assertAnswer(
            "5|2 ;; 7|2",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id, fz.id, tt.id)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|B ;; 7|B",
            "SELECT id, (SELECT MAX(IFF(tt.id <> fz.id, value, 'x')) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|null ;; 7|null",
            "SELECT id, (SELECT MAX(value) FROM tt WHERE tt.id = fz.id) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|0 ;; 7|0",
            "SELECT id, (SELECT COUNT(*) FROM tt WHERE IFF(tt.id = fz.id, TRUE, FALSE)) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|x ;; 7|x",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id OR tt.id = 1, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
    }

    @Test
    public void otherPlacesAndShapes() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        answer(
            "INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        assertAnswer(
            "",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id, 'x', value)) FROM tt) AS x FROM fz WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT MAX(IFF(b, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|x ;; 7|x",
            "SELECT id, (SELECT MAX(IFF(fz.id > 0, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT MAX(IFF(fz.id > 6, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|2 ;; 7|2",
            "SELECT id, (SELECT MAX(tt.id + IFF(tt.id = fz.id, 10, 0)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|BA ;; 7|BA",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id, 'x', value)) || MIN(value) FROM tt) AS x FROM fz ORDER BY "
                + "id");
        assertAnswer(
            "5|2 ;; 7|2",
            "SELECT id, (SELECT COUNT(DISTINCT IFF(tt.id = fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        answer(
            "SELECT id, (SELECT MAX(IFF(tt.id IN (fz.id, 1), 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
        answer(
            "SELECT id, (SELECT MAX(COALESCE(tt.value, fz.id::VARCHAR)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "5|B ;; 7|B",
            "SELECT id, (SELECT MAX(NVL2(tt.id, value, fz.id::VARCHAR)) FROM tt) AS x FROM fz ORDER BY id");
        answer(
            "SELECT id, (SELECT MAX(value) FILTER (WHERE tt.id = fz.id) FROM tt) AS x FROM fz ORDER BY id");
        answer(
            "SELECT id FROM fz WHERE id IN (SELECT MAX(IFF(tt.id = fz.id, 5, 7)) FROM tt) ORDER BY id");
        assertAnswer(
            "7",
            "SELECT id FROM fz WHERE (SELECT MAX(IFF(tt.id = fz.id, 5, 7)) FROM tt) = id ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function [MAX(IFF(TT.ID = FZ.ID, 'x', TT.VALUE)) OVER ()] "
                + "contains a correlation.",
            "SELECT id, (SELECT MAX(IFF(tt.id = fz.id, 'x', value)) OVER () FROM tt LIMIT 1) AS x FROM fz ORDER "
                + "BY id");
        assertAnswer(
            "5|3 ;; 7|3",
            "SELECT id, (SELECT SUM(IFF(tt.id < fz.id, tt.id, 0)) FROM tt) AS x FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 12",
            "SELECT id, (SELECT MAX(IFF(tt.id + 4 < fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id");
    }
}
