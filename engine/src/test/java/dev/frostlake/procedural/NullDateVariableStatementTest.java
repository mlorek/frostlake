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
 * Date arithmetic over a block's DATE variable holding NULL is the bare word NULL in the statements the block runs
 * itself - SELECT ... INTO, INSERT, UPDATE - as in a RESULTSET's query: :d + 1 is NULL[LOB], :d + 1 + 1 a
 * NUMBER(19,0) and COALESCE(:d + 1, CURRENT_DATE()) a DATE, while a scripting expression keeps the DATE
 * (all live-verified).
 */
public class NullDateVariableStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void blockStatements() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table DT52 successfully created.", "CREATE OR REPLACE TABLE dt52 (c VARCHAR)");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1) INTO :x; RETURN x; END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d - 1) INTO :x; RETURN x; END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(1 + :d) INTO :x; RETURN x; END;");
        assertAnswer("NUMBER(19,0)[SB1]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1 + 1) INTO :x; RETURN x; END;");
        assertAnswer("DATE[SB4]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(COALESCE(:d + 1, CURRENT_DATE())) INTO :x; RETURN "
                + "x; END;");
        assertAnswer("DATE[SB4]",
            "DECLARE d DATE DEFAULT '2024-01-01'; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1) INTO :x; RETURN x; "
                + "END;");
        assertAnswer("DATE[SB4]", "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d) INTO :x; RETURN x; END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN DELETE FROM dt52; INSERT INTO dt52 SELECT SYSTEM$TYPEOF(:d + 1); LET r RESULTSET "
                + ":= (SELECT c FROM dt52); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN DELETE FROM dt52; INSERT INTO dt52 (c) VALUES (SYSTEM$TYPEOF(:d - 1)); LET r "
                + "RESULTSET := (SELECT c FROM dt52); RETURN TABLE(r); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; BEGIN DELETE FROM dt52; INSERT INTO dt52 VALUES ('x'); UPDATE dt52 SET c = "
                + "SYSTEM$TYPEOF(:d + 1); LET r RESULTSET := (SELECT c FROM dt52); RETURN TABLE(r); END;");
        assertAnswer("DATE[SB4]", "DECLARE d DATE; x VARCHAR; BEGIN x := SYSTEM$TYPEOF(d + 1); RETURN x; END;");
        assertAnswer("DATE[SB4]", "DECLARE d DATE; BEGIN RETURN SYSTEM$TYPEOF(d + 1); END;");
        assertAnswer("NULL[LOB]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(DATEADD(day, 1, :d)) INTO :x; RETURN x; END;");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void operationsAroundTheFold() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("NUMBER(19,0)[SB1]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1 - 1) INTO :x; RETURN x; END;");
        assertAnswer("DATE[SB4]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(NVL(:d + 1, CURRENT_DATE())) INTO :x; RETURN x; "
                + "END;");
        assertAnswer("DATE[SB4]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN :d + 1 ELSE CURRENT_DATE() "
                + "END) INTO :x; RETURN x; END;");
        assertAnswer("NUMBER(1,0)[SB1]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(IFF(:d + 1 IS NULL, 1, 2)) INTO :x; RETURN x; END;");
        assertAnswer("NUMBER(19,0)[SB1]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF((:d + 1) * 2) INTO :x; RETURN x; END;");
        assertAnswer("VARCHAR(134217728)[LOB]",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1 || 'x') INTO :x; RETURN x; END;");
        assertAnswer("NUMBER(38,0)[SB1]",
            "DECLARE d DATE; n NUMBER; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1 + :n) INTO :x; RETURN x; END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : SQL compilation "
                + "error: error line 1 at position 28 | Invalid argument types for function '+': (NULL, DATE)",
            "DECLARE d DATE; x VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:d + 1 + '2024-01-01'::DATE) INTO :x; RETURN x; "
                + "END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 28 | Invalid argument types for function '+': (NULL, DATE)",
            "DECLARE d DATE; BEGIN LET r RESULTSET := (SELECT SYSTEM$TYPEOF(:d + 1 + '2024-01-01'::DATE) AS c1); "
                + "RETURN TABLE(r); END;");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
