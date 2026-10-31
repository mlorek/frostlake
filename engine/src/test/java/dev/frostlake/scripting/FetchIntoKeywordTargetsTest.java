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
 * FETCH … INTO a variable named by CASE, WHEN, CAST, DEFAULT, CONSTRAINT, JOIN, INNER or CROSS, as the account
 * reads it (live-verified): the target is the variable, as SELECT … INTO :join's is, a DECLARE section's and a LET's
 * alike, and a target nothing declares is refused by its name; a DECLAREd exception so named is raised and caught.
 */
public class FetchIntoKeywordTargetsTest extends MeasuredCellsTestSupport {

    /** Each block and its answer. */
    @Test
    public void fetchedIntoVariablesNamedByTheWords() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET join := 5; RETURN :join + 1; END; $$",
                "6"},
            {"EXECUTE IMMEDIATE $$ DECLARE join INT; BEGIN SELECT 7 INTO :join; RETURN join; END; $$",
                "7"},
            {"EXECUTE IMMEDIATE $$ DECLARE join EXCEPTION (-20001, 'boom'); BEGIN RAISE join; EXCEPTION WHEN join "
                + "THEN RETURN 'caught'; END; $$",
                "caught"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET join := 0; LET c CURSOR FOR SELECT 3; OPEN c; FETCH c INTO join; RETURN "
                + "join; END; $$",
                "3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET a := 0; LET inner := 0; LET cast := 0; LET c CURSOR FOR SELECT 1, 2, 3; "
                + "OPEN c; FETCH c INTO a, inner, cast; RETURN a + inner + cast; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 143 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE default INT DEFAULT 0; c CURSOR FOR SELECT 9; BEGIN OPEN c; FETCH c INTO "
                + "default; RETURN default; END; $$",
                "9"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT 9; OPEN c; FETCH c INTO join; RETURN 1; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'JOIN'"},
        });
    }
}
