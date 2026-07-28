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
import dev.frostlake.executor.udf.JavaScriptProcedureExecutor;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaScriptProcedureTest {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptProcedureTest.class);
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
    public void testCreateJavaScriptProcedure() {
        logger.info("Testing CREATE PROCEDURE with LANGUAGE JAVASCRIPT");

        engine.execute("""
            CREATE PROCEDURE js_add_proc(a INTEGER, b INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS $$
                return a + b;
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("js_add_proc");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("JS_ADD_PROC", proc.getName());
        assertEquals("JAVASCRIPT", proc.getLanguage());
        assertEquals(2, proc.getParameters().size());
    }

    @Test
    public void testJavaScriptProcedureWithSnowflakeExecute() {
        logger.info("Testing JavaScript procedure with snowflake.execute()");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("dummy", StringType.VARCHAR));

        String body = """
            var cmd = "SELECT 42 as result";
            var rs = snowflake.execute({sqlText: cmd});
            if (rs.next()) {
                return rs.getColumnValue(1);
            }
            return null;
            """;

        Procedure proc = new Procedure("test_proc", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList("test"),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals(42L, ((Number) result).longValue());
    }

    @Test
    public void testSnowflakeCreateStatementExecute() {
        logger.info("Testing the standard snowflake.createStatement().execute() API");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("dummy", StringType.VARCHAR));

        // The two-step form real Snowflake JS procedures use: createStatement -> execute; the statement
        // also exposes column metadata (count/name), as ADD_DML reads it.
        final String body = """
            var stmt = snowflake.createStatement({sqlText: "SELECT 7 AS num"});
            var rs = stmt.execute();
            rs.next();
            return "" + rs.getColumnValue(1) + "|" + stmt.getColumnCount() + "|" + stmt.getColumnName(1);
            """;

        final Procedure proc = new Procedure("cs_proc", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");
        final Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc, Arrays.asList("x"), engine);

        assertEquals("7|1|NUM", String.valueOf(result));
    }

    @Test
    public void testSnowflakeCreateStatementWithBinds() {
        logger.info("Testing snowflake.createStatement with positional ? binds");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("dummy", StringType.VARCHAR));

        final String body = """
            var stmt = snowflake.createStatement({sqlText: "SELECT ? + ? AS total", binds: [40, 2]});
            var rs = stmt.execute();
            rs.next();
            return rs.getColumnValue(1);
            """;

        final Procedure proc = new Procedure("bind_proc", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");
        final Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc, Arrays.asList("x"), engine);

        assertEquals(42L, ((Number) result).longValue());
    }

    @Test
    public void testObjectParamSurvivesNestedCreateStatement() {
        logger.info("An OBJECT param stays live across a nested createStatement().execute() (the ADD_DML shape)");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("so", ObjectType.OBJECT));

        // Mirrors ADD_DML: a nested query runs, then the OBJECT param is mutated — it must not be clobbered.
        final String body = """
            try {
                var stmt = snowflake.createStatement({sqlText: "SELECT 1 AS a"});
                var rs = stmt.execute();
                rs.next();
                so.dml_stats.copy.push({rows: rs.getColumnValue(1)});
                return so;
            } catch (ex) {
                so.dml_error = {err: ex.toString()};
                return so;
            }
            """;

        final Procedure proc = new Procedure("add_dml_like", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");
        final Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc, Arrays.asList("{\"dml_stats\": {\"copy\": []}}"), engine);

        final String json = String.valueOf(result);
        assertTrue(json.contains("\"rows\"") && !json.contains("dml_error"),
            "the nested query result should be pushed onto the live object, with no error: " + json);
    }

    @Test
    public void testJavaScriptProcedureReturnObject() {
        logger.info("Testing JavaScript procedure returning object");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("s", StringType.VARCHAR));

        String body = """
            const obj = {
                p: null
            };
            try {
                var cmd = "SELECT 'a' as c";
                var rs = snowflake.execute({sqlText: cmd});
                if (rs.next()) {
                    obj.rc = rs.getColumnValue(1);
                }
                obj.p = 1;
            } catch (err) {
                obj.msg = err.message;
                obj.f = true;
            } finally {
                obj.p2 = 2;
                return obj;
            }
            """;

        Procedure proc = new Procedure("p1", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList("test_string"),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result type: {}", result.getClass().getName());
        logger.info("Result: {}", result);

        assertTrue(result instanceof Map || result.toString().contains("rc"),
                   "Result should be an object/map");
    }

    @Test
    public void testJavaScriptProcedureWithErrorHandling() {
        logger.info("Testing JavaScript procedure with try-catch-finally");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("input", StringType.VARCHAR));

        String body = """
            const result = {
                success: false,
                error: null,
                data: null
            };
            try {
                var cmd = "SELECT '" + input + "' as value";
                var rs = snowflake.execute({sqlText: cmd});
                if (rs.next()) {
                    result.data = rs.getColumnValue(1);
                    result.success = true;
                }
            } catch (err) {
                result.error = err.message;
            } finally {
                return result;
            }
            """;

        Procedure proc = new Procedure("test_error", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList("hello"),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
    }

    @Test
    public void testJavaScriptProcedureGetColumnByName() {
        logger.info("Testing JavaScript procedure with getColumnValue by name");

        List<Parameter> parameters = new ArrayList<>();

        String body = """
            var cmd = "SELECT 'John' as name, 30 as age";
            var rs = snowflake.execute({sqlText: cmd});
            const result = {};
            if (rs.next()) {
                result.name = rs.getColumnValue('name');
                result.age = rs.getColumnValue('age');
            }
            return result;
            """;

        Procedure proc = new Procedure("test_col_name", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList(),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
    }

    @Test
    public void testJavaScriptProcedureMultipleRows() {
        logger.info("Testing JavaScript procedure with multiple rows");

        engine.execute("CREATE TABLE test_data (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO test_data VALUES (1, 'A'), (2, 'B'), (3, 'C')");

        List<Parameter> parameters = new ArrayList<>();

        String body = """
            var cmd = "SELECT * FROM test_data ORDER BY id";
            var rs = snowflake.execute({sqlText: cmd});
            const items = [];
            while (rs.next()) {
                items.push({
                    id: rs.getColumnValue(1),
                    value: rs.getColumnValue(2)
                });
            }
            return {count: items.length, items: items};
            """;

        Procedure proc = new Procedure("test_multi_row", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList(),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
    }

    @Test
    public void testUserProvidedProcedure() {
        logger.info("Testing user-provided procedure p1");

        engine.execute("""
            CREATE PROCEDURE public.p1 (s STRING)
            RETURNS OBJECT
            LANGUAGE JAVASCRIPT
            AS
            $$
              const obj = {
                p: null
              };
              try{
                var cmd = "SELECT 'a' as c";
                var rs = snowflake.execute({sqlText: cmd});
                if(rs.next()){
                  obj.rc = rs.getColumnValue(1);
                }
                obj.p = 1;
              }
              catch(err){
                obj.msg = err.message;
                obj.f = true;
              }
              finally{
                obj.p2 = 2;
                return obj;
              }
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure proc = schema.getProcedure("p1");

        assertNotNull(proc, "Procedure should be created");
        assertEquals("P1", proc.getName());
        assertEquals("JAVASCRIPT", proc.getLanguage());

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList("test"),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result from p1: {}", result);
    }

    @Test
    public void testJavaScriptProcedureDynamicSQL() {
        logger.info("Testing JavaScript procedure with dynamic SQL");

        engine.execute("CREATE TABLE numbers (n INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (1), (2), (3), (4), (5)");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("min_value", StringType.VARCHAR));

        String body = """
            var cmd = "SELECT SUM(n) as total FROM numbers WHERE n > " + min_value;
            var rs = snowflake.execute({sqlText: cmd});
            if (rs.next()) {
                return {total: rs.getColumnValue(1), sql: cmd};
            }
            return null;
            """;

        Procedure proc = new Procedure("sum_proc", parameters, ObjectType.OBJECT, body, "JAVASCRIPT");

        Object result = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
            proc,
            Arrays.asList("2"),
            engine
        );

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
    }

    @Test
    public void testCompareWithSQLProcedure() {
        logger.info("Testing comparison between SQL and JavaScript procedures");

        engine.execute("""
            CREATE PROCEDURE sql_proc(x INTEGER)
            RETURNS INTEGER
            LANGUAGE SQL
            AS $$
                BEGIN
                    RETURN x * 2;
                END;
            $$
            """);

        engine.execute("""
            CREATE PROCEDURE js_proc(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS $$
                return x * 2;
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Procedure sqlProc = schema.getProcedure("sql_proc");
        Procedure jsProc = schema.getProcedure("js_proc");

        assertEquals("SQL", sqlProc.getLanguage());
        assertEquals("JAVASCRIPT", jsProc.getLanguage());
    }

    @Test
    public void testObjectParameterIsNativeJsObject() {
        // Snowflake exposes an OBJECT argument as a native JS object, so the body can set/read properties.
        // (Regression: Frostlake used to bind it as a JSON string, so `so.x = 1` threw / was lost.)
        engine.execute("""
            CREATE OR REPLACE PROCEDURE js_obj_prop("SO" OBJECT)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                SO.added = 9;
                return (typeof SO) + ':' + SO.a + ':' + SO.added;
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL js_obj_prop(OBJECT_CONSTRUCT('a', 1))");
        assertEquals("object:1:9", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testObjectReturnRoundTripsAsJson() {
        // A JS object returned from a procedure must come back as a Frostlake OBJECT (valid JSON), so a
        // caller can read its fields with the variant-path operator.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE js_ret_obj("SO" OBJECT)
            RETURNS OBJECT
            LANGUAGE JAVASCRIPT
            AS $$
                SO.extra = 5;
                return SO;
            $$
            """);
        engine.execute("""
            CREATE OR REPLACE PROCEDURE js_ret_caller()
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$
            DECLARE res OBJECT;
            BEGIN
                res := (CALL js_ret_obj(OBJECT_CONSTRUCT('a', 1)));
                RETURN res:extra::VARCHAR || '/' || res:a::VARCHAR;
            END
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL js_ret_caller()");
        assertEquals("5/1", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testArrayParameterSupportsPush() {
        // An ARRAY argument is a native JS array (so .push works); the returned array round-trips as JSON.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE js_arr_push("A" ARRAY)
            RETURNS ARRAY
            LANGUAGE JAVASCRIPT
            AS $$
                A.push(99);
                return A;
            $$
            """);
        final ResultSet rs = engine.executeQuery("CALL js_arr_push(ARRAY_CONSTRUCT(1, 2))");
        final String out = String.valueOf(rs.getRows().get(0).getValue(0)).replaceAll("\\s", "");
        assertEquals("[1,2,99]", out);
    }
}
