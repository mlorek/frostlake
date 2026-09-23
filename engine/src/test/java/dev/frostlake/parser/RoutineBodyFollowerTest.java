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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Nothing may follow a routine's quoted body: whatever token comes after it is refused at that token, and nothing
 * more of what it begins is reported (live-verified) — an option written after the body included, where Frostlake
 * read the option's keyword on and refused the token after it.
 */
public class RoutineBodyFollowerTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void anOptionAfterAFunctionBodyIsRefusedAtItsKeyword() {
        assertEquals(syntax(40, "COMMENT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' COMMENT = 'x'"));
        assertEquals(syntax(40, "LANGUAGE"), refusal("CREATE FUNCTION o4() RETURNS INT AS '1' LANGUAGE SQL"));
        assertEquals(syntax(40, "COMMENT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' COMMENT ="));
        assertEquals(syntax(40, "RETURNS"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' RETURNS INT"));
        assertEquals(syntax(40, "COPY"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' COPY GRANTS"));
        assertEquals(syntax(40, "CALLED"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' CALLED ON NULL INPUT"));
        assertEquals(syntax(40, "IMPORTS"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' IMPORTS = ('x')"));
        assertEquals(syntax(40, "RUNTIME_VERSION"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' RUNTIME_VERSION = '1'"));
        assertEquals(syntax(40, "COMMENT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' COMMENT = 'x' SELECT 1"));
    }

    @Test
    public void aDollarQuotedBodyAndAProcedureAlike() {
        assertEquals(syntax(42, "COMMENT"), refusal("CREATE FUNCTION o1() RETURNS INT AS $$1$$ COMMENT = 'x'"));
        assertEquals(syntax(73, "COMMENT"),
            refusal("CREATE OR REPLACE FUNCTION o1(a INT) RETURNS TABLE (b INT) AS 'SELECT a' COMMENT = 'x'"));
        assertEquals(syntax(79, "COMMENT"),
            refusal("CREATE FUNCTION o5() RETURNS INT LANGUAGE JAVASCRIPT STRICT AS $$ return 1; $$ COMMENT = 'x'"));
        assertEquals(syntax(72, "HANDLER"),
            refusal("CREATE FUNCTION o5() RETURNS INT LANGUAGE JAVASCRIPT AS $$ return 1; $$ HANDLER = 'x'"));
        assertEquals(syntax(77, "COMMENT"),
            refusal("CREATE PROCEDURE o6() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$ COMMENT = 'x'"));
        assertEquals(syntax(77, "EXECUTE"),
            refusal("CREATE PROCEDURE o6() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$ EXECUTE AS CALLER"));
        assertEquals(syntax(77, "LANGUAGE"),
            refusal("CREATE PROCEDURE o6() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$ LANGUAGE SQL"));
    }

    @Test
    public void everyOtherFollowerIsRefusedWhereItStands() {
        assertEquals(syntax(40, "("), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' (1)"));
        assertEquals(syntax(40, "IMMUTABLE"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' IMMUTABLE"));
        assertEquals(syntax(40, "COMMENT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' COMMENT"));
        assertEquals(syntax(40, "x"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' x"));
        assertEquals(syntax(40, "SELECT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' SELECT 1"));
        assertEquals(syntax(40, "COMMENT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' COMMENT ON TABLE t IS 'x'"));
        assertEquals(syntax(40, "NOT"), refusal("CREATE FUNCTION o1() RETURNS INT AS '1' NOT NULL"));
    }
}
