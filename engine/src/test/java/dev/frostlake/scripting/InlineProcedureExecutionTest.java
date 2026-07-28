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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that inline stored PROCEDURES actually EXECUTE (not merely get created) for each language, by
 * creating a procedure and CALLing it. Parallels {@code dev.frostlake.udf.InlineUdfExecutionTest}.
 *
 * <p>Conventions: Java and Scala procedures declare a {@code HANDLER='Class.method'} whose method takes a
 * Snowpark {@code Session} first arg; JavaScript runs the body as an IIFE; Python calls the HANDLER with
 * {@code session}. (The pre-existing {@code JavaScriptProcedureTest}/{@code PythonProcedureTest} assert
 * only that the procedure is created; this asserts the {@code CALL} return value. Scala compiles
 * in-process via {@code ScalaCompiler}, so no external {@code scalac} is required.)
 */
public class InlineProcedureExecutionTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(InlineProcedureExecutionTest.class);

    @Test
    public void inlineJavaScriptProcedureExecutes() {
        logger.info("inline JavaScript procedure (GraalVM JS)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE js_proc()
            RETURNS STRING
            LANGUAGE JAVASCRIPT
            AS $$
                return "js-proc";
            $$
            """);
        assertEquals("js-proc", callScalar("CALL js_proc()"));
    }

    @Test
    public void inlineJavaProcedureExecutes() {
        logger.info("inline Java procedure");
        // A Java procedure handler MUST take a Snowpark Session as its first argument (the executor
        // injects it; the declared procedure params follow). Use it — run a query through the session —
        // so this verifies the injected Session is functional, not just present in the signature.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE java_proc()
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'P.run'
            AS $$
            import com.snowflake.snowpark_java.Session;
            public class P {
              public String run(Session session) {
                Object[] rows = session.sql("SELECT 'java-proc' AS v").collect();
                return ((dev.frostlake.storage.Row) rows[0]).getValue(0).toString();
              }
            }
            $$
            """);
        assertEquals("java-proc", callScalar("CALL java_proc()"));
    }

    @Test
    public void inlinePythonProcedureExecutes() {
        logger.info("inline Python procedure (Jython)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE py_proc()
            RETURNS STRING
            LANGUAGE PYTHON
            HANDLER = 'run'
            AS $$
            def run(session):
                return 'py-proc'
            $$
            """);
        assertEquals("py-proc", callScalar("CALL py_proc()"));
    }

    @Test
    public void inlineScalaProcedureExecutes() {
        logger.info("inline Scala procedure (in-process scala-compiler)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE scala_proc()
            RETURNS STRING
            LANGUAGE SCALA
            HANDLER = 'P.run'
            AS $$
            import com.snowflake.snowpark_java.Session
            class P { def run(session: Session): String = "scala-proc" }
            $$
            """);
        assertEquals("scala-proc", callScalar("CALL scala_proc()"));
    }

    @Test
    public void procReturningFunctionCallWithTimestampArg() {
        // A scripting proc evaluates a function call by re-building "SELECT fn(arg,...)" from the evaluated
        // argument VALUES. A non-numeric, non-boolean value (a timestamp) used to be appended RAW —
        // OBJECT_CONSTRUCT_KEEP_NULL('t', CURRENT_TIMESTAMP()) became SELECT ...('t', 2026-07-24T08:..) →
        // "SQL syntax error". Such values must be emitted as quoted string literals.
        logger.info("procedural function call with a timestamp (non-numeric) argument");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_ts() RETURNS OBJECT LANGUAGE SQL AS $$
            BEGIN
                RETURN OBJECT_CONSTRUCT_KEEP_NULL('rule_id', 'EXT-002', 'seen_at', CURRENT_TIMESTAMP());
            END $$""");
        final String result = callScalar("CALL p_ts()");
        assertEquals(true, result.contains("\"rule_id\":\"EXT-002\""), result);
        assertEquals(true, result.contains("\"seen_at\":"), result);
    }

    @Test
    public void procReturningFunctionCallWithDateColumnValue() {
        // The real loader shape: a timestamp/date COLUMN value threaded into OBJECT_CONSTRUCT_KEEP_NULL
        // inside a procedure.
        logger.info("procedural function call with a date column value");
        engine.execute("CREATE TABLE ts_src (d DATE)");
        engine.execute("INSERT INTO ts_src VALUES ('2023-08-24')");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_dcol() RETURNS OBJECT LANGUAGE SQL AS $$
            DECLARE v DATE;
            BEGIN
                SELECT d INTO :v FROM ts_src;
                RETURN OBJECT_CONSTRUCT_KEEP_NULL('license_expires_at', :v, 'license_days', -1);
            END $$""");
        final String result = callScalar("CALL p_dcol()");
        assertEquals(true, result.contains("\"license_expires_at\":\"2023-08-24\""), result);
        assertEquals(true, result.contains("\"license_days\":-1"), result);
    }

    @Test
    public void procFunctionCallWithBackslashStringValue() {
        // A string value containing a backslash (e.g. a nested error message ending in '\', or a "\'"
        // sequence) must round-trip through the procedural re-build. frostlake's lexer treats '\' as an
        // escape, so an un-escaped backslash used to derail the rebuilt SELECT ("no viable alternative").
        logger.info("procedural function call with a backslash-bearing string value");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_bs() RETURNS OBJECT LANGUAGE SQL AS $$
            DECLARE msg VARCHAR;
            BEGIN
                msg := 'path' || CHR(92);
                RETURN OBJECT_CONSTRUCT_KEEP_NULL('m', :msg, 'k', 1);
            END $$""");
        final String result = callScalar("CALL p_bs()");
        assertEquals(true, result.contains("\"k\":1"), result);
        assertEquals(true, result.contains("path"), result);
    }

    /** Execute a CALL and return its single scalar result as a String. */
    private String callScalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0).toString();
    }
}
