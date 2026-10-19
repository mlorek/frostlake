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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A context word written without its parentheses inside a call's arguments in a block's own expression is a
 * name no block declares, refused at its place while the block compiles, as an undeclared name is there; a
 * date part's unit, a declared variable and a query the block runs read as before. A quoted name is echoed with
 * its quotes only where it needs them (all live-verified).
 */
public class BlockContextWordArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void aBareContextWordInACallsArgumentsIsAnUndeclaredName() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'CURRENT_DATE'",
            "BEGIN RETURN MONTHS_BETWEEN(NULL, CURRENT_DATE); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'CURRENT_DATE'",
            "DECLARE s DATE; BEGIN RETURN NVL(s, CURRENT_DATE) IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'CURRENT_DATE'",
            "DECLARE s DATE; BEGIN LET x := DATEDIFF(DAY, s, CURRENT_DATE); RETURN x; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'CURRENT_DATE'",
            "BEGIN LET x := 1; x := YEAR(CURRENT_DATE); RETURN x > 0; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'CURRENT_DATE'",
            "BEGIN RETURN \"CURRENT_DATE\" IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'NOSUCH'",
            "BEGIN RETURN YEAR(nosuch); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH'",
            "BEGIN RETURN NVL(nosuch, 1); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'CURRENT_USER'",
            "BEGIN RETURN LENGTH(CURRENT_USER) > 0; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'LOCALTIMESTAMP'",
            "BEGIN RETURN LENGTH(TO_VARCHAR(LOCALTIMESTAMP)) > 0; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'CURRENT_DATE'",
            "BEGIN IF (YEAR(CURRENT_DATE) > 2000) THEN RETURN 1; END IF; RETURN 0; END;");
        assertAnswer("2", "BEGIN RETURN DATEDIFF(DAY, '2020-01-01'::DATE, '2020-01-03'::DATE); END;");
        assertAnswer("2020", "BEGIN RETURN DATE_PART(YEAR, '2020-05-01'::DATE); END;");
        assertAnswer("2020", "BEGIN RETURN EXTRACT(YEAR FROM '2020-05-01'::DATE); END;");
        assertAnswer("2020", "DECLARE d DATE DEFAULT '2020-01-01'; BEGIN RETURN YEAR(d); END;");
        assertAnswer("true", "BEGIN LET x := (SELECT YEAR(CURRENT_DATE)); RETURN x > 0; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier '\"a b\"'",
            "BEGIN RETURN \"a b\" IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier '\"abc\"'",
            "BEGIN RETURN \"abc\" IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'ABC'",
            "BEGIN RETURN \"ABC\" IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH'",
            "BEGIN RETURN ABS(nosuch + 1); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'CURRENT_TIME'",
            "BEGIN RETURN COALESCE(1, CURRENT_TIME) IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH'",
            "BEGIN RETURN IFF(TRUE, 1, nosuch); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'NOSUCH'",
            "BEGIN RETURN CASE WHEN TRUE THEN 1 ELSE YEAR(nosuch) END; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'CURRENT_DATE'",
            "BEGIN RETURN ARRAY_CONSTRUCT(CURRENT_DATE); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: error line 1 at position 0 | invalid identifier 'NOSUCHUNIT'",
            "BEGIN RETURN DATEADD(nosuchunit, 1, '2020-01-01'::DATE); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier "
                + "'CURRENT_TIMESTAMP'",
            "BEGIN RETURN TO_CHAR(CURRENT_TIMESTAMP, 'YYYY') > '2000'; END;");
        assertAnswer("Function P_CW successfully created.",
            "CREATE OR REPLACE PROCEDURE p_cw() RETURNS BOOLEAN LANGUAGE SQL AS 'BEGIN RETURN YEAR(CURRENT_DATE) > "
                + "0; END;'");
    }
}
