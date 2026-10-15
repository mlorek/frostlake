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
 * IDENTIFIER() over a block's variable that holds no identifier is refused with the text the variable holds
 * between quotes — "invalid identifier ''" for an empty one — where a literal is echoed as it is written,
 * quotes and all (live-verified).
 */
public class IdentifierBindEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void theTextTheVariableHolds() {
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : SQL compilation "
                + "error: error line 1 at position 39 | invalid identifier ''",
            "DECLARE s VARCHAR DEFAULT ''; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i2 FROM "
                + "IDENTIFIER(:s)); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier ''''",
            "SELECT COUNT(*) FROM IDENTIFIER('')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                + "error: error line 1 at position 39 | invalid identifier ' '",
            "DECLARE s VARCHAR DEFAULT ' '; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i2 FROM "
                + "IDENTIFIER(:s)); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: | Object '\"s\"' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE \"s\".",
            "DECLARE s VARCHAR; res RESULTSET; BEGIN res := (SELECT COUNT(*) AS i2 FROM IDENTIFIER(:s)); RETURN "
                + "TABLE(res); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_ZQ7.",
            "SELECT COUNT(*) FROM IDENTIFIER('nosuch_zq7')");
        assertAnswer(
            "REFUSED SQL compilation error: | Object '\"\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"\".",
            "SELECT COUNT(*) FROM IDENTIFIER('\"\"')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                + "error: error line 1 at position 32 | invalid identifier ''",
            "DECLARE s VARCHAR DEFAULT ''; x INT; BEGIN SELECT COUNT(*) INTO :x FROM IDENTIFIER(:s); RETURN x; "
                + "END;");
    }
}
