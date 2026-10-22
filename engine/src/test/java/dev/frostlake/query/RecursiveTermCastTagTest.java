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
 * A cast out of range over a recursive CTE's own column inside its term: the refusal names the value as the widest
 * exact number, SB16, and prints it at the target's scale, where a cast in the anchor or over a joined table's
 * column names its source type; and the nullability a CREATE TABLE AS over the recursion reports for its columns
 * (all live-verified).
 */
public class RecursiveTermCastTagTest extends MeasuredCellsTestSupport {

    @Test
    public void recursiveCasts() {
        engine.execute("CREATE OR REPLACE TABLE wt2056 (i INT NOT NULL, j INT)");
        engine.execute("INSERT INTO wt2056 VALUES (1, 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](1,0){not null}, value 15",
            "WITH RECURSIVE r (n) AS (SELECT 5 UNION ALL SELECT (n * 3)::NUMBER(1,0) FROM r WHERE n < 9) SELECT n "
                + "FROM r");
        assertAnswer("1 ;; 5 ;; 9",
            "WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT (n + 4)::NUMBER(1,0) FROM r WHERE n < 9) SELECT n "
                + "FROM r");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](2,1){not null}, value 15.0",
            "WITH RECURSIVE r (n) AS (SELECT 5 UNION ALL SELECT (n * 3)::NUMBER(2,1) FROM r WHERE n < 9) SELECT n "
                + "FROM r");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB1](1,0){not null}, value 15",
            "WITH RECURSIVE r (n) AS (SELECT 15::NUMBER(1,0) UNION ALL SELECT n FROM r WHERE n < 9) SELECT n FROM r");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB1](1,0){not null}, value 100",
            "WITH RECURSIVE r (n) AS (SELECT 5 UNION ALL SELECT (w.i * 100)::NUMBER(1,0) FROM r JOIN wt2056 w ON "
                + "TRUE WHERE n < 9) SELECT n FROM r");
        assertAnswer("Table C2056 successfully created.",
            "CREATE OR REPLACE TABLE c2056 AS WITH RECURSIVE r (n, m) AS (SELECT i + 1, j FROM wt2056 UNION ALL "
                + "SELECT n + 1, m FROM r WHERE n < 3) SELECT n, m FROM r");
        assertAnswer("N|NUMBER(38,0)|Y ;; M|NUMBER(38,0)|Y",
            "DESCRIBE TABLE c2056 ->> SELECT \"name\", \"type\", \"null?\" FROM $1");
        assertAnswer("Table C2056 successfully created.",
            "CREATE OR REPLACE TABLE c2056 AS WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT w.i + n FROM r JOIN "
                + "wt2056 w ON TRUE WHERE n < 3) SELECT n FROM r");
        assertAnswer("N|NUMBER(38,0)|Y", "DESCRIBE TABLE c2056 ->> SELECT \"name\", \"type\", \"null?\" FROM $1");
        assertAnswer("Table C2056 successfully created.",
            "CREATE OR REPLACE TABLE c2056 AS WITH RECURSIVE r (n) AS (SELECT i FROM wt2056 UNION ALL SELECT n + 1 "
                + "FROM r WHERE n < 3) SELECT n FROM r");
        assertAnswer("N|NUMBER(38,0)|Y", "DESCRIBE TABLE c2056 ->> SELECT \"name\", \"type\", \"null?\" FROM $1");
    }
}
