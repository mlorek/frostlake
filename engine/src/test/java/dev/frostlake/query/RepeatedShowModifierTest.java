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
 * A listing's modifier written again, or after one it has to stand before, is refused at its first
 * repetition however many follow it: SHOW TABLES LIMIT 1 LIMIT 2 LIMIT 3 is refused at the second LIMIT, and in
 * a block at that LIMIT and its count (live-verified).
 */
public class RepeatedShowModifierTest extends MeasuredCellsTestSupport {

    @Test
    public void aModifierWrittenThreeTimes() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 LIMIT 3");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'STARTS'.",
            "SHOW TABLES STARTS WITH 'a' STARTS WITH 'b' STARTS WITH 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | syntax "
                + "error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2 LIMIT 3 LIMIT 4; END;");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'STARTS'.",
            "SHOW TABLES LIMIT 1 STARTS WITH 'a' LIMIT 2");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 LIMIT");
    }

    @Test
    public void inABlock() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | syntax "
                + "error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2 LIMIT 3; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | syntax "
                + "error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2; END;");
    }
}
