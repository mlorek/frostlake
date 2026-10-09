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
 * A fault of a statement inside a loop's body, and the line after it. The LOOP of a WHILE … LOOP or a
 * FOR … LOOP is that construct's word, closed by the one END LOOP, so the second line is the token
 * after the loop — the block's END, a RETURN — as it is for WHILE … DO and a plain LOOP; after two
 * nested loops it is the block's own END. Every answer is live-verified.
 */
public class LoopBodyRecoveryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theLineAfterTheLoop() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                + "error line 1 at position 48 unexpected 'END'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 x y; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                + "error line 1 at position 48 unexpected 'RETURN'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 x y; END LOOP; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax "
                + "error line 1 at position 55 unexpected 'END'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 INTO t,, u; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax "
                + "error line 1 at position 43 unexpected 'VALUES'. | syntax error line 1 at position 65 "
                + "unexpected 'END'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 INTO t, u VALUES (1); END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'. | syntax "
                + "error line 1 at position 47 unexpected 'END'.",
            "BEGIN WHILE (TRUE) DO SELECT 1 x y; END WHILE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'y'. | syntax "
                + "error line 1 at position 35 unexpected 'END'.",
            "BEGIN LOOP SELECT 1 x y; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                + "error line 1 at position 43 unexpected 'FOR'.",
            "BEGIN FOR i IN 1 TO 2 DO SELECT 1 x y; END FOR; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'. | syntax "
                + "error line 1 at position 51 unexpected 'END'.",
            "BEGIN FOR i IN 1 TO 2 LOOP SELECT 1 x y; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax "
                + "error line 1 at position 54 unexpected 'END'.",
            "BEGIN WHILE (TRUE) DO SELECT 1 INTO t,, u; END WHILE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax "
                + "error line 1 at position 41 unexpected 'VALUES'. | syntax error line 1 at position 64 "
                + "unexpected 'END'.",
            "BEGIN WHILE (TRUE) DO SELECT 1 INTO t, u VALUES (1); END WHILE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected 'END'.",
            "BEGIN LOOP SELECT 1 INTO t,, u; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'y'. | syntax "
                + "error line 1 at position 76 unexpected 'END'.",
            "BEGIN WHILE (TRUE) LOOP WHILE (TRUE) LOOP SELECT 1 x y; END LOOP; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                + "error line 1 at position 48 unexpected 'END'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 x y; END LOOP; END; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                + "error line 1 at position 55 unexpected 'END'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 x y; BREAK; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 17 unexpected 'LOOP'. | syntax error line 1 at position 33 "
                + "unexpected 'y'. | syntax error line 1 at position 46 unexpected 'END'.",
            "BEGIN WHILE TRUE LOOP SELECT 1 x y; END LOOP; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                + "error line 1 at position 47 unexpected 'lbl'.",
            "BEGIN WHILE (TRUE) LOOP SELECT 1 x y; END LOOP lbl; END");
    }
}
