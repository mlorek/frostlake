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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$SEND_EMAIL is a procedure of the account, written in JavaScript (live-verified over an integration that
 * is not there): four or five arguments that read as texts reach its body, which refuses the integration as
 * {@code Integration 'NOSUCH_INT' does not exist or not authorized.} in the body's frame, its name upper-cased
 * unless written in double quotes, and a NULL as an unsupported binding; another count, or an ARRAY, is refused
 * for the signature at line 0, position -1; and as a SELECT it is no function.
 */
public class SendEmailProcedureTest extends MeasuredCellsTestSupport {

    @Test
    public void itIsAProcedureOfTheAccount() {
        assertCells(new String[][] {
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'NOSUCH_INT' does not exist or not authorized. | At Snowflake.execute, line 3 "
                + "position 24"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b', 'text/html')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'NOSUCH_INT' does not exist or not authorized. | At Snowflake.execute, line 3 "
                + "position 24"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b', 'text/nope')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'NOSUCH_INT' does not exist or not authorized. | At Snowflake.execute, line 3 "
                + "position 24"},
            {"CALL SYSTEM$SEND_EMAIL(1, 2, 3, 4)",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration '1' does not exist or not authorized. | At Snowflake.execute, line 3 position 24"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$SEND_EMAIL': (VARCHAR(10), VARCHAR(5), VARCHAR(1))"},
            {"CALL SYSTEM$SEND_EMAIL()",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$SEND_EMAIL': ()"},
            {"SELECT SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b')",
                "REFUSED SQL compilation error: | Unknown function SYSTEM$SEND_EMAIL."},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b', 'text/html', 'x')",
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                + "[SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b', 'text/html', 'x')] expected 5, got 6"},
            {"CALL SYSTEM$SEND_EMAIL(NULL, 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | Unsupported type for binding "
                + "argument 2undefined | At Snowflake.execute, line 3 position 24"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', NULL, 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | Unsupported type for binding "
                + "argument 2undefined | At Snowflake.execute, line 3 position 24"},
            {"CALL system$send_email('NOSUCH_INT', 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'NOSUCH_INT' does not exist or not authorized. | At Snowflake.execute, line 3 "
                + "position 24"},
            {"CALL SYSTEM$SEND_EMAIL('\"lower_int\"', 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'lower_int' does not exist or not authorized. | At Snowflake.execute, line 3 position "
                + "24"},
            {"CALL SYSTEM$SEND_EMAIL(TRUE, 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'TRUE' does not exist or not authorized. | At Snowflake.execute, line 3 position 24"},
            {"CALL SYSTEM$SEND_EMAIL('a b', 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'A B' does not exist or not authorized. | At Snowflake.execute, line 3 position 24"},
            {"BEGIN CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b'); RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Execution error in "
                + "store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | Integration 'NOSUCH_INT' does not "
                + "exist or not authorized. | At Snowflake.execute, line 3 position 24"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 1)",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration 'NOSUCH_INT' does not exist or not authorized. | At Snowflake.execute, line 3 "
                + "position 24"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$SEND_EMAIL': (VARCHAR(10), VARCHAR(5))"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$SEND_EMAIL': (VARCHAR(10))"},
            {"CALL SYSTEM$SEND_EMAIL('nosuch_int', ARRAY_CONSTRUCT('a@b.c'), 's', 'b')",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$SEND_EMAIL': (VARCHAR(10), ARRAY, VARCHAR(1), VARCHAR(1))"},
            {"CALL SYSTEM$SEND_EMAIL('', 'a@b.c', 's', 'b')",
                "REFUSED Execution error in store procedure SYSTEM$SEND_EMAIL: | SQL compilation error: | "
                + "Integration '' does not exist or not authorized. | At Snowflake.execute, line 3 position 24"},
        });
    }
}
