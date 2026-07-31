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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A UDF parameter declared with a DEFAULT may be omitted at the call site. The default was already parsed and
 * stored, and a stored-procedure CALL already applied it, but a FUNCTION call resolved strictly by exact
 * parameter count — so a call that relied on defaults matched no overload, and because the caller swallows that
 * exception it surfaced as the misleading "Unknown function".
 */
public class UdfParameterDefaultTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFunctions() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION tag(a INTEGER, b VARCHAR DEFAULT 'B', c VARCHAR DEFAULT 'C')
            RETURNS VARCHAR AS $$ a || '|' || b || '|' || c $$""");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void allArgumentsSuppliedPositionally() {
        assertEquals("1|x|y", scalar("SELECT tag(1, 'x', 'y')"));
    }

    @Test
    public void aTrailingDefaultMayBeOmitted() {
        assertEquals("1|x|C", scalar("SELECT tag(1, 'x')"));
    }

    @Test
    public void everyDefaultMayBeOmitted() {
        assertEquals("1|B|C", scalar("SELECT tag(1)"));
    }

    @Test
    public void aParameterWithoutADefaultIsStillRequired() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT tag()");
            }
        });
    }

    @Test
    public void tooManyArgumentsAreStillRejected() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT tag(1, 'x', 'y', 'z')");
            }
        });
    }

    // ── named arguments ──────────────────────────────────────────────────────

    @Test
    public void aNamedCallMayLeaveADefaultedParameterUnnamed() {
        // B is never named, so it takes its DEFAULT rather than NULL.
        assertEquals("1|B|z", scalar("SELECT tag(1, c => 'z')"));
    }

    @Test
    public void namedArgumentsMayBeGivenOutOfOrder() {
        assertEquals("1|p|q", scalar("SELECT tag(1, c => 'q', b => 'p')"));
    }

    // ── interaction with overloads and expression defaults ───────────────────

    @Test
    public void overloadResolutionPrefersTheExactArityBeforeApplyingDefaults() {
        engine.execute("CREATE OR REPLACE FUNCTION pick(a INTEGER) RETURNS VARCHAR AS $$ 'one' $$");
        engine.execute("CREATE OR REPLACE FUNCTION pick(a INTEGER, b INTEGER, c INTEGER DEFAULT 9) "
            + "RETURNS VARCHAR AS $$ 'three:' || c $$");
        assertEquals("one", scalar("SELECT pick(1)"));
        assertEquals("three:9", scalar("SELECT pick(1, 2)"));
        assertEquals("three:3", scalar("SELECT pick(1, 2, 3)"));
    }

    @Test
    public void aDefaultMayBeAnExpression() {
        engine.execute("CREATE OR REPLACE FUNCTION addup(a INTEGER, b INTEGER DEFAULT 2 + 3) "
            + "RETURNS INTEGER AS $$ a + b $$");
        assertEquals(6L, ((Number) scalar("SELECT addup(1)")).longValue());
    }

    @Test
    public void anExplicitNullIsNotReplacedByTheDefault() {
        engine.execute("CREATE OR REPLACE FUNCTION addup(a INTEGER, b INTEGER DEFAULT 2 + 3) "
            + "RETURNS INTEGER AS $$ a + b $$");
        assertNull(scalar("SELECT addup(1, NULL)"));
    }

    @Test
    public void defaultsApplyToANonSqlLanguageToo() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION jv_tag(name VARCHAR, kind VARCHAR DEFAULT 'str')
            RETURNS VARCHAR
            LANGUAGE JAVA
            HANDLER = 'Tag.h'
            AS $$
            class Tag {
              public static String h(String name, String kind) {
                return name + "|" + kind;
              }
            }
            $$""");
        assertEquals("n|str", String.valueOf(scalar("SELECT jv_tag('n')")));
    }

    // ── the body of a SQL UDF is already unquoted when stored ────────────────

    @Test
    public void aSqlBodyThatIsAStringLiteralEvaluatesToThatString() {
        // The body was unquoted a SECOND time at call time, so `$$ 'plain' $$` became the column reference
        // `plain` and failed with "Column not found: PLAIN".
        engine.execute("CREATE OR REPLACE FUNCTION lit(a INTEGER) RETURNS VARCHAR AS $$ 'plain' $$");
        assertEquals("plain", scalar("SELECT lit(1)"));
    }

    @Test
    public void aSqlBodyMayConcatenateStringLiterals() {
        engine.execute("CREATE OR REPLACE FUNCTION cat(a INTEGER) RETURNS VARCHAR AS $$ 'x' || 'y-z' $$");
        assertEquals("xy-z", scalar("SELECT cat(1)"));
    }
}
