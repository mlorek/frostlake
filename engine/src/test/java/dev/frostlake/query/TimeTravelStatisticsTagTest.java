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
 * A read at an AT or BEFORE point is tagged by that version's statistics: a COUNT of 300 earlier rows is
 * {@code [SB2]}, as the same rows copied into a table are, even after the table has shrunk to 5 (live-verified).
 */
public class TimeTravelStatisticsTagTest extends BaseDatabaseTest {

    @Test
    public void anEarlierVersionIsTaggedByItsOwnRows() {
        engine.execute("CREATE TABLE tt AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 300))");
        engine.execute("SELECT COUNT(*) FROM tt");
        engine.execute("SET q = LAST_QUERY_ID()");
        engine.execute("DELETE FROM tt WHERE i >= 5");
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*), SYSTEM$TYPEOF(COUNT(*)), SYSTEM$TYPEOF(MAX(i)), SYSTEM$TYPEOF(MIN(i))"
                + " FROM tt AT(STATEMENT => $q)", "300, NUMBER(18,0)[SB2], NUMBER(10,0)[SB2], NUMBER(10,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(i) FROM tt AT(STATEMENT => $q) WHERE i = 299", "NUMBER(10,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(i + 1) FROM tt AT(STATEMENT => $q) WHERE i = 299", "NUMBER(11,0)[SB2]"},
            {"SELECT COUNT(*), SYSTEM$TYPEOF(COUNT(*)) FROM tt BEFORE(STATEMENT => $q)", "300, NUMBER(18,0)[SB2]"},
            {"SELECT COUNT(*), SYSTEM$TYPEOF(COUNT(*)), SYSTEM$TYPEOF(MAX(i)) FROM tt",
                "5, NUMBER(18,0)[SB1], NUMBER(10,0)[SB1]"},
        });
        engine.execute("UNSET q");
    }
}
