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
 * A SELECT ... INTO hands a block's DATE, TIME or TIMESTAMP_NTZ variable its value as text, read back under the
 * session's input format: under DATE_INPUT_FORMAT = 'DD/MM/YYYY' the fetched date is refused as Date '2024-02-01'
 * is not recognized at the SELECT, where a SQL function's parameter keeps its value whatever the formats say
 * (all live-verified).
 */
public class BlockTemporalInputFormatTest extends MeasuredCellsTestSupport {

    @Test
    public void aFetchedTemporalTravelsAsTextIntoABlockVariable() {
        engine.execute("CREATE OR REPLACE TABLE d1 (d DATE, t TIME(9), ts TIMESTAMP_NTZ(9))");
        engine.execute("INSERT INTO d1 VALUES ('2024-02-01', '15:30:00.123456789', '2024-02-01 15:30:00.123456789')");
        engine.execute("CREATE OR REPLACE FUNCTION fm(d DATE) RETURNS NUMBER AS 'MONTH(d)'");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Date "
                + "'2024-02-01' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE v DATE; BEGIN SELECT d INTO :v FROM d1; RETURN MONTH(v); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Date "
                + "'2024-02-01' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE v DATE; BEGIN SELECT d INTO :v FROM d1; RETURN v; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : Date "
                + "'2024-02-01' is not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN LET v DATE := TO_DATE('01/02/2024'); RETURN MONTH(v); END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Date "
                + "'2024-02-01' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE v DATE; BEGIN SELECT d INTO :v FROM d1; LET r RESULTSET := (SELECT "
                + "MONTH(:v) AS m); LET c CURSOR FOR r; FOR x IN c DO RETURN x.m; END FOR; END; $$");
        assertAnswer("2", "SELECT fm(d) FROM d1");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET DATE_INPUT_FORMAT");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET TIME_INPUT_FORMAT = 'HH12:MI AM'");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Time "
                + "'15:30:00.123456789' is not recognized",
            "EXECUTE IMMEDIATE $$ DECLARE v TIME; BEGIN SELECT t INTO :v FROM d1; RETURN HOUR(v); END; $$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET TIME_INPUT_FORMAT");
        assertAnswer("Statement executed successfully.",
            "ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI:SS'");
        answer("EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_NTZ; BEGIN SELECT ts INTO :v FROM d1; RETURN DAY(v); END; $$");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET TIMESTAMP_INPUT_FORMAT");
        assertAnswer("2",
            "EXECUTE IMMEDIATE $$ DECLARE v DATE; BEGIN SELECT d INTO :v FROM d1; RETURN MONTH(v); END; $$");
    }
}
