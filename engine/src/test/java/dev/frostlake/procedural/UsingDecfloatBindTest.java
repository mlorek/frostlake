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
 * A DECFLOAT bound through OPEN ... USING or EXECUTE IMMEDIATE ... USING is its own text - 1.5e300, 1e16384,
 * 1.2345678901234567890123456789012345678e-20 - so a numeric context refuses 1.5e300 by that text, as a VARCHAR of
 * its length (all live-verified).
 */
public class UsingDecfloatBindTest extends MeasuredCellsTestSupport {

    @Test
    public void decfloatBoundAsItsText() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Function P48 successfully created.",
            "CREATE OR REPLACE PROCEDURE p48(x DECFLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR "
                + "SELECT (? / 3)::VARCHAR || ' ' || SYSTEM$TYPEOF(?) v; r VARCHAR; BEGIN OPEN c USING (x, x); FETCH "
                + "c INTO r; RETURN r; END $$");
        assertAnswer("0.333333 VARCHAR(1)[LOB]", "CALL p48(DECFLOAT '1')");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 93 : Numeric value "
                + "'1.5e300' is not recognized",
            "CALL p48(DECFLOAT '1.5e300')");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 93 : SQL compilation "
                + "error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "CALL p48(DECFLOAT '1e-50')");
        assertAnswer("41.152000000 VARCHAR(7)[LOB]", "CALL p48(DECFLOAT '123.456')");
        assertAnswer("Function Q48 successfully created.",
            "CREATE OR REPLACE PROCEDURE q48(x DECFLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT (? / 3)::VARCHAR || '' '' || SYSTEM$TYPEOF(?) v' "
                + "USING (x, x)); LET c CURSOR FOR rs; OPEN c; FETCH c INTO r; RETURN r; END $$");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 47 : SQL compilation "
                + "error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "CALL q48(DECFLOAT '1e-50')");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 47 : Numeric value "
                + "'1.5e300' is not recognized",
            "CALL q48(DECFLOAT '1.5e300')");
        assertAnswer("-2.333333 VARCHAR(2)[LOB]", "CALL q48(DECFLOAT '-7')");
        assertAnswer("Function S48 successfully created.",
            "CREATE OR REPLACE PROCEDURE s48(x DECFLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "LET rs RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?::VARCHAR v' USING (x)); LET c CURSOR FOR rs; "
                + "OPEN c; FETCH c INTO r; RETURN r; END $$");
        assertAnswer("1.2345678901234567890123456789012345678e-20",
            "CALL s48(DECFLOAT '1.2345678901234567890123456789012345678e-20')");
        assertAnswer("1e16384", "CALL s48(DECFLOAT '1e16384')");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
