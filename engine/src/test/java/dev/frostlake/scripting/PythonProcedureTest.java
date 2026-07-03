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
import dev.frostlake.executor.udf.PythonProcedureExecutor;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.types.VariantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PythonProcedureTest {

    private static final Logger logger = LoggerFactory.getLogger(PythonProcedureTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCreatePythonProcedure() {
        logger.info("Testing CREATE PROCEDURE with LANGUAGE PYTHON");

        engine.execute("""
            CREATE PROCEDURE pysp()
            RETURNS VARIANT
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.9'
            PACKAGES = ('snowflake-snowpark-python')
            HANDLER = 'run'
            AS
            $$
def run(session):
    return "{}"
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("pysp");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("PYSP", proc.getName());
        assertEquals("PYTHON", proc.getLanguage());
        assertEquals("run", proc.getHandler());
        assertEquals("3.9", proc.getRuntimeVersion());
        assertEquals(1, proc.getPackages().size());
        assertEquals("snowflake-snowpark-python", proc.getPackages().get(0));
        assertTrue(proc.getBody().contains("def run"),
                   "Procedure body should contain handler function");
    }

    @Test
    public void testExecutePythonProcedureReturningString() {
        logger.info("Testing Python procedure execution returning string");

        List<Parameter> parameters = new ArrayList<>();

        String body = """
def run(session):
    return "{}"
""";

        List<String> packages = Arrays.asList("snowflake-snowpark-python");
        Procedure proc = new Procedure("test_pysp", parameters, VariantType.VARIANT,
                                      body, "PYTHON", "run", "3.9", packages);

        List<Object> args = Arrays.asList();
        Object result = PythonProcedureExecutor.executePythonProcedure(proc, args, engine);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals("{}", result.toString(), "Result should be '{}'");
    }

    @Test
    public void testPythonProcedureWithSessionSQL() {
        logger.info("Testing Python procedure with session.sql()");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");
        engine.execute("INSERT INTO test_table VALUES (2, 'Bob')");

        List<Parameter> parameters = new ArrayList<>();

        String body = """
def run(session):
    result = session.sql("SELECT COUNT(*) as cnt FROM test_table")
    return str(result.count())
""";

        List<String> packages = Arrays.asList("snowflake-snowpark-python");
        Procedure proc = new Procedure("test_count", parameters, VariantType.VARIANT,
                                      body, "PYTHON", "run", "3.9", packages);

        List<Object> args = Arrays.asList();
        Object result = PythonProcedureExecutor.executePythonProcedure(proc, args, engine);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals("1", result.toString(), "Result should be '1' (one row returned from count query)");
    }

    @Test
    public void testPythonProcedureWithMultiplePackages() {
        logger.info("Testing Python procedure with multiple packages");

        engine.execute("""
            CREATE PROCEDURE multi_pkg_proc()
            RETURNS VARIANT
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.9'
            PACKAGES = ('snowflake-snowpark-python', 'pandas', 'numpy')
            HANDLER = 'process'
            AS
            $$
def process(session):
    return "processed"
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("multi_pkg_proc");

        assertNotNull(proc, "Procedure should be created");
        assertEquals(3, proc.getPackages().size());
        assertTrue(proc.getPackages().contains("snowflake-snowpark-python"));
        assertTrue(proc.getPackages().contains("pandas"));
        assertTrue(proc.getPackages().contains("numpy"));
    }

    @Test
    public void testPythonProcedureReturningDict() {
        logger.info("Testing Python procedure returning dictionary");

        List<Parameter> parameters = new ArrayList<>();

        String body = """
def run(session):
    return {"status": "success", "count": 42}
""";

        List<String> packages = Arrays.asList("snowflake-snowpark-python");
        Procedure proc = new Procedure("test_dict", parameters, VariantType.VARIANT,
                                      body, "PYTHON", "run", "3.9", packages);

        List<Object> args = Arrays.asList();
        Object result = PythonProcedureExecutor.executePythonProcedure(proc, args, engine);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        logger.info("Result class: {}", result.getClass().getName());
    }

    @Test
    public void testDropPythonProcedure() {
        logger.info("Testing DROP Python procedure");

        engine.execute("""
            CREATE PROCEDURE py_test()
            RETURNS VARIANT
            LANGUAGE PYTHON
            HANDLER = 'run'
            AS $$
def run(session):
    return "test"
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNotNull(schema.getProcedure("py_test"), "Procedure should exist");

        engine.execute("DROP PROCEDURE py_test");

        RuntimeException exception = Assertions.assertThrows(
            RuntimeException.class,
            () -> schema.getProcedure("py_test")
        );
        assertTrue(exception.getMessage().contains("does not exist"));
    }
}
