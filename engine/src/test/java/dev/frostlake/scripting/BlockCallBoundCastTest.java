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
 * A block's call over its variables (live-verified): a refusal names a bound NUMBER variable by the conversion
 * the block wrote, {@code CAST('5' AS NUMBER(38,0))}; and a variable read by its bare name, or an argument that
 * reads no name, is the constant LAST_QUERY_ID, UNIFORM and RANDOM ask for.
 */
public class BlockCallBoundCastTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlocksCallOverItsVariables() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.50; RETURN UPPER(DISTINCT n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.50' AS NUMBER(10,2)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN LAST_QUERY_ID(1 + 1) IS NOT NULL; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN LAST_QUERY_ID(ABS(-1)) IS NOT NULL; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := -1; RETURN LAST_QUERY_ID(i) IS NOT NULL; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET lo INT := 1; RETURN UNIFORM(lo, 10, RANDOM(5)) > 0; END; $$", "true"},
            {"BEGIN LET n INT := 5; RETURN ABS(ALL n); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"BEGIN LET n NUMBER(10,2) := 1.50; RETURN UPPER(DISTINCT n); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('1.50' AS NUMBER(10,2)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n FLOAT := 1.5; RETURN ABS(ALL n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('1.5' AS FLOAT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n VARCHAR := 'a'; RETURN UPPER(DISTINCT n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'a')'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(DISTINCT n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'ABS(DISTINCT CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n DATE := '2024-01-15'::DATE; RETURN YEAR(ALL n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: | invalid use of 'all' for function 'YEAR(ALL CAST('2024-01-15' AS DATE))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 2; RETURN LAST_QUERY_ID(i) IS NOT NULL; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET lo INT := 1; LET hi INT := 10; RETURN UNIFORM(lo, hi, RANDOM(5)) > 0; "
                + "END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s INT := 5; RETURN RANDOM(s) IS NOT NULL; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s INT := 5; RETURN UNIFORM(1, 10, s) > 0; END; $$", "true"},
        });
    }
}
