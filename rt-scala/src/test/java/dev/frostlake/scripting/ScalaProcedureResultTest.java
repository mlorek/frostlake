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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a CALL of a Scala procedure answers: as a Java procedure's, one row in a column named after the procedure
 * and typed by its RETURNS — a NULL when the handler hands back none — holding the handler's value as that type,
 * a {@code BigDecimal} rounded half to even to the declared scale and a number the declared width cannot hold
 * refused. The handlers here compile against this engine's own session class, so the class runs on the embedded
 * engine alone.
 */
public class ScalaProcedureResultTest {

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
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void scala(final String name, final String returns, final String scalaType, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS " + returns + " LANGUAGE SCALA "
            + "RUNTIME_VERSION = '2.12' HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.Session; "
            + "object H { def run(s: Session): " + scalaType + " = " + body + " } $$");
    }

    @Test
    public void theAnswerIsTypedByReturns() {
        scala("sn", "NUMBER", "Long", "7L");
        final ResultSet whole = engine.executeQuery("CALL sn()");
        assertEquals("SN", whole.getColumns().get(0).getName());
        assertEquals("NUMBER", whole.getColumns().get(0).getDataType().getName());
        assertEquals("7", String.valueOf(whole.getRows().get(0).getValue(0)));
        scala("sd", "NUMBER(10,2)", "java.math.BigDecimal", "new java.math.BigDecimal(\"1.385\")");
        assertEquals("1.38", String.valueOf(engine.executeQuery("CALL sd()").getRows().get(0).getValue(0)));
        scala("st", "VARCHAR", "String", "\"abc\"");
        assertEquals("abc", String.valueOf(engine.executeQuery("CALL st()").getRows().get(0).getValue(0)));
    }

    @Test
    public void aNullIsARowOfTheDeclaredType() {
        scala("sv", "VARCHAR", "String", "null");
        final ResultSet none = engine.executeQuery("CALL sv()");
        assertEquals("SV", none.getColumns().get(0).getName());
        assertEquals(1, none.getRows().size());
        assertNull(none.getRows().get(0).getValue(0));
    }

    @Test
    public void aNumberTheDeclaredWidthCannotHoldIsRefused() {
        scala("sw", "NUMBER(3,0)", "Long", "1000L");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL sw()");
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("Java value cannot be losslessly represented as a "
            + "Snowflake NUMBER with a precision of 3, value: 1000 in function SW with handler H.run"),
            refused.getMessage());
    }
}
