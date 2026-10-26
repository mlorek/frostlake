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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Python procedure a CALL defines for itself runs as a named one does (live-verified): with the Snowpark package
 * it answers, and without it the CALL is refused as a procedure created without it is, the procedure named as the
 * CALL defines it.
 */
public class AnonymousPythonProcedureTest extends BaseDatabaseTest {

    private static final String DEFINITION = """
        WITH ap AS PROCEDURE () RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
          %s HANDLER = 'main'
          AS $$
        def main(session):
            return 'anon'
        $$
        CALL ap()""";

    @Test
    public void aCallRunsThePythonProcedureItDefines() {
        assertEquals("anon", String.valueOf(engine.executeQuery(String.format(DEFINITION,
            "PACKAGES = ('snowflake-snowpark-python')")).getRows().get(0).getValue(0)));
    }

    @Test
    public void withoutTheSnowparkPackageTheCallIsRefused() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(String.format(DEFINITION, ""));
            }
        });
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named 'snowflake.snowpark' in "
            + "function AP with handler main", refused.getMessage());
    }
}
