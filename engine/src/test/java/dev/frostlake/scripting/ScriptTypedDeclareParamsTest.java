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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Snowflake Scripting variable may be DECLAREd / LET with a parametrized type such as
 * {@code NUMBER(38,2)} or {@code VARCHAR(20)}. Previously the DECLARE / LET grammar accepted only a bare
 * type name, so the precision/scale/length parenthesis failed to parse.
 */
public class ScriptTypedDeclareParamsTest extends BaseDatabaseTest {

    private Object ret(final String block) {
        return engine.executeQuery(block).getRows().get(0).getValue(0);
    }

    @Test
    public void declareNumberWithPrecisionAndScale() {
        assertEquals(0.0, ((Number) ret("""
            BEGIN
              DECLARE profit NUMBER(38, 2) DEFAULT 0.0;
              RETURN profit;
            END;""")).doubleValue(), 1e-9);
    }

    @Test
    public void letNumberWithPrecisionAndScale() {
        assertEquals(12.5, ((Number) ret("""
            BEGIN
              LET p NUMBER(38, 2) := 12.5;
              RETURN p;
            END;""")).doubleValue(), 1e-9);
    }

    @Test
    public void declareNumberScaleCoercesAssignedValue() {
        // NUMBER(10,2) rounds an assigned value to scale 2.
        assertEquals(3.14, ((Number) ret("""
            BEGIN
              DECLARE amt NUMBER(10, 2);
              amt := 3.14159;
              RETURN amt;
            END;""")).doubleValue(), 1e-9);
    }

    @Test
    public void declareVarcharWithLength() {
        assertEquals("Alice", String.valueOf(ret("""
            BEGIN
              DECLARE name VARCHAR(20) := 'Alice';
              RETURN name;
            END;""")));
    }
}
