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
 * A cursor's query binds none of the block's names while the block compiles: a bind nothing declares is refused when
 * the cursor opens, and a cursor never opened never refuses it (live-verified).
 */
public class CursorUndeclaredBindTest extends MeasuredCellsTestSupport {

    @Test
    public void aCursorsBindIsJudgedWhenItOpens() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, d DATE, s VARCHAR, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE, '2024-01-01', 'X', '2024-01-01 10:00:00'), (2, FALSE, "
            + "'2024-01-02', 'y', '2024-01-02 00:00:00'), (3, NULL, NULL, NULL, NULL)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE cur CURSOR FOR SELECT id FROM fz WHERE id = :nosuch; BEGIN OPEN cur; "
                + "RETURN 1; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 68 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :nosuch not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE cur CURSOR FOR SELECT id FROM fz WHERE id = :nosuch; BEGIN RETURN 1; "
                + "END; $$",
                "1"},
            {"EXECUTE IMMEDIATE $$\nDECLARE\n  cur CURSOR FOR SELECT id FROM fz WHERE id = :nosuch;\nBEGIN\n  "
                + "OPEN cur;\n  RETURN 1;\nEND;\n$$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 5 at position 2 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :nosuch not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE cur CURSOR FOR SELECT id FROM fz WHERE id = :v; v INT DEFAULT 2; "
                + "BEGIN OPEN cur; RETURN 1; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 80 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :v not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT DEFAULT 2; cur CURSOR FOR SELECT id FROM fz WHERE id = :v; "
                + "BEGIN OPEN cur; FETCH cur INTO v; RETURN v; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 80 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :v not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE cur CURSOR FOR SELECT id FROM fz WHERE id = :nosuch; BEGIN FOR r IN "
                + "cur DO RETURN 1; END FOR; RETURN 0; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 68 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :nosuch not set."},
            {"EXECUTE IMMEDIATE $$ DECLARE cur CURSOR FOR SELECT nosuch_col FROM fz; BEGIN RETURN 1; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ DECLARE cur CURSOR FOR SELECT nosuch_col FROM fz; BEGIN OPEN cur; RETURN 1; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOSUCH_COL'"},
            {"EXECUTE IMMEDIATE $$ DECLARE res RESULTSET DEFAULT (SELECT id FROM fz WHERE id = :nosuch); BEGIN "
                + "RETURN 1; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 61 | invalid identifier 'NOSUCH'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cur CURSOR FOR SELECT id FROM fz WHERE id = :nosuch; OPEN cur; "
                + "RETURN 1; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 29 | Bind variable :nosuch not set."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cur CURSOR FOR SELECT id FROM fz WHERE id = :nosuch; RETURN 1; END; $$",
                "1"},
        });
    }
}
