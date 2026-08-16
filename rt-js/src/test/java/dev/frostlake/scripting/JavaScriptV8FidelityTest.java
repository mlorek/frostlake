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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JavaScript handler behaves as it does on the account's V8. Its {@code Intl} reads the session's
 * TIMEZONE; V8's stack-trace API is there ({@code Error.captureStackTrace}, a {@code stackTraceLimit} of
 * 10); no script-engine globals ({@code context}, {@code engine}) leak in; an argument is bound under its
 * canonical name only, so a body that spells an unquoted name in lower case fails. An uncaught error
 * reaches SQL as {@code JavaScript execution error: Uncaught <error> in <handler> at '<line>' position
 * <column>}, then {@code stackstrace:} and the frames, innermost first. Every cell is live-verified.
 */
public class JavaScriptV8FidelityTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aHandlerReadsTheSessionZoneAndOffersV8sStackTraceApi() {
        try {
            engine.execute("CREATE OR REPLACE FUNCTION fz() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [typeof Error.captureStackTrace, String(Error.stackTraceLimit), typeof context, typeof engine, typeof Java, Intl.DateTimeFormat().resolvedOptions().timeZone].join(',');$$");
            engine.execute("ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'");
            assertEquals("function,10,undefined,undefined,undefined,Europe/Warsaw",
                rows("SELECT fz()"));
            engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
            assertEquals("function,10,undefined,undefined,undefined,Asia/Tokyo",
                rows("SELECT fz()"));
            engine.execute("CREATE OR REPLACE PROCEDURE pz() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [typeof Error.captureStackTrace, String(Error.stackTraceLimit), typeof context, typeof engine].join(',');$$");
            assertEquals("function,10,undefined,undefined",
                rows("CALL pz()"));
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void anUncaughtErrorReachesSqlInTheAccountsWords() {
        engine.execute("CREATE OR REPLACE FUNCTION fe() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return null.x;'");
        assertRefused("SELECT fe()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in FE at 'return null.x;' position 12\nstackstrace: \nFE line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION fu() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return undefined.y;'");
        assertRefused("SELECT fu()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading 'y') in FU at 'return undefined.y;' position 17\nstackstrace: \nFU line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION f1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$throw new Error('boom');$$");
        assertRefused("SELECT f1()",
                "JavaScript execution error: Uncaught Error: boom in F1 at 'throw new Error('boom');' position 0\nstackstrace: \nF1 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION f4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var a = 1;\nvar b = 2;\n  return null.x;$$");
        assertRefused("SELECT f4()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in F4 at '  return null.x;' position 14\nstackstrace: \nF4 line: 3");
        engine.execute("CREATE OR REPLACE FUNCTION f5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$throw 'str';$$");
        assertRefused("SELECT f5()",
                "JavaScript execution error: Uncaught str in F5 at 'throw 'str';' position 0\nstackstrace: \nF5 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION f6(X FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return X.y.z;$$");
        assertRefused("SELECT f6(1)",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of undefined (reading 'z') in F6 at 'return X.y.z;' position 11\nstackstrace: \nF6 line: 1");
        engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return null.x;$$");
        assertRefused("CALL p1()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in P1 at 'return null.x;' position 12\nstackstrace: \nP1 line: 1");
        engine.execute("CREATE OR REPLACE PROCEDURE p2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$throw new Error('boom');$$");
        assertRefused("CALL p2()",
                "JavaScript execution error: Uncaught Error: boom in P2 at 'throw new Error('boom');' position 0\nstackstrace: \nP2 line: 1");
        engine.execute("CREATE OR REPLACE PROCEDURE p3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$throw 'str';$$");
        assertRefused("CALL p3()",
                "JavaScript execution error: Uncaught str in P3 at 'throw 'str';' position 0\nstackstrace: \nP3 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION f7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$  return   null.x;$$");
        assertRefused("SELECT f7()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in F7 at '  return   null.x;' position 16\nstackstrace: \nF7 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION g1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$  throw new Error('boom');$$");
        assertRefused("SELECT g1()",
                "JavaScript execution error: Uncaught Error: boom in G1 at '  throw new Error('boom');' position 2\nstackstrace: \nG1 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION g6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var o = null; return o.x;$$");
        assertRefused("SELECT g6()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in G6 at 'var o = null; return o.x;' position 23\nstackstrace: \nG6 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION g7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var e = new Error('m'); e.name = 'Custom'; throw e;$$");
        assertRefused("SELECT g7()",
                "JavaScript execution error: Uncaught Custom: m in G7 at 'var e = new Error('m'); e.name = 'Custom'; throw e;' position 43\nstackstrace: \nG7 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION g8() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$function inner() { return null.q; } return inner();$$");
        assertRefused("SELECT g8()",
                "JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'q') in G8 at 'function inner() { return null.q; } return inner();' position 31\nstackstrace: \ninner line: 1\nG8 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION g9() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$throw {a: 1};$$");
        assertRefused("SELECT g9()",
                "JavaScript execution error: Uncaught #<Object> in G9 at 'throw {a: 1};' position 0\nstackstrace: \nG9 line: 1");
        engine.execute("CREATE OR REPLACE FUNCTION g10() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$undefined.y = 1; return 1;$$");
        assertRefused("SELECT g10()",
                "JavaScript execution error: Uncaught TypeError: Cannot set properties of undefined (setting 'y') in G10 at 'undefined.y = 1; return 1;' position 12\nstackstrace: \nG10 line: 1");
    }

    @Test
    public void anArgumentIsBoundUnderItsCanonicalNameOnly() {
        engine.execute("CREATE OR REPLACE PROCEDURE pa(a FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return typeof a + ',' + typeof A;$$");
        assertEquals("undefined,number",
                rows("CALL pa(1)"));
        engine.execute("CREATE OR REPLACE FUNCTION fq(\"x\" FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return typeof x + ',' + typeof X;$$");
        assertEquals("number,undefined",
                rows("SELECT fq(1)"));
        engine.execute("CREATE OR REPLACE FUNCTION fl(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return x * 2;'");
        assertRefused("SELECT fl(2)",
                "JavaScript execution error: Uncaught ReferenceError: x is not defined in FL at 'return x * 2;' position ");
        engine.execute("CREATE OR REPLACE PROCEDURE pl(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return a * 2;'");
        assertRefused("CALL pl(2)",
                "JavaScript execution error: Uncaught ReferenceError: a is not defined in PL at 'return a * 2;' position ");
    }
}
