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

package dev.frostlake.scripting;

import dev.frostlake.LiveSnowflake;
import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * An ASYNC job written without its brackets is refused at its statement's first word and then at the semicolon
 * where the closing bracket is wanted; one with a semicolon inside its brackets at that semicolon and then at the
 * bracket after the next statement; and a FOR over a RESULTSET whose job was never awaited is placed nowhere
 * (live-verified). The account is not asked again: the live suite starts no ASYNC job.
 */
public class AsyncJobSyntaxLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void jobsWithoutTheirBrackets() {
        assumeFalse(LiveSnowflake.enabled(), "the live suite starts no ASYNC job");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 21 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC SELECT 1; AWAIT ALL; RETURN 'no parentheses'; END; $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ';'. | syntax "
                + "error line 1 at position 32 unexpected ')'.",
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1; SELECT 2); AWAIT ALL; RETURN 'two'; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 0 at position -1 : Stored "
                + "procedure execution error: uninitialized resultSet 'R'",
            "EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := ASYNC (SELECT 1 AS a); FOR x IN r DO RETURN x.a; END FOR; "
                + "RETURN 'looped'; END; $$");
    }
}
