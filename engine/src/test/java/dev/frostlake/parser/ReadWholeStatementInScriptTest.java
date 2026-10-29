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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A request of several statements whose only fault, to this grammar, is a statement the account reads whole — a stage
 * reference of the account's own spelling — runs the statements before it and then fails at it, as it compiles, the
 * request's helper naming it (live-verified).
 */
public class ReadWholeStatementInScriptTest extends MeasuredCellsTestSupport {

    private static final String FAILED = "REFUSED JavaScript execution error: Uncaught Execution of multiple "
        + "statements failed on statement ";

    private static final String HELPER = " in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements "
        + "failed on statement {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4 | "
        + "stackstrace:  | SYSTEM$MULTISTMT line: 10";

    @Test
    public void theStatementIsNamedWhereItStands() {
        // The live harness splits a script by this grammar's statements, and this text is none, so it would go over
        // as one statement - which the account counts as two.
        Assumptions.assumeFalse(isLiveSnowflake(), "the live harness sends text this grammar refuses as one statement");
        assertAnswer(FAILED + "\"DESCRIBE TABLE t1 (a) x = @s!=...\" (at line 1, position 0). | SQL compilation "
                + "error: | invalid parameter 'x'" + HELPER,
            "DESCRIBE TABLE t1 (a) x = @s!=x; SELECT 1");
        assertAnswer(FAILED + "\"DESCRIBE TABLE t1 (a) x = @s!=...\" (at line 1, position 10). | SQL compilation "
                + "error: | invalid parameter 'x'" + HELPER,
            "SELECT 1; DESCRIBE TABLE t1 (a) x = @s!=x");
    }

    /** The statement alone is refused as it compiles. */
    @Test
    public void theStatementAloneIsRefused() {
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'x'", "DESCRIBE TABLE t1 (a) x = @s!=x");
    }

    /** The statements before it run, each as a statement of its own: one that fails is named in its place. */
    @Test
    public void theStatementsBeforeItRun() {
        Assumptions.assumeFalse(isLiveSnowflake(), "what the statements before it leave is not measured");
        engine.execute("CREATE OR REPLACE TABLE w2579 (n INT)");
        assertAnswer(FAILED + "\"DESCRIBE TABLE t1 (a) x = @s!=...\" (at line 1, position 30). | SQL compilation "
                + "error: | invalid parameter 'x'" + HELPER,
            "INSERT INTO w2579 VALUES (1); DESCRIBE TABLE t1 (a) x = @s!=x; INSERT INTO w2579 VALUES (2)");
        assertAnswer("1", "SELECT n FROM w2579");
        assertAnswer(FAILED + "\"SELECT 1/0\" (at line 1, position 0). | Division by zero" + HELPER,
            "SELECT 1/0; DESCRIBE TABLE t1 (a) x = @s!=x");
    }
}
