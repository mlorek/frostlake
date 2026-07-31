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

package dev.frostlake.rt.scala;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inline {@code LANGUAGE SCALA} execution — an {@code AS $$ … $$} body compiled by the in-process
 * {@link ScalaCompiler}: a UDF handler on an {@code object}, and a procedure handler class taking a
 * Snowpark {@code Session}. The Java/JavaScript/Python counterparts live with their own runtimes
 * (engine, rt-js, rt-py).
 */
public class InlineScalaExecutionTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(InlineScalaExecutionTest.class);

    @Test
    public void inlineScalaUdfExecutes() {
        logger.info("inline Scala UDF (in-process scala-compiler)");
        engine.execute("""
            CREATE OR REPLACE FUNCTION sc_double(x INTEGER)
            RETURNS INTEGER
            LANGUAGE SCALA
            HANDLER = 'M.f'
            AS $$
            object M { def f(x: Int): Int = x * 2 }
            $$
            """);
        assertEquals("42", scalar("SELECT sc_double(21)"));
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
        assertEquals("scala-proc", scalar("CALL scala_proc()"));
    }

    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0).toString();
    }
}
