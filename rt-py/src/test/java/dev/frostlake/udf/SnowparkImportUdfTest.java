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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A scalar Python UDF whose MODULE LEVEL imports {@code snowflake.snowpark} — the shape real schemas
 * use for typed helpers. The snowpark shim must be present for the CREATE-time body compile and for
 * execution alike; without the compile-time install, every such function was refused at CREATE with
 * ModuleNotFoundError while the identical import in a procedure body worked.
 */
public class SnowparkImportUdfTest extends BaseDatabaseTest {

    @Test
    public void moduleLevelSnowparkImportResolves() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION spudf(x VARCHAR)
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.10'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.types import *

            def main(x):
                return "ok:" + x
            $$""");
        assertEquals("ok:a", engine.executeQuery("SELECT spudf('a')")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void procedureControl() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE spproc()
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION='3.10'
            HANDLER='main'
            AS $$
            from snowflake.snowpark.types import *

            def main(session):
                return "proc-ok"
            $$""");
        assertEquals("proc-ok", engine.executeQuery("CALL spproc()")
            .getRows().get(0).getValue(0));
    }
}
