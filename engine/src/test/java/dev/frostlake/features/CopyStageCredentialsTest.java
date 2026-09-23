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

package dev.frostlake.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A COPY that loads from an INTERNAL stage takes no credentials: the statement is refused while it
 * compiles, before any file is looked for, naming the user or table stage as a pair and a named stage on
 * its own. An unload TO a stage keeps them, and so does everything else the load writes. Frostlake read
 * the credentials as any other option group and went looking for the files (live-verified).
 */
public class CopyStageCredentialsTest extends BaseDatabaseTest {

    private static final String CREDENTIALS = "CREDENTIALS = (AWS_KEY_ID='id' AWS_SECRET_KEY='key')";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE load1 (a VARCHAR)");
        engine.execute("CREATE STAGE st1");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aLoadFromAUserOrTableStageRefusesCredentials() {
        assertEquals("SQL compilation error: \n COPY statements targeting a user or table stage"
            + " do not support credential properties.",
            refusal("COPY INTO load1 FROM @%load1/data1/ " + CREDENTIALS + " FILES = ('t1.csv')"));
        assertEquals("SQL compilation error: \n COPY statements targeting a user or table stage"
            + " do not support credential properties.",
            refusal("COPY INTO load1 FROM @~/data1/ " + CREDENTIALS + " FILES = ('t1.csv')"));
        // A named internal stage is named on its own.
        assertEquals("SQL compilation error: \n COPY statements targeting a stage"
            + " do not support credential properties.",
            refusal("COPY INTO load1 FROM @st1/data1/ " + CREDENTIALS + " FILES = ('t1.csv')"));
        // It is refused before any file is looked for: no FILES clause is needed.
        assertEquals("SQL compilation error: \n COPY statements targeting a stage"
            + " do not support credential properties.",
            refusal("COPY INTO load1 FROM @st1/data1/ " + CREDENTIALS));
    }

    @Test
    public void whatKeepsItsCredentialsOrItsOwnRefusal() {
        // ENCRYPTION is not a credential property: the load goes looking for the file.
        assertTrue(refusal("COPY INTO load1 FROM @%load1/data1/ ENCRYPTION = (TYPE='AWS_SSE_S3')"
            + " FILES = ('t1.csv')").contains("was not found"));
        assertTrue(refusal("COPY INTO load1 FROM @~/data1/ ENCRYPTION = (TYPE='AWS_SSE_S3')"
            + " FILES = ('t1.csv')").contains("was not found"));
        // Without credentials the load is unchanged.
        assertTrue(refusal("COPY INTO load1 FROM @%load1/data1/ FILES = ('t1.csv')").contains("was not found"));
        // An UNLOAD to a table stage takes them.
        engine.execute("COPY INTO @%load1/out/ FROM load1 " + CREDENTIALS);
    }
}
