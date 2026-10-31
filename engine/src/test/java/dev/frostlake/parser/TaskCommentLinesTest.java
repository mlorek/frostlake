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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The syntax-error line of a task whose query is followed by a COMMENT: the COMMENT is refused, after an aliased
 * FROM item as after any other clause (all live-verified).
 */
public class TaskCommentLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void commentAfterTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk7 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t x COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk7 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t AS x COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk7 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t x ORDER BY 1 COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk7 SCHEDULE = '60 MINUTE' AS SELECT a FROM t x GROUP BY a COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk7 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t x JOIN t y ON TRUE COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 79 unexpected 'COMMENT'.",
            "CREATE OR REPLACE TASK tk7 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t x LIMIT 1 COMMENT = 'c'");
    }
}
