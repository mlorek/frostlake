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
 * What a Scala handler's object holds lasts as long as a Java handler's static fields do: a function's for the
 * statement that calls it and the place in it that does, so a later statement and another call of the function in
 * the same statement start anew, and a procedure's for its CALL. Every answer is live-verified.
 */
public class ScalaHandlerStateTest extends MeasuredCellsTestSupport {

    @Test
    public void aFunctionsObjectLastsForItsStatementsCall() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION scnt(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { var n = 0; def f(a: Int): Int = { n += 1; n } } $$",
                "Function SCNT successfully created."},
            {"SELECT scnt(1)", "1"},
            {"SELECT scnt(1)", "1"},
            {"SELECT scnt(1), scnt(1)", "1|1"},
        });
    }

    @Test
    public void aProceduresObjectLastsForItsCall() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE sjcnt(a NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; object H { var n = 0; def run(s: Session, a: Int): Int = { "
                + "n += 1; n } } $$", "Function SJCNT successfully created."},
            {"CALL sjcnt(1)", "1"},
            {"CALL sjcnt(1)", "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN CALL sjcnt(1) INTO :x; CALL sjcnt(1) INTO :y; RETURN "
                + "x || '|' || y; END; $$", "1|1"},
        });
    }
}
