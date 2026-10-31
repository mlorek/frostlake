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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A pipe's COPY is read as the COPY statement it is when the pipe is created: a fault in it refuses the pipe at its
 * place in the statement, as live refuses it, where the pipe used to be created. Engine-only: the suite sends no
 * COPY INTO to the live account.
 */
public class PipeCopyBodySyntaxTest extends BaseDatabaseTest {

    @Test
    public void aFaultInThePipesCopyRefusesThePipe() {
        assumeFalse(LiveSnowflake.enabled(), "the suite sends no COPY INTO to the live account");
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        engine.execute("CREATE OR REPLACE STAGE cs");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE PIPE pp AS COPY INTO ct FROM @cs FILE_FORMAT = (, TYPE = CSV)");
            }
        });
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 66 unexpected ','.\n"
            + "syntax error line 1 at position 78 unexpected ')'.", refused.getMessage());
        final RuntimeException onItsLine = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE PIPE pp AS\nCOPY INTO ct FROM @cs FILE_FORMAT = (, TYPE = CSV)");
            }
        });
        assertEquals("SQL compilation error:\nsyntax error line 2 at position 37 unexpected ','.\n"
            + "syntax error line 2 at position 49 unexpected ')'.", onItsLine.getMessage());
    }

    @Test
    public void aWellFormedCopyStillMakesThePipe() {
        assumeFalse(LiveSnowflake.enabled(), "the suite sends no COPY INTO to the live account");
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        engine.execute("CREATE OR REPLACE STAGE cs");
        assertEquals("Pipe PP successfully created.", engine.executeQuery(
            "CREATE OR REPLACE PIPE pp AS COPY INTO ct FROM @cs FILE_FORMAT = (TYPE = CSV)").getRows().get(0)
            .getValues().get(0));
        assertEquals("Pipe PQ successfully created.", engine.executeQuery(
            "CREATE OR REPLACE PIPE pq AS COPY INTO ct FROM (SELECT $1 FROM @cs) FILE_FORMAT = (TYPE = CSV)")
            .getRows().get(0).getValues().get(0));
    }
}
