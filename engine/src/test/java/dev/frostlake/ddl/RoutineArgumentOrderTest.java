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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A routine's required argument written after an optional one is refused at its name, naming the optional one
 * nearest before it (live-verified). The rule is judged before a repeated name, the body, the language's own type
 * rule, an unknown type and a missing schema; a default too wide to read, standing before the argument, is still
 * refused first.
 */
public class RoutineArgumentOrderTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private static String misplaced(final int line, final int position, final String required, final String optional) {
        return "SQL compilation error: error line " + line + " at position " + position + "\nrequired argument "
            + required + " cannot follow optional argument " + optional;
    }

    @Test
    public void aRequiredArgumentAfterAnOptionalOneIsRefusedAtItsName() {
        assertEquals(misplaced(1, 47, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION cf(x INT DEFAULT 1, y VARCHAR) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 48, "Y", "X"),
            refusal("CREATE OR REPLACE PROCEDURE cp(x INT DEFAULT 1, y VARCHAR) RETURNS INT"
                + " LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$"));
        assertEquals(misplaced(1, 65, "C", "B"),
            refusal("CREATE OR REPLACE FUNCTION cf2(a INT DEFAULT 1, b INT DEFAULT 2, c INT) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 48, "B", "A"),
            refusal("CREATE OR REPLACE FUNCTION cf3(a INT DEFAULT 1, b INT, c INT DEFAULT 3, d INT)"
                + " RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 50, "y z", "x"),
            refusal("CREATE OR REPLACE FUNCTION cf4(\"x\" INT DEFAULT 1, \"y z\" INT) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 50, "BB", "AA"),
            refusal("CREATE OR REPLACE FUNCTION cf11(aa INT DEFAULT 1, bb INT) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(3, 2, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION cf12(\n  x INT DEFAULT 1,\n  y INT) RETURNS INT AS $$ 1 $$"));
    }

    @Test
    public void theOrderIsJudgedBeforeTheRestOfTheStatement() {
        assertEquals(misplaced(1, 48, "X", "X"),
            refusal("CREATE OR REPLACE FUNCTION cf5(x INT DEFAULT 1, x INT) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 55, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION cf6(x INT, x INT DEFAULT 1, y INT) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 48, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION cf7(x INT DEFAULT 1, y INT) RETURNS INT AS $$ nosuchcol $$"));
        assertEquals(misplaced(1, 48, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION cf9(x INT DEFAULT 1, y NOSUCHTYPE) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 55, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION ut2(x NOSUCHTYPE DEFAULT 1, y INT) RETURNS INT AS $$ 1 $$"));
        assertEquals(misplaced(1, 48, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION ut3(x INT DEFAULT 1, y INT) RETURNS NOSUCHTYPE AS $$ 1 $$"));
        assertEquals(misplaced(1, 62, "Y", "X"),
            refusal("CREATE OR REPLACE FUNCTION nosuchschema.cf10(x INT DEFAULT 1, y INT) RETURNS INT AS $$ 1 $$"));
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.",
            refusal("CREATE OR REPLACE FUNCTION ut1(y NOSUCHTYPE) RETURNS INT AS $$ 1 $$"));
    }

    @Test
    public void argumentsInOrderAreCreated() {
        engine.execute("CREATE OR REPLACE FUNCTION cf20(x INT, y INT DEFAULT 1, z INT DEFAULT 2) RETURNS INT AS $$ x $$");
        assertEquals("7", String.valueOf(engine.executeQuery("SELECT cf20(7)").getRows().get(0).getValue(0)));
    }
}
