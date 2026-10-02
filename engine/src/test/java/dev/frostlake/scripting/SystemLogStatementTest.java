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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** SYSTEM$LOG and SYSTEM$LOG_<level>: callable as functions, and standing as statements inside a block. */
public class SystemLogStatementTest extends BaseDatabaseTest {

    @Test
    public void levelFunctionsReturnNull() {
        for (final String level : new String[] {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"}) {
            assertNull(engine.executeQuery("SELECT SYSTEM$LOG_" + level + "('m')").getRows().get(0).getValue(0), level);
        }
    }

    @Test
    public void logCallsStandAsStatementsAndReadVariables() {
        final Object result = engine.executeQuery("EXECUTE IMMEDIATE $$\n"
            + "DECLARE\n  stats OBJECT DEFAULT OBJECT_CONSTRUCT('k', 1);\n"
            + "BEGIN\n"
            + "  SYSTEM$LOG_INFO(OBJECT_CONSTRUCT('stats', stats));\n"
            + "  SYSTEM$LOG('warn', 'plain');\n"
            + "  RETURN 'ok';\n"
            + "END;\n$$").getRows().get(0).getValue(0);

        assertEquals("ok", String.valueOf(result));
    }

    @Test
    public void otherFunctionsStillCannotStandAsStatements() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE $$ BEGIN UPPER('x'); END; $$");
            }
        });
    }
}
