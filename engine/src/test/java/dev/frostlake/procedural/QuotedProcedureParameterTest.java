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
 * A procedure's quoted parameter keeps its case: "low" is read by "low" alone, low and "LOW" are invalid
 * identifiers, and a named argument names it quoted; a block's quoted variable reads the same way (all
 * live-verified).
 */
public class QuotedProcedureParameterTest extends MeasuredCellsTestSupport {

    @Test
    public void quotedNames() {
        engine.execute("CREATE OR REPLACE PROCEDURE pqq(\"low\" INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "\"low\"; END; $$");
        assertAnswer("1", "CALL pqq(1)");
        assertAnswer("1", "CALL pqq(\"low\" => 1)");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | named arguments [LOW] do not match "
                + "any signature for function PQQ",
            "CALL pqq(low => 1)");
        assertAnswer("Function PQQ2 successfully created.",
            "CREATE OR REPLACE PROCEDURE pqq2(\"low\" INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN low; END; $$");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'LOW'",
            "CALL pqq2(1)");
        assertAnswer("Function PQQ3 successfully created.",
            "CREATE OR REPLACE PROCEDURE pqq3(\"low\" INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN \"LOW\"; END; "
                + "$$");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'LOW'",
            "CALL pqq3(1)");
        assertAnswer("Function PQQ4 successfully created.",
            "CREATE OR REPLACE PROCEDURE pqq4(\"low\" INT, low INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN "
                + "\"low\" + low * 10; END; $$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function PQQ5.", "CALL pqq5(1)");
        assertAnswer("Function PQQ6 successfully created.",
            "CREATE OR REPLACE PROCEDURE pqq6(\"low\" INT) RETURNS INT LANGUAGE SQL AS $$ DECLARE r INT; BEGIN "
                + "SELECT \"low\" + 1 INTO :r; RETURN r; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier '\"low\"'",
            "CALL pqq6(1)");
        assertAnswer("1", "EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; BEGIN RETURN \"low\"; END; $$");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'LOW'",
            "EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; BEGIN RETURN low; END; $$");
        assertAnswer("5",
            "EXECUTE IMMEDIATE $$ DECLARE \"low\" INT DEFAULT 1; BEGIN \"low\" := 5; RETURN \"low\"; END; $$");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'MX'",
            "EXECUTE IMMEDIATE $$ DECLARE \"Mx\" INT DEFAULT 1; BEGIN RETURN \"MX\"; END; $$");
        assertAnswer("1", "EXECUTE IMMEDIATE $$ DECLARE mx INT DEFAULT 1; BEGIN RETURN \"MX\"; END; $$");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier '\"mx\"'",
            "EXECUTE IMMEDIATE $$ DECLARE mx INT DEFAULT 1; BEGIN RETURN \"mx\"; END; $$");
        assertAnswer("signature|(low NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body| "
                + "BEGIN RETURN \"low\"; END; ",
            "DESCRIBE PROCEDURE pqq(INT)");
    }
}
