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
 * A query a block runs holds date arithmetic over a variable holding NULL as the bare word NULL: a NULL DATE plus or
 * minus a number, a date or an INTERVAL, a number plus it, and a DATE beside a NULL number, the variable in brackets
 * of its own too; the NULL then reads as the word does, a NUMBER(19,0) under a further + 1 and a DATE in a COALESCE
 * with one. A time or a timestamp plus a number stays refused (all live-verified).
 */
public class NullDateBindArithmeticTest extends MeasuredCellsTestSupport {

    @Test
    public void nullDateVariablesInArithmetic() {
        engine.execute("CREATE OR REPLACE TABLE dt (c VARCHAR)");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d - 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(1 + :d) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; n NUMBER; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + :n) AS c1); RETURN "
                + "TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; n NUMBER DEFAULT 1; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + :n) AS c1); "
                + "RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE DEFAULT '2024-01-01'; n NUMBER; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + :n) "
                + "AS c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d - :d) AS c1); RETURN TABLE(r); END;");
        assertAnswer("DATE[SB4]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + 1.5) AS c1); RETURN TABLE(r); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                + "error: error line 1 at position 24 | Invalid argument types for function '+': (TIMESTAMP_NTZ(9), "
                + "NUMBER(1,0))",
            "DECLARE t TIMESTAMP_NTZ; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:t + 1) AS c1); RETURN "
                + "TABLE(r); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 81 : SQL compilation "
                + "error: error line 1 at position 24 | Invalid argument types for function '+': (TIMESTAMP_NTZ(9), "
                + "NUMBER(1,0))",
            "DECLARE t TIMESTAMP_NTZ DEFAULT '2024-01-01 10:00:00'; BEGIN LET r RESULTSET := (SELECT "
                + "SYSTEM$TYPEOF(:t + 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 25 | Invalid argument types for function '+': (TIME(9), NUMBER(1,"
                + "0))",
            "DECLARE tm TIME; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:tm + 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 62 : SQL compilation "
                + "error: error line 1 at position 25 | Invalid argument types for function '+': (TIME(9), NUMBER(1,"
                + "0))",
            "DECLARE tm TIME DEFAULT '10:00:00'; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:tm + 1) AS c1); "
                + "RETURN TABLE(r); END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                + "error: error line 1 at position 24 | Invalid argument types for function '-': (TIMESTAMP_LTZ(9), "
                + "NUMBER(1,0))",
            "DECLARE t TIMESTAMP_LTZ; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:t - 1) AS c1); RETURN "
                + "TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(DATEADD(day, 1, :d)) AS c1); RETURN "
                + "TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + INTERVAL '1 day') AS c1); RETURN "
                + "TABLE(r); END;");
    }

    @Test
    public void theFoldedNullReadsAsTheWord() {
        assertAnswer("NUMBER(19,0)[SB1]", "SELECT SYSTEM$TYPEOF(DATEADD(day, 0, NULL) + 1)");
        assertAnswer("NUMBER(19,0)[SB1]", "SELECT SYSTEM$TYPEOF(DATEADD(day, 0, NULL) - 1)");
        assertAnswer("NUMBER(19,0)[SB1]", "SELECT SYSTEM$TYPEOF(1 + DATEADD(day, 0, NULL))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | Invalid argument types for "
                + "function '+': (NULL, INTERVAL)",
            "SELECT SYSTEM$TYPEOF(DATEADD(day, 0, NULL) + INTERVAL '1 day')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | Invalid argument types for "
                + "function '-': (NULL, DATE)",
            "SELECT SYSTEM$TYPEOF(DATEADD(day, 0, NULL) - DATE '2024-01-01')");
        assertAnswer("NUMBER(19,1)[SB1]", "SELECT SYSTEM$TYPEOF(DATEADD(day, 0, NULL) + 1.5)");
        assertAnswer("NULL[LOB]", "SELECT SYSTEM$TYPEOF(DATEADD(day, 0, NULL))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | Invalid argument types for "
                + "function '+': (NULL, INTERVAL)",
            "SELECT SYSTEM$TYPEOF(NULL + INTERVAL '1 day')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function '+': (DATE, NULL)",
            "SELECT SYSTEM$TYPEOF(DATE '2024-01-01' + NULL)");
        assertAnswer("DATE[SB4]", "SELECT SYSTEM$TYPEOF(TO_DATE(NULL) + 1)");
        assertAnswer("NUMBER(38,0)[SB1]",
            "DECLARE n NUMBER; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:n + 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NUMBER(11,2)[SB1]",
            "DECLARE n NUMBER(10,2); BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:n * 2) AS c1); RETURN TABLE(r); "
                + "END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 24 | Invalid argument types for function '+': (DATE, DATE)",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + :d) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE DEFAULT '2024-01-01'; e DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d - :e) AS "
                + "c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF((:d) + 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("NUMBER(19,0)[SB1]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + 1 + 1) AS c1); RETURN TABLE(r); END;");
        assertAnswer("DATE[SB4]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(COALESCE(:d + 1, CURRENT_DATE())) AS "
                + "c1); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(DATEDIFF(day, :d, CURRENT_DATE())) AS "
                + "c1); RETURN TABLE(r); END;");
        assertAnswer("null|true",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT :d + 1 AS c1, (:d + 1) IS NULL AS c2); RETURN "
                + "TABLE(r); END;");
    }
}
