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

package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Java handler's report cut at 4093 bytes keeps a character the cut splits as one U+FFFD; a failed statement's
 * message past 16384 bytes keeps its first 16384 characters, a character of two UTF-16 units counted once; and
 * the message a scripting block's handler reads in SQLERRM is cut as the client's is, the failure of a statement
 * it ran or of a procedure it called (all live-verified).
 */
public class HandlerMessageCutTest extends MeasuredCellsTestSupport {

    /** What ends a failed statement's message the account cut. */
    private static final String CUT = "... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).";

    /** A character of two UTF-16 units. */
    private static final String EMOJI = "\uD83D\uDE00";

    @Test
    public void aLongMessageInABlock() {
        engine.execute("CREATE OR REPLACE FUNCTION jl677(k INT) RETURNS INT LANGUAGE JAVA HANDLER = 'H.f' AS "
            + "$$\nclass H {\n  public static int f(int k) {\n    String unit = k == 1 ? \"x\" : "
            + "\"\\uD83D\\uDE00\";\n    StringBuilder b = new StringBuilder();\n    for (int i = 0; i < 20000; "
            + "i++) { b.append(unit); }\n    throw new RuntimeException(b.toString());\n  }\n}\n$$");
        assertAnswer("4131",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl677(1)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM); "
                + "END $$");
        assertAnswer("1111",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl677(2)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM); "
                + "END $$");
        assertAnswer("User Error Report:  | Java Stack Trace: | java.lang.RuntimeExcep",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl677(1)); EXCEPTION WHEN OTHER THEN RETURN LEFT(SQLERRM, "
                + "60); END $$");
        assertAnswer("xx... in function JL677 with handler H.f",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl677(1)); EXCEPTION WHEN OTHER THEN RETURN RIGHT(SQLERRM, "
                + "40); END $$");
        assertAnswer("andler H.f",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl677(2)); EXCEPTION WHEN OTHER THEN RETURN RIGHT(SQLERRM, "
                + "10); END $$");
        assertAnswer("4131",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN BEGIN SELECT jl677(1) INTO :r; EXCEPTION WHEN OTHER THEN "
                + "RETURN LENGTH(SQLERRM); END; END $$");
    }

    @Test
    public void theCutsCharacters() {
        engine.execute("CREATE OR REPLACE FUNCTION jl678(k INT) RETURNS INT LANGUAGE JAVA HANDLER = 'H.f' AS "
            + "$$\nclass H {\n  public static int f(int k) {\n    String unit = k == 1 ? \"\\u00E9\" : k == 2 ? "
            + "\"\\u20AC\" : \"\\uD83D\\uDE00\";\n    StringBuilder b = new StringBuilder(k == 4 ? \"a\" : k == "
            + "5 ? \"ab\" : \"\");\n    for (int i = 0; i < 20000; i++) { b.append(unit); }\n    throw new "
            + "RuntimeException(b.toString());\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pl678() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT "
            + "REPEAT('a', 20000)::INT); END $$");
        assertAnswer("2118:C3A9EFBFBD2E2E",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl678(1)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM) ||"
                + " ':' || HEX_ENCODE(SUBSTR(SQLERRM, LENGTH(SQLERRM) - 39, 4)); END $$");
        assertAnswer("1447:E282ACEFBFBD2E2E",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl678(2)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM) ||"
                + " ':' || HEX_ENCODE(SUBSTR(SQLERRM, LENGTH(SQLERRM) - 39, 4)); END $$");
        assertAnswer("1111:F09F9880EFBFBD2E2E",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl678(3)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM) ||"
                + " ':' || HEX_ENCODE(SUBSTR(SQLERRM, LENGTH(SQLERRM) - 39, 4)); END $$");
        assertAnswer("1112:F09F9880EFBFBD2E2E",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl678(4)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM) ||"
                + " ':' || HEX_ENCODE(SUBSTR(SQLERRM, LENGTH(SQLERRM) - 39, 4)); END $$");
        assertAnswer("1113:F09F9880EFBFBD2E2E",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT jl678(5)); EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM) ||"
                + " ':' || HEX_ENCODE(SUBSTR(SQLERRM, LENGTH(SQLERRM) - 39, 4)); END $$");
        assertAnswer("REFUSED Numeric value '" + EMOJI.repeat(9000) + "' is not recognized" + CUT + CUT,
            "SELECT REPEAT('\ud83d\ude00', 9000)::INT");
        assertAnswer("16460",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT REPEAT('a', 20000)::INT); EXCEPTION WHEN OTHER THEN RETURN "
                + "LENGTH(SQLERRM); END $$");
        assertAnswer("aaaaaaaaaaaaaa... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT REPEAT('a', 20000)::INT); EXCEPTION WHEN OTHER THEN RETURN "
                + "RIGHT(SQLERRM, 90); END $$");
        assertAnswer("16460",
            "EXECUTE IMMEDIATE $$ BEGIN CALL pl678(); RETURN 0; EXCEPTION WHEN OTHER THEN RETURN LENGTH(SQLERRM); "
                + "END $$");
        assertAnswer("REFUSED " + ("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "Numeric value '" + "a".repeat(20000) + "' is not recognized").substring(0, 16384) + CUT,
            "CALL pl678()");
        assertAnswer("9186",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT REPEAT('\ud83d\ude00', 9000)::INT); EXCEPTION WHEN OTHER THEN "
                + "RETURN LENGTH(SQLERRM); END $$");
        assertAnswer("16460",
            "EXECUTE IMMEDIATE $$ DECLARE r INT; BEGIN SELECT REPEAT('a', 20000)::INT INTO :r; RETURN r; EXCEPTION "
                + "WHEN OTHER THEN RETURN LENGTH(SQLERRM); END $$");
    }
}
