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
 * An IDENTIFIER() text that names nothing, {@code IDENTIFIER('')}, is refused while the block compiles, before any
 * statement runs: placed in the block's text and with no uncaught-exception wrapper, in a plain statement and in a
 * RESULTSET's query alike (live-verified).
 */
public class BlockIdentifierLiteralTest extends MeasuredCellsTestSupport {

    @Test
    public void anEmptyIdentifierIsRefusedAsTheBlockCompiles() {
        engine.execute("CREATE OR REPLACE TABLE tp2 (k INT, v VARCHAR)");
        engine.execute("INSERT INTO tp2 VALUES (1, 'a')");
        assertCells(new String[][] {
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS() FROM tp2 WHERE k = :s); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | not enough arguments for function [ABS()], expected 1, got 0"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT COUNT(*) FROM IDENTIFIER('')); "
                + "RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: error line 1 at position 86 | invalid identifier ''''"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT COUNT(*) FROM IDENTIFIER('nosuch')); "
                + "RETURN TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: | Object 'NOSUCH' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE NOSUCH."},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT k FROM IDENTIFIER('tp2')); RETURN "
                + "TABLE(res); END;",
                "1"},
            {"DECLARE s INT DEFAULT 1; BEGIN SELECT ABS(:s, 1) FROM tp2; RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(:s, 1)] expected 1, got 2"},
            {"DECLARE res RESULTSET; BEGIN res := (SELECT COUNT(*) FROM IDENTIFIER('')); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier ''''"},
            {"BEGIN SELECT COUNT(*) FROM IDENTIFIER(''); RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier ''''"},
        });
    }
}
