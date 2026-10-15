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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A correlated subquery in a select list beside a window reads the outer row through the alias the FROM clause gives
 * its one relation, as it does without the window and over a join (live-verified).
 */
public class CorrelatedSubqueryBesideWindowTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rr (o INT, v INT)");
        engine.execute("INSERT INTO rr VALUES (1, 1), (2, 2), (3, 4)");
    }

    @Test
    public void theAliasReachesTheSubquery() {
        assertCells(engine, new String[][] {
            {"SELECT r1.o, SUM(r1.v) OVER () s, (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o) c FROM rr r1"
                + " ORDER BY 1", "1, 7, 1 | 2, 7, 2 | 3, 7, 3"},
            {"SELECT o, ROW_NUMBER() OVER (ORDER BY o) rn, EXISTS (SELECT 1 FROM rr r2 WHERE r2.o > r1.o) has_next"
                + " FROM rr r1 ORDER BY 1", "1, 1, true | 2, 2, true | 3, 3, false"},
            {"SELECT r1.o, SUM(r1.v) OVER (ORDER BY r1.o) s, (SELECT MAX(r2.v) FROM rr r2 WHERE r2.o < r1.o) c"
                + " FROM rr r1 ORDER BY 1", "1, 1, null | 2, 3, 1 | 3, 7, 2"},
            {"SELECT r1.o, (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o) c, LAG(r1.v) OVER (ORDER BY r1.o) l"
                + " FROM rr r1 ORDER BY 1", "1, 1, null | 2, 2, 1 | 3, 3, 2"},
            {"SELECT r1.o, ROW_NUMBER() OVER (ORDER BY r1.o) rn, (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= r1.o) c"
                + " FROM rr AS r1 ORDER BY 1", "1, 1, 1 | 2, 2, 2 | 3, 3, 3"},
            {"SELECT r1.o, COUNT(*) OVER () n, r1.v IN (SELECT r2.v FROM rr r2 WHERE r2.o < r1.o) i FROM rr r1"
                + " ORDER BY 1", "1, 3, false | 2, 3, false | 3, 3, false"},
            {"SELECT o, SUM(v) OVER () s, (SELECT COUNT(*) FROM rr r2 WHERE r2.o <= rr.o) c FROM rr ORDER BY 1",
                "1, 7, 1 | 2, 7, 2 | 3, 7, 3"},
        });
    }
}
