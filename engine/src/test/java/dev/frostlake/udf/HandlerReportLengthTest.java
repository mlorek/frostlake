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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * A Java handler's report of more than 4096 bytes of UTF-8 is cut to its first 4093, a character never split, and
 * ended by {@code ...} before the sentence that names the function and its handler, for a function, a procedure and
 * a table function alike; a body the compiler only warns about creates its function without a word (all
 * live-verified).
 */
public class HandlerReportLengthTest extends MeasuredCellsTestSupport {

    /** The report's lines before the exception's text, joined as {@code answer} joins them. */
    private static final String HEAD = "REFUSED User Error Report:  | Java Stack Trace: | "
        + "java.lang.IllegalStateException: ";

    /** The letters a cut report keeps: its 4093 bytes less the 71 of its head. */
    private static final int KEPT = 4022;

    /** What the handlers below throw: a to z, over and over. */
    private static String letters(final int count) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append((char) ('a' + i % 26));
        }
        return text.toString();
    }

    /** A function whose handler throws n characters, each appended as {@code appended} spells it. */
    private static String throwing(final String name, final String appended) {
        return String.format(Locale.ROOT, """
            CREATE OR REPLACE FUNCTION %s(n INT) RETURNS INT LANGUAGE JAVA HANDLER = 'H.f' AS $$
            class H {
              public static int f(int n) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) {
                  b.append(%s);
                }
                throw new IllegalStateException(b.toString());
              }
            }
            $$""", name, appended);
    }

    @Test
    public void aBodyTheCompilerWarnsAboutIsCreated() {
        assertAnswer("Function JW654 successfully created.", """
            CREATE OR REPLACE FUNCTION jw654() RETURNS INT LANGUAGE JAVA HANDLER = 'H.f' AS $$
            class H {
              public static int f() {
                java.util.List l = new java.util.ArrayList();
                l.add(1);
                return l.size();
              }
            }
            $$""");
        assertAnswer("1", "SELECT jw654()");
    }

    @Test
    public void aReportPast4096BytesIsCut() {
        engine.execute(throwing("jl655", "(char) ('a' + i % 26)"));
        final String frame = " | \tat function_handler_0//H.f(InlineCode.java:8) |  in function JL655 with handler H.f";
        assertAnswer(HEAD + letters(100) + frame, "SELECT jl655(100)");
        assertAnswer(HEAD + letters(3976) + frame, "SELECT jl655(3976)");
        assertAnswer(HEAD + letters(3977) + frame, "SELECT jl655(3977)");
        assertAnswer(HEAD + letters(3978) + " | \tat function_handler_0//H.f(InlineCode.java... in function JL655 "
            + "with handler H.f", "SELECT jl655(3978)");
        assertAnswer(HEAD + letters(3979) + " | \tat function_handler_0//H.f(InlineCode.jav... in function JL655 "
            + "with handler H.f", "SELECT jl655(3979)");
        assertAnswer(HEAD + letters(KEPT) + "... in function JL655 with handler H.f", "SELECT jl655(70000)");
    }

    @Test
    public void bytesAreCountedNotCharacters() {
        engine.execute(throwing("jm655", "(char) 233"));
        final String kept = HEAD + "é".repeat(2011) + "... in function JM655 with handler H.f";
        assertAnswer(kept, "SELECT jm655(2100)");
        assertAnswer(kept, "SELECT jm655(4100)");
    }

    @Test
    public void proceduresAndTableFunctionsAreCutAlike() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE jp655(n INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = \
            ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$
            class H {
              public int run(com.snowflake.snowpark_java.Session s, int n) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) {
                  b.append((char) ('a' + i % 26));
                }
                throw new IllegalStateException(b.toString());
              }
            }
            $$""");
        assertAnswer(HEAD + letters(100) + " | \tat function_handler_0//H.run(InlineCode.java:8) |  in function JP655 "
            + "with handler H.run", "CALL jp655(100)");
        assertAnswer(HEAD + letters(KEPT) + "... in function JP655 with handler H.run", "CALL jp655(5000)");
        engine.execute("""
            CREATE OR REPLACE FUNCTION jt655(n INT) RETURNS TABLE (b INT) LANGUAGE JAVA HANDLER = 'H' AS $$
            class OutRow {
              public int b;
              public OutRow(int b) { this.b = b; }
            }
            class H {
              public static Class getOutputClass() { return OutRow.class; }
              public java.util.stream.Stream<OutRow> process(int n) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) {
                  b.append((char) ('a' + i % 26));
                }
                throw new IllegalStateException(b.toString());
              }
            }
            $$""");
        assertAnswer(HEAD + letters(100) + " | \tat function_handler_0//H.process(InlineCode.java:13) |  in function "
            + "JT655 with handler H", "SELECT * FROM TABLE(jt655(100))");
        assertAnswer(HEAD + letters(KEPT) + "... in function JT655 with handler H", "SELECT * FROM TABLE(jt655(5000))");
    }
}
