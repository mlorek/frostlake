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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REPEAT, SPACE and INSERT over a BINARY, judged as the plans the account rewrites them to before it
 * looks at the arguments — REPEAT(s, n) as {@code LPAD('', n * LENGTH(s), s)}, SPACE(n) as
 * {@code LPAD('', n, ' ')}, INSERT as {@code SUBSTR(s, 1, p - 1) || i || SUBSTR(s, p + l)} — each refused in
 * the rewrite's words, at the call, while the statement compiles; and COLLATE, which takes no BINARY at
 * all. Every cell is live-verified.
 */
public class BinaryRewriteRefusalTest extends BaseDatabaseTest {

    private static final String LPAD = "Invalid argument types for function 'LPAD': ";
    private static final String CONCAT = "Invalid argument types for function '||': ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE bt (i NUMBER(5,0), bn BINARY)");
        engine.execute("INSERT INTO bt SELECT 1, TO_BINARY('6162') UNION ALL SELECT 2, TO_BINARY('63')");
        engine.execute("CREATE OR REPLACE TABLE bt5 (b5 BINARY(5), v VARCHAR(3))");
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    private void assertStatementRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** REPEAT(s, n) is LPAD('', n * LENGTH(s), s), LENGTH declaring NUMBER(18,0): the product keeps its width. */
    @Test
    public void repeatIsJudgedAsTheLpadItIsPlannedAs() {
        assertRefused("SELECT REPEAT(bn, 2) FROM bt",
            "error line 1 at position 7\n" + LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(8388608))");
        assertRefused("SELECT i, REPEAT(bn, 2) FROM bt",
            "error line 1 at position 10\n" + LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, 10) FROM bt", LPAD + "(VARCHAR(1), NUMBER(20,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, 1000000) FROM bt", LPAD + "(VARCHAR(1), NUMBER(25,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, -1) FROM bt", LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, i) FROM bt", LPAD + "(VARCHAR(1), NUMBER(23,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, 2.5) FROM bt", LPAD + "(VARCHAR(1), NUMBER(20,1), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, NULL) FROM bt", LPAD + "(VARCHAR(1), NUMBER(36,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, '2') FROM bt", LPAD + "(VARCHAR(1), NUMBER(36,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, 2::FLOAT) FROM bt", LPAD + "(VARCHAR(1), FLOAT, BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, PARSE_JSON('2')) FROM bt", LPAD + "(VARCHAR(1), FLOAT, BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, 2::NUMBER(38,0)) FROM bt",
            LPAD + "(VARCHAR(1), NUMBER(38,0), BINARY(8388608))");
        assertRefused("SELECT REPEAT(b5, 2) FROM bt5", LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(5))");
        assertRefused("SELECT REPEAT(X'6162', 2)", LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(2))");
        assertRefused("SELECT REPEAT(TO_BINARY('6162'), 2)", LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(67108864))");
        assertRefused("SELECT REPEAT(x, 2) FROM (SELECT bn AS x FROM bt)",
            LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(8388608))");
    }

    /** The product is typed first, so a count the multiplication refuses is refused in its words. */
    @Test
    public void repeatRefusesItsProductFirst() {
        final String product = "error line 1 at position 7\nInvalid argument types for function '*': ";
        assertRefused("SELECT REPEAT('ab', bn) FROM bt", product + "(BINARY(8388608), NUMBER(18,0))");
        assertRefused("SELECT REPEAT(bn, bn) FROM bt", product + "(BINARY(8388608), NUMBER(18,0))");
        assertRefused("SELECT REPEAT(bn, TRUE) FROM bt", product + "(BOOLEAN, NUMBER(18,0))");
        assertRefused("SELECT REPEAT(bn, CURRENT_DATE()) FROM bt", product + "(DATE, NUMBER(18,0))");
    }

    /** SPACE(n) is LPAD('', n, ' '): a BINARY count sits between the two one-character strings. */
    @Test
    public void spaceIsJudgedAsTheLpadItIsPlannedAs() {
        assertRefused("SELECT SPACE(bn) FROM bt",
            "error line 1 at position 7\n" + LPAD + "(VARCHAR(1), BINARY(8388608), VARCHAR(1))");
        assertRefused("SELECT  SPACE(bn) FROM bt",
            "error line 1 at position 8\n" + LPAD + "(VARCHAR(1), BINARY(8388608), VARCHAR(1))");
        assertRefused("SELECT SPACE(b5) FROM bt5", LPAD + "(VARCHAR(1), BINARY(5), VARCHAR(1))");
        assertRefused("SELECT SPACE(TO_BINARY('02'))", LPAD + "(VARCHAR(1), BINARY(67108864), VARCHAR(1))");
    }

    /** INSERT is a concatenation around the base's two SUBSTRs: a BINARY beside any other family is refused. */
    @Test
    public void insertIsJudgedAsTheConcatenationItIsPlannedAs() {
        assertRefused("SELECT INSERT(bn, 1, 1, 'x') FROM bt",
            "error line 1 at position 7\n" + CONCAT + "(BINARY(8388608), VARCHAR(1), BINARY(8388608))");
        assertRefused("SELECT  INSERT(bn, 1, 1, 'x') FROM bt",
            "error line 1 at position 8\n" + CONCAT + "(BINARY(8388608), VARCHAR(1), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 1, 1, 'xy') FROM bt", CONCAT + "(BINARY(8388608), VARCHAR(2), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 2, 3, 'x') FROM bt", CONCAT + "(BINARY(8388608), VARCHAR(1), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, i, 1, 'x') FROM bt", CONCAT + "(BINARY(8388608), VARCHAR(1), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 1, 1, v) FROM bt, bt5", CONCAT + "(BINARY(8388608), VARCHAR(3), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 1, 1, 5) FROM bt", CONCAT + "(BINARY(8388608), NUMBER(1,0), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 1, 1, TRUE) FROM bt", CONCAT + "(BINARY(8388608), BOOLEAN, BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 1, 1, PARSE_JSON('\"x\"')) FROM bt",
            CONCAT + "(BINARY(8388608), VARIANT, BINARY(8388608))");
        assertRefused("SELECT INSERT('abc', 1, 1, bn) FROM bt", CONCAT + "(VARCHAR(3), BINARY(8388608), VARCHAR(3))");
        assertRefused("SELECT INSERT(v, 1, 1, b5) FROM bt5", CONCAT + "(VARCHAR(3), BINARY(5), VARCHAR(3))");
        assertRefused("SELECT INSERT(NULL, 1, 1, bn) FROM bt",
            CONCAT + "(VARCHAR(134217728), BINARY(8388608), VARCHAR(134217728))");
        assertRefused("SELECT INSERT(b5, 1, 1, 'x') FROM bt5", CONCAT + "(BINARY(5), VARCHAR(1), BINARY(5))");
        assertRefused("SELECT INSERT(TO_BINARY('6162'), 1, 1, 'x')",
            CONCAT + "(BINARY(67108864), VARCHAR(1), BINARY(67108864))");
    }

    /** The arithmetic inside the SUBSTRs is typed before the concatenation: p - 1 first, then p + l. */
    @Test
    public void insertRefusesItsArithmeticFirst() {
        final String minus = "error line 1 at position 7\nInvalid argument types for function '-': ";
        final String plus = "error line 1 at position 7\nInvalid argument types for function '+': ";
        assertRefused("SELECT INSERT('abc', bn, 1, 'x') FROM bt", minus + "(BINARY(8388608), NUMBER(1,0))");
        assertRefused("SELECT INSERT(bn, bn, 1, 'x') FROM bt", minus + "(BINARY(8388608), NUMBER(1,0))");
        assertRefused("SELECT INSERT('abc', bn, bn, 'x') FROM bt", minus + "(BINARY(8388608), NUMBER(1,0))");
        assertRefused("SELECT INSERT('abc', 1, bn, 'x') FROM bt", plus + "(NUMBER(1,0), BINARY(8388608))");
        assertRefused("SELECT INSERT(bn, 1, bn, bn) FROM bt", plus + "(NUMBER(1,0), BINARY(8388608))");
    }

    /** Every rewrite is refused while the statement compiles: over no rows, and inside a view at its offset. */
    @Test
    public void theRewritesAreRefusedWhileTheStatementCompiles() {
        assertRefused("SELECT INSERT(bn, 1, 1, 'x') FROM bt WHERE FALSE",
            "error line 1 at position 7\n" + CONCAT + "(BINARY(8388608), VARCHAR(1), BINARY(8388608))");
        assertRefused("SELECT REPEAT(bn, 2) FROM bt WHERE FALSE",
            "error line 1 at position 7\n" + LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(8388608))");
        assertRefused("SELECT SPACE(bn) FROM bt WHERE FALSE",
            "error line 1 at position 7\n" + LPAD + "(VARCHAR(1), BINARY(8388608), VARCHAR(1))");
        assertStatementRefused("CREATE OR REPLACE VIEW rv AS SELECT REPEAT(bn, 2) AS r FROM bt",
            "error line 1 at position 36\n" + LPAD + "(VARCHAR(1), NUMBER(19,0), BINARY(8388608))");
    }

    /** COLLATE takes no BINARY in either spelling, the operand named from the plan. */
    @Test
    public void collateTakesNoBinary() {
        assertRefused("SELECT COLLATE(bn, 'en') FROM bt", "SQL compilation error:\nargument needs to be a string: 'BT.BN'");
        assertRefused("SELECT bn COLLATE 'en' FROM bt", "SQL compilation error:\nargument needs to be a string: 'BT.BN'");
        assertRefused("SELECT COLLATE(b5, 'en-ci') FROM bt5",
            "SQL compilation error:\nargument needs to be a string: 'BT5.B5'");
        assertRefused("SELECT COLLATE(TO_BINARY('61'), 'en')",
            "SQL compilation error:\nargument needs to be a string: 'TO_BINARY('61')'");
        assertRefused("SELECT COLLATE(x, 'en') FROM (SELECT bn AS x FROM bt)",
            "SQL compilation error:\nargument needs to be a string: '\"values\".X'");
    }
}
