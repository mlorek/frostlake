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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * CASE, WHEN, CAST, DEFAULT, CONSTRAINT, JOIN, INNER and CROSS name a routine's or a policy's argument, a named
 * argument of a call, and a script's variable, cursor or RESULTSET as LET, FOR, OPEN, INTO and a bind name it. Where a
 * value is read the words keep their own constructs, and an ORDER BY or GROUP BY key written DEFAULT reads nothing
 * (live-verified).
 */
public class KeywordArgumentNamesTest extends BaseDatabaseTest {

    @Test
    public void aRoutineTakesTheWordsAsItsArgumentsNames() {
        assertCells(engine, new String[][] {
            {"CREATE FUNCTION kw_case(case INT, when VARCHAR) RETURNS INT AS $$ 1 $$",
                "Function KW_CASE successfully created."},
            {"SELECT kw_case(when => 'a', case => 2)", "1"},
            {"DESCRIBE FUNCTION kw_case(INT, VARCHAR) ->> SELECT \"value\" FROM $1 WHERE \"property\" = 'signature'",
                "(CASE NUMBER, WHEN VARCHAR)"},
            {"CREATE FUNCTION kw_join(join INT) RETURNS INT AS $$ join + 1 $$",
                "Function KW_JOIN successfully created."},
            {"SELECT kw_join(5), kw_join(join => 6)", "6, 7"},
            {"CREATE FUNCTION kw_def(default INT) RETURNS INT AS $$ \"DEFAULT\" + 1 $$",
                "Function KW_DEF successfully created."},
            {"SELECT kw_def(5)", "6"},
            {"CREATE FUNCTION kw_opt(case INT DEFAULT 3) RETURNS INT AS $$ 1 $$",
                "Function KW_OPT successfully created."},
            {"SELECT kw_opt()", "1"},
            {"CREATE PROCEDURE kw_proc(join INT) RETURNS INT LANGUAGE SQL AS $$ DECLARE r INT; "
                + "BEGIN SELECT :join + 1 INTO :r; RETURN r; END; $$", "Function KW_PROC successfully created."},
            {"CALL kw_proc(5)", "6"},
            {"CALL kw_proc(join => 6)", "7"},
            {"CREATE MASKING POLICY kw_mask AS (join VARCHAR) RETURNS VARCHAR -> 'x'",
                "Masking policy KW_MASK successfully created."},
        });
    }

    @Test
    public void aScriptNamesItsVariablesByTheWords() {
        assertCells(engine, new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET join := 1; RETURN join; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET when := 1; RETURN 1; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cross := 'c'; RETURN cross || 'd'; END; $$", "cd"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET constraint INT := 2; RETURN constraint; END; $$", "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET default := 4; default := default + 1; RETURN default; END; $$", "5"},
            {"EXECUTE IMMEDIATE $$ DECLARE s INT DEFAULT 0; BEGIN FOR inner IN 1 TO 3 DO s := s + inner; END FOR; "
                + "RETURN s; END; $$", "6"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET case := 4; LET r INT := 0; SELECT :case + 1 INTO :r; RETURN r; END; $$",
                "5"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET join INT := 0; SELECT 6 INTO join; RETURN join; END; $$", "6"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET join CURSOR FOR SELECT 1 AS a; OPEN join; CLOSE join; RETURN 2; END; $$",
                "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET join RESULTSET := (SELECT 3 AS a); RETURN TABLE(join); END; $$", "3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 4 AS a; s INT DEFAULT 0; "
                + "BEGIN FOR join IN c DO s := s + join.a; END FOR; RETURN s; END; $$", "4"},
        });
    }

    @Test
    public void aValueStillReadsTheWordsAsTheirConstructs() {
        assertCells(engine, new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 4; BEGIN RETURN default; END; $$",
                "SQL compilation error: error line 1 at position 39|invalid identifier 'DEFAULT'"},
            {"SELECT default FROM (SELECT 1 AS \"DEFAULT\")",
                "SQL compilation error: error line 1 at position 7|invalid identifier 'DEFAULT'"},
        });
    }

    @Test
    public void aKeyWrittenDefaultReadsNoOutputName() {
        assertCells(engine, new String[][] {
            {"SELECT 1 AS default ORDER BY default",
                "SQL compilation error: error line 1 at position 29|invalid identifier 'DEFAULT'"},
            {"SELECT 1 AS default ORDER BY 1, default",
                "SQL compilation error: error line 1 at position 32|invalid identifier 'DEFAULT'"},
            {"SELECT 1 AS d GROUP BY default",
                "SQL compilation error: error line 1 at position 23|invalid identifier 'DEFAULT'"},
            {"SELECT COUNT(*) FROM (SELECT 1 AS x) GROUP BY default",
                "SQL compilation error: error line 1 at position 46|invalid identifier 'DEFAULT'"},
            {"SELECT 1 AS default ORDER BY \"DEFAULT\"", "1"},
            {"SELECT 1 AS join ORDER BY join", "1"},
        });
    }
}
