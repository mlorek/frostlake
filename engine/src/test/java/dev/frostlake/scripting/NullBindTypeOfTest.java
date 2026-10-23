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
 * SYSTEM$TYPEOF of a block variable that holds NULL: a text declared at the width nothing bounds in so many words
 * reads as a text of no width, one declared without a width at that width; a geography or a geometry is bound as
 * an OBJECT and NULL cast to one is a VARIANT; and a date or time function or date arithmetic over the variable
 * folds to an untyped NULL, in a RESULTSET's query and a SELECT … INTO alike (all live-verified, the result cache
 * off: a cached answer is the first one a text ever had, whatever its binds).
 */
public class NullBindTypeOfTest extends MeasuredCellsTestSupport {

    @Test
    public void nullBindsAsTyped() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("VARCHAR[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(134217728); res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF(:s) AS c1); RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR(10)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS "
                + "c2); RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR(16777216)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(16777216); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) "
                + "AS c3); RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR(134217728)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c4); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR(134217728)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s STRING; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c5); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(134217728); res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF(UPPER(:s)) AS c6); RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(134217728); res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF(COALESCE(:s, NULL)) AS c7); RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR(30)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF(UPPER(:s)) AS c8); RETURN TABLE(res); END; $$");
        assertAnswer("DATE[SB4]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c9); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("NUMBER(10,2)[SB1]",
            "EXECUTE IMMEDIATE $$ DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS "
                + "c10); RETURN TABLE(res); END; $$");
        assertAnswer("BOOLEAN[SB1]",
            "EXECUTE IMMEDIATE $$ DECLARE s BOOLEAN; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c11); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("TIMESTAMP_NTZ(9)[SB16]",
            "EXECUTE IMMEDIATE $$ DECLARE s TIMESTAMP_NTZ; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS "
                + "c12); RETURN TABLE(res); END; $$");
        assertAnswer("TIME(9)[SB8]",
            "EXECUTE IMMEDIATE $$ DECLARE s TIME; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c13); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("FLOAT[DOUBLE]",
            "EXECUTE IMMEDIATE $$ DECLARE s FLOAT; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c14); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("VARIANT[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARIANT; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c15); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("BINARY(67108864)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s BINARY; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c16); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("OBJECT[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE g GEOGRAPHY; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:g) AS "
                + "c17); RETURN TABLE(res); END; $$");
        assertAnswer("OBJECT[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE g GEOMETRY; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:g) AS c18); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(DATEADD(day, 1, "
                + ":s)) AS c19); RETURN TABLE(res); END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(YEAR(:s)) AS "
                + "c20); RETURN TABLE(res); END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s + 1) AS c21); "
                + "RETURN TABLE(res); END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF('2024-01-02'::DATE - :s) AS c22); RETURN TABLE(res); END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(WEEKISO(:s)) AS "
                + "c23); RETURN TABLE(res); END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s TIMESTAMP_NTZ; res RESULTSET; BEGIN res := (SELECT "
                + "SYSTEM$TYPEOF(HOUR(:s)) AS c24); RETURN TABLE(res); END; $$");
        assertAnswer("DATE[SB4]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(COALESCE(:s, "
                + "NULL)) AS c25); RETURN TABLE(res); END; $$");
        assertAnswer("VARCHAR(10)[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(10); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) AS c26 INTO :t; "
                + "RETURN t; END; $$");
        assertAnswer("VARCHAR[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR(134217728); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) AS c27 "
                + "INTO :t; RETURN t; END; $$");
        assertAnswer("DATE[SB4]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) AS c28 INTO :t; RETURN "
                + "t; END; $$");
        assertAnswer("NUMBER(10,2)[SB1]",
            "EXECUTE IMMEDIATE $$ DECLARE s NUMBER(10,2); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) AS c29 INTO :t; "
                + "RETURN t; END; $$");
        assertAnswer("OBJECT[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE g GEOGRAPHY; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:g) AS c30 INTO :t; "
                + "RETURN t; END; $$");
        assertAnswer("OBJECT[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE g GEOMETRY; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:g) AS c31 INTO :t; "
                + "RETURN t; END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(DATEADD(day, 1, :s)) AS c32 "
                + "INTO :t; RETURN t; END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(YEAR(:s)) AS c33 INTO :t; "
                + "RETURN t; END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s TIMESTAMP_NTZ; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(HOUR(:s)) AS c34 "
                + "INTO :t; RETURN t; END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF('2024-01-02'::DATE - :s) AS "
                + "c35 INTO :t; RETURN t; END; $$");
        assertAnswer("NULL[LOB]",
            "EXECUTE IMMEDIATE $$ DECLARE s DATE; t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s + 1) AS c36 INTO :t; "
                + "RETURN t; END; $$");
        assertAnswer("VARIANT[LOB]", "SELECT SYSTEM$TYPEOF(NULL::GEOGRAPHY) AS c99");
        assertAnswer("VARIANT[LOB]", "SELECT SYSTEM$TYPEOF(NULL::GEOMETRY) AS c98");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
