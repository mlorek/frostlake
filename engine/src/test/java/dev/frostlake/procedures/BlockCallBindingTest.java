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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A block binds a NUMBER, FLOAT or BOOLEAN variable into a call as the conversion of its text to its declared type
 * (live-verified): TO_VARCHAR, TO_CHAR, UPPER and CONCAT of a NUMBER(10,2) holding 1.50 read 1.50, and a refused
 * call echoes the variable as CAST('1.5' AS FLOAT) or CAST('true' AS BOOLEAN).
 */
public class BlockCallBindingTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariableIsBoundAsItsDeclaredType() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET f FLOAT := 1.5; RETURN UPPER(DISTINCT f); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.5' AS FLOAT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET b BOOLEAN := TRUE; RETURN UPPER(DISTINCT b); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('true' AS BOOLEAN))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN TO_VARCHAR(n); END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; LET t VARCHAR := TO_VARCHAR(n); RETURN t; END; $$",
                "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN n::VARCHAR; END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN n || ''; END; $$", "1.50"},
        });
    }

    @Test
    public void aDeclaredNumberKeepsItsScaleInACall() {
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(1.50)", "1.5"},
            {"SELECT TO_VARCHAR(1.50::NUMBER(10,2))", "1.50"},
            {"SELECT TO_VARCHAR(10 / 4)", "2.500000"},
            {"SELECT TO_VARCHAR(CAST('1.50' AS NUMBER(10,2)))", "1.50"},
            {"SELECT TO_VARCHAR(CAST('5' AS NUMBER(38,0)) / 4)", "1.250000"},
            {"SELECT TO_VARCHAR(CAST('1.50' AS NUMBER(10,2)) + 1)", "2.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN n; END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN n::VARCHAR; END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN TO_CHAR(n); END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN UPPER(n); END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN CONCAT(n, ''); END; $$", "1.50"},
        });
    }
}
