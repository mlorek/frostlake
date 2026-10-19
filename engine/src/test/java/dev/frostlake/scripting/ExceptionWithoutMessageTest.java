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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An exception declared without a message. {@code e1 EXCEPTION;} has the number -20000 and no message at all:
 * to a handler SQLERRM is NULL - not the empty string - so a text built from it is NULL, and the uncaught
 * sentence ends at the place, {@code Uncaught exception of type 'E1' on line 1 at position 29}. Declared with
 * the empty string as its message, SQLERRM is that string and the uncaught sentence keeps its {@code  : }
 * tail. A number without a message is a syntax error. Let out of a called procedure, the exception is the
 * CALL's failure and SQLERRM the procedure's uncaught sentence. Every answer is live-verified.
 */
public class ExceptionWithoutMessageTest extends MeasuredCellsTestSupport {

    @Test
    public void sqlerrmAndTheUncaughtSentence() {
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION; BEGIN RAISE e1; EXCEPTION WHEN e1 THEN RETURN SQLCODE || "
                + "'|' || SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION; BEGIN RAISE e1; EXCEPTION WHEN OTHER THEN RETURN SQLCODE "
                + "|| '|' || SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E1' on line 1 at position 29",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION; BEGIN RAISE e1; END; $$");
        assertAnswer(
            "-20005||P0001|0",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20005, ''); BEGIN RAISE e1; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || '|' || SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E1' on line 1 at position 42 : ",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20005, ''); BEGIN RAISE e1; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'.",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20005); BEGIN RAISE e1; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || '|' || SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "-20005|five|P0001|4",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION (-20005, 'five'); BEGIN RAISE e1; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || '|' || SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "Function XP successfully created.",
            "CREATE OR REPLACE PROCEDURE xp() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE e1 EXCEPTION; BEGIN "
                + "RAISE e1; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E1' on line 1 at position 29",
            "CALL xp()");
        assertAnswer(
            "-20000|Uncaught exception of type 'E1' on line 1 at position 29|P0001|56",
            "EXECUTE IMMEDIATE $$ BEGIN CALL xp(); RETURN 'no'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' "
                + "|| SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION; BEGIN BEGIN RAISE e1; EXCEPTION WHEN e1 THEN RAISE; END; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' || SQLERRM || '|' || SQLSTATE || '|' || "
                + "LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "null/is null/not empty",
            "EXECUTE IMMEDIATE $$ DECLARE e1 EXCEPTION; r VARCHAR; BEGIN BEGIN RAISE e1; EXCEPTION WHEN OTHER "
                + "THEN r := NVL(SQLERRM, 'null') || '/' || IFF(SQLERRM IS NULL, 'is null', 'not null') || '/' || "
                + "IFF(SQLERRM = '', 'empty', 'not empty'); END; RETURN r; END; $$");
    }
}
