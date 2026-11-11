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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * An OVERLAY written in the PLACING form names the PLACING and then what its brackets decide in more shapes
 * (live-verified): a later select item after items of one token, a plain parenthesis around an expression value,
 * four calls, two calls in a cast and two calls in a cast in a call; in a block, the first word after its SELECT's
 * semicolon - the next statement's, the END of a DECLARE block, the outermost END of nested blocks - and a call in a
 * RESULTSET's query with the call's and the query's closing brackets; and the text EXECUTE IMMEDIATE runs as a
 * statement of its own.
 */
public class OverlayPlacingMoreShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void placingLinesInMoreShapes() {
        engine.execute("CREATE OR REPLACE TABLE tb931 (b VARCHAR)");
        assertCells(new String[][] {
            {"BEGIN SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb931; RETURN 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 46 unexpected '2'. | syntax error line 1 at position 62 "
                + "unexpected 'RETURN'."},
            {"BEGIN LET r RESULTSET := (SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb931); RETURN TABLE(r); END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 66 unexpected '2'. | syntax error line 1 at position 68 "
                + "unexpected ')'. | syntax error line 1 at position 80 unexpected ')'."},
            {"DECLARE x INT; BEGIN SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb931; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 61 unexpected '2'. | syntax error line 1 at position 77 "
                + "unexpected 'END'."},
            {"BEGIN BEGIN SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb931; END; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 52 unexpected '2'. | syntax error line 1 at position 73 "
                + "unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb931$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 40 unexpected '2'."},
            {"SELECT UPPER(UPPER(UPPER(UPPER(OVERLAY(b PLACING 'x' FROM 2))))) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 62 unexpected ')'."},
            {"SELECT UPPER(CAST(UPPER(UPPER(OVERLAY(b PLACING 'x' FROM 2))) AS INT)) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 59 unexpected ')'. | syntax error line 1 at position 60 "
                + "unexpected ')'."},
            {"SELECT CAST(UPPER(UPPER(OVERLAY(b PLACING 'x' FROM 2))) AS INT) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 53 unexpected ')'. | syntax error line 1 at position 62 "
                + "unexpected ')'."},
            {"SELECT UPPER((UPPER(OVERLAY(b PLACING 'x' FROM 2)))) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 49 unexpected ')'."},
            {"SELECT (OVERLAY(b PLACING 'x' || 'y' FROM 2)) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 26 unexpected ''x''."},
            {"SELECT 1, 2, UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 46 unexpected '2'."},
            {"SELECT 1, 2, (OVERLAY(b PLACING 'x' FROM 2)) FROM tb931",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'PLACING'. | "
                + "syntax error line 1 at position 32 unexpected ''x''."},
        });
    }
}
