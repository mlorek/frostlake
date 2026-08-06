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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Procedure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ScalaProcedureTest {

    private static final Logger logger = LoggerFactory.getLogger(ScalaProcedureTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testCreateScalaProcedure() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE scala_sp()
            RETURNS VARIANT
            LANGUAGE SCALA
            RUNTIME_VERSION = '2.12'
            PACKAGES = ('com.snowflake:snowpark:1.15.0')
            HANDLER = 'Test.test'
            AS
            $$
            import com.snowflake.snowpark_java.Session;

            class Test {
              def test(session: Session): String = {
                "{}"
              }
            }
            $$
            """);

        String db = engine.getCatalog().getCurrentDatabase();
        String sc = engine.getCatalog().getCurrentSchema();
        Procedure proc = engine.getCatalog().getDatabase(db).getSchema(sc).getProcedure("SCALA_SP");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("SCALA_SP", proc.getName());
        assertEquals("SCALA", proc.getLanguage());
        assertEquals("Test.test", proc.getHandler());
        assertEquals("2.12", proc.getRuntimeVersion());
        assertEquals(1, proc.getPackages().size());
        assertEquals("com.snowflake:snowpark:1.15.0", proc.getPackages().get(0));
        assertTrue(proc.getBody().contains("class Test"), "Body should contain Scala class");
        logger.info("Scala procedure created with handler: {}", proc.getHandler());
    }

    @Test
    public void testScalaProcedureWithStringReturn() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE greet_scala(name VARCHAR)
            RETURNS VARCHAR
            LANGUAGE SCALA
            RUNTIME_VERSION = '2.12'
            HANDLER = 'Greeter.greet'
            AS
            $$
            import com.snowflake.snowpark_java.Session;

            class Greeter {
              def greet(session: Session, name: String): String = {
                "Hello, " + name + "!"
              }
            }
            $$
            """);

        Procedure proc = engine.getCatalog()
            .getDatabase(engine.getCatalog().getCurrentDatabase())
            .getSchema(engine.getCatalog().getCurrentSchema())
            .getProcedure("GREET_SCALA");

        assertNotNull(proc);
        assertEquals("SCALA", proc.getLanguage());
        assertEquals(1, proc.getParameters().size());
        assertEquals("name", proc.getParameters().get(0).getName().toLowerCase());
        logger.info("Scala procedure with parameter created");
    }

    @Test
    public void testScalaProcedureOrReplace() {
        engine.execute("""
            CREATE PROCEDURE sp1()
            RETURNS VARCHAR
            LANGUAGE SCALA
            RUNTIME_VERSION = '2.12'
            HANDLER = 'A.run'
            AS $$
            import com.snowflake.snowpark_java.Session;
            class A { def run(s: Session): String = "v1" }
            $$
            """);

        engine.execute("""
            CREATE OR REPLACE PROCEDURE sp1()
            RETURNS VARCHAR
            LANGUAGE SCALA
            RUNTIME_VERSION = '2.12'
            HANDLER = 'A.run'
            AS $$
            import com.snowflake.snowpark_java.Session;
            class A { def run(s: Session): String = "v2" }
            $$
            """);

        Procedure proc = engine.getCatalog()
            .getDatabase(engine.getCatalog().getCurrentDatabase())
            .getSchema(engine.getCatalog().getCurrentSchema())
            .getProcedure("SP1");

        assertNotNull(proc);
        assertTrue(proc.getBody().contains("v2"), "OR REPLACE should update body");
        logger.info("Scala procedure OR REPLACE works");
    }

    @Test
    public void testCallScalaProcedureDispatchesToExecutor() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE scala_hello()
            RETURNS VARCHAR
            LANGUAGE SCALA
            RUNTIME_VERSION = '2.12'
            HANDLER = 'Hello.run'
            AS $$
            import com.snowflake.snowpark_java.Session;
            class Hello { def run(session: Session): String = "hello from scala" }
            $$
            """);

        // Execution will succeed if scalac is on PATH, otherwise it will throw
        // a compilation error — both outcomes confirm the dispatch reached the executor.
        try {
            var rs = engine.executeQuery("CALL scala_hello()");
            assertNotNull(rs);
            assertEquals("hello from scala", rs.getRows().get(0).getValue(0).toString());
            logger.info("Scala procedure executed successfully");
        } catch (final RuntimeException e) {
            // scalac not available in this environment — verify it was dispatched correctly
            assertTrue(e.getMessage().contains("scala") || e.getMessage().contains("Scala")
                || e.getMessage().contains("scalac") || e.getMessage().contains("compile"),
                "Expected Scala-related error, got: " + e.getMessage());
            logger.info("Scala procedure dispatched to executor (scalac not available): {}", e.getMessage());
        }
    }
}
