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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaProcedureTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(JavaProcedureTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE SCHEMA io");
        engine.execute("USE SCHEMA io");
    }

    /**
     * Mirrors the user-provided example exactly (using schema io).
     * The CALL uses the simple name because callStatement grammar uses identifier, not qualifiedName.
     */
    @Test
    public void testVariantTestProcedure() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE io.vt()
            RETURNS OBJECT
            LANGUAGE JAVA
            PACKAGES=('com.snowflake:snowpark:1.9.0')
            HANDLER='VariantTest.test'
            AS
            $$
            import java.util.HashMap;
            import java.util.Map;
            import com.snowflake.snowpark_java.types.Variant;
            import com.snowflake.snowpark_java.Session;

            public class VariantTest {
              public String test(Session session) throws Exception {
                Map<String, Map<String, Object>> m = new HashMap<>();
                Map<String, Object> m2 = new HashMap<>();
                m2.put("c", 1);
                m.put("b", m2);
                Variant v = new Variant("{\\\"a\\\":1}");
                v = new Variant(m);
                return v.toString();
              }
            }
            $$
            """);

        final ResultSet result = engine.executeQuery("CALL vt()");
        assertNotNull(result);
        assertTrue(result.getRowCount() > 0, "Expected result rows, got 0");
        final String value = result.getRows().get(0).getValue(0).toString();
        logger.info("Variant result: {}", value);
        assertTrue(value.contains("b"), "Expected variant to contain key 'b', got: " + value);
        assertTrue(value.contains("c"), "Expected variant to contain key 'c', got: " + value);
    }

    @Test
    public void testJavaProcedureWithSessionSql() {
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'apple'), (2, 'banana')");
        engine.execute("USE SCHEMA io");

        engine.execute("""
            CREATE OR REPLACE PROCEDURE count_items()
            RETURNS VARCHAR
            LANGUAGE JAVA
            PACKAGES=('com.snowflake:snowpark:1.9.0')
            HANDLER='Counter.run'
            AS
            $$
            import com.snowflake.snowpark_java.DataFrame;
            import com.snowflake.snowpark_java.Row;
            import com.snowflake.snowpark_java.Session;

            public class Counter {
              public String run(Session session) {
                DataFrame df = session.sql("SELECT COUNT(*) AS CNT FROM public.items");
                Row[] rows = df.collect();
                if (rows.length > 0) {
                  return "count=" + rows[0].getLong(0);
                }
                return "count=0";
              }
            }
            $$
            """);

        final ResultSet result = engine.executeQuery("CALL count_items()");
        assertNotNull(result);
        assertTrue(result.getRowCount() > 0, "Expected result rows, got 0");
        final String value = result.getRows().get(0).getValue(0).toString();
        logger.info("Count result: {}", value);
        assertTrue(value.startsWith("count="), "Expected result starting with 'count=', got: " + value);
    }

    @Test
    public void testJavaProcedureWithStringParameter() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE greet(name VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVA
            PACKAGES=('com.snowflake:snowpark:1.9.0')
            HANDLER='Greeter.greet'
            AS
            $$
            import com.snowflake.snowpark_java.Session;

            public class Greeter {
              public String greet(Session session, String name) {
                return "Hello, " + name + "!";
              }
            }
            $$
            """);

        final ResultSet result = engine.executeQuery("CALL greet('World')");
        assertNotNull(result);
        assertTrue(result.getRowCount() > 0, "Expected result rows, got 0");
        final String value = result.getRows().get(0).getValue(0).toString();
        logger.info("Greet result: {}", value);
        assertEquals("Hello, World!", value);
    }
}
