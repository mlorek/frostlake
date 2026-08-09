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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Java handler's class and method have to be declared as, measured against live:
 *
 * <pre>
 *   class C  { public … go(…) }   accepted     — the handler CLASS need not be public
 *   class C3 { static int go(…) } refused      — the handler METHOD must be public
 * </pre>
 *
 * <p>Both halves matter. A bare {@code class H { … }} is the shape Snowflake's own examples use, so
 * refusing it turns working Snowflake SQL away; and the method rule is the one thing that stops the
 * accessibility fix from going too far, since reflection would happily invoke a package-private method
 * once it is made accessible.
 *
 * <p>Live words the refusal {@code Failed to find a public method named "go" with 1 arguments in
 * function <name> with handler <handler>} — "function" even for a procedure, "1 arguments" even for one
 * — and counts a procedure handler's leading Session in that arity.
 */
public class JavaHandlerAccessibilityTest extends BaseDatabaseTest {

    private static final String PACKAGES = " PACKAGES = ('com.snowflake:snowpark:latest')";

    /** The shape Snowflake's docs use: a bare class, a public method, a wildcard import. */
    @Test
    public void aPackagePrivateHandlerClassIsAccepted() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE testsp() RETURNS VARCHAR LANGUAGE JAVA
            RUNTIME_VERSION = 11 PACKAGES = ('com.snowflake:snowpark:latest')
            HANDLER = 'TestJavaSP.test' AS
            $$
            import com.snowflake.snowpark_java.*;

            class TestJavaSP {
              public String test(Session session) throws Exception {
                    return "OK";
              }
            }
            $$""");
        assertEquals("OK", engine.executeQuery("CALL testsp()").getRows().get(0).getValue(0));
    }

    /** The same body with the import spelled out, and with none at all — all three forms run. */
    @Test
    public void theImportFormDoesNotMatter() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_explicit() RETURNS VARCHAR LANGUAGE JAVA"
            + PACKAGES + " HANDLER = 'H.go' AS $$\n"
            + "import com.snowflake.snowpark_java.Session;\n"
            + "class H { public String go(Session s) { return \"OK\"; } }\n$$");
        assertEquals("OK", engine.executeQuery("CALL p_explicit()").getRows().get(0).getValue(0));

        engine.execute("CREATE OR REPLACE PROCEDURE p_qualified() RETURNS VARCHAR LANGUAGE JAVA"
            + PACKAGES + " HANDLER = 'H2.go' AS $$\n"
            + "class H2 { public String go(com.snowflake.snowpark_java.Session s) { return \"OK\"; } }\n$$");
        assertEquals("OK", engine.executeQuery("CALL p_qualified()").getRows().get(0).getValue(0));
    }

    /** A UDF's handler class may be package-private too, so long as the method is public and static. */
    @Test
    public void aPackagePrivateFunctionHandlerClassIsAccepted() {
        engine.execute("CREATE OR REPLACE FUNCTION f_double(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
            + " RUNTIME_VERSION = 11 HANDLER = 'C.go' AS $$\n"
            + "class C { public static int go(int x) { return x * 2; } }\n$$");
        assertEquals(42, ((Number) engine.executeQuery("SELECT f_double(21) AS v")
            .getRows().get(0).getValue(0)).intValue());
    }

    /** The method itself must be public — refused at CREATE time, in live's wording. */
    @Test
    public void aPackagePrivateHandlerMethodIsRefused() {
        final RuntimeException fn = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE FUNCTION f_hidden(x INTEGER) RETURNS INTEGER"
                    + " LANGUAGE JAVA RUNTIME_VERSION = 11 HANDLER = 'C3.go' AS $$\n"
                    + "class C3 { static int go(int x) { return x + 1; } }\n$$");
            }
        });
        assertTrue(String.valueOf(fn.getMessage()).contains(
                "Failed to find a public method named \"go\" with 1 arguments"),
            fn.getMessage());

        final RuntimeException proc = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE PROCEDURE p_hidden() RETURNS VARCHAR LANGUAGE JAVA"
                    + PACKAGES + " HANDLER = 'P.go' AS $$\n"
                    + "import com.snowflake.snowpark_java.*;\n"
                    + "class P { String go(Session s) { return \"OK\"; } }\n$$");
            }
        });
        // The leading Session counts toward the arity live reports, so a no-argument procedure says "1".
        assertTrue(String.valueOf(proc.getMessage()).contains(
                "Failed to find a public method named \"go\" with 1 arguments"),
            proc.getMessage());
    }

    /** A procedure that declares parameters reports the arity including its session. */
    @Test
    public void theReportedArityCountsTheSession() {
        final RuntimeException proc = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE PROCEDURE p_two(a INTEGER, b INTEGER) RETURNS VARCHAR"
                    + " LANGUAGE JAVA" + PACKAGES + " HANDLER = 'Q.nosuch' AS $$\n"
                    + "import com.snowflake.snowpark_java.*;\n"
                    + "class Q { public String go(Session s, int a, int b) { return \"OK\"; } }\n$$");
            }
        });
        assertTrue(String.valueOf(proc.getMessage()).contains(
                "Failed to find a public method named \"nosuch\" with 3 arguments"),
            proc.getMessage());
    }
}
