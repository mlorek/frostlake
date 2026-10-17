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
 * A query assigned to a RESULTSET echoes each bind as the block wrote it in an argument-count refusal: {@code res :=
 * (SELECT ABS(:s, 1) FROM tp)} is {@code too many arguments for function [ABS(:s, 1)]}, whatever value the bind
 * holds, as a block's own statement echoes it (live-verified).
 */
public class ResultSetBindEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void aBindIsEchoedAsWritten() {
        engine.execute("CREATE OR REPLACE TABLE tp (k INT, v VARCHAR)");
        engine.execute("INSERT INTO tp VALUES (1, 'a')");
        assertCells(new String[][] {
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(:s, 1) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(:s, 1)] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(:s, 1, 2) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(:s, 1, 2)] expected 1, "
                + "got 3"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT UPPER(:s, 1) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [UPPER(:s, 1)] expected 1, "
                + "got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(:s, :s) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(:s, :s)] expected 1, got "
                + "2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(:s + 1, 1) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS((:s) + 1, 1)] expected "
                + "1, got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(ABS(:s), 1) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(ABS(:s), 1)] expected 1, "
                + "got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(1, :s) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, :s)] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS() FROM tp WHERE k = :s); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | not enough arguments for function [ABS()], expected 1, got 0"},
            {"DECLARE s VARCHAR DEFAULT 'x'; res RESULTSET; BEGIN res := (SELECT ABS(:s, 1) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(:s, 1)] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; BEGIN SELECT ABS(:s, 1) FROM tp; RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(:s, 1)] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET DEFAULT (SELECT ABS(:s, 1) FROM tp); BEGIN RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(:s, 1)] expected 1, got 2"},
            {"DECLARE s INT DEFAULT 1; c CURSOR FOR SELECT ABS(:s, 1) FROM tp; BEGIN OPEN c; RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 71 : SQL compilation "
                + "error: error line 1 at position 11 | Bind variable :s not set."},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(s, 1) FROM tp); RETURN TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 12 | invalid identifier 'S'"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT LENGTH(:s, 1) FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [LENGTH(:s, 1)] expected 1, "
                + "got 2"},
            {"DECLARE s INT DEFAULT 1; res RESULTSET; BEGIN res := (SELECT ABS(:s, 'a') FROM tp); RETURN "
                + "TABLE(res); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(:s, 'a')] expected 1, "
                + "got 2"},
        });
    }
}
