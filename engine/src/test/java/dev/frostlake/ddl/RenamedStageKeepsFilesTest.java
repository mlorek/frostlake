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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An internal stage renamed, a temporary one among them, takes its files with it: the new name lists them, and a
 * stage created later under the old name starts empty.
 */
public class RenamedStageKeepsFilesTest extends BaseDatabaseTest {

    private String listed(final String stage) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("LIST @" + stage).getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void theFilesGoWithTheNewName() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the unload's file names are the engine's own");
        engine.execute("CREATE STAGE st1");
        engine.execute("COPY INTO @st1/d.csv FROM (SELECT 1 AS a) SINGLE = TRUE");
        engine.execute("ALTER STAGE st1 RENAME TO st2");
        assertEquals("st2/d.csv", listed("st2"));
        engine.execute("CREATE STAGE st1");
        assertEquals("", listed("st1"));
        engine.execute("CREATE TEMPORARY STAGE ts1");
        engine.execute("COPY INTO @ts1/e.csv FROM (SELECT 1 AS a) SINGLE = TRUE");
        engine.execute("ALTER STAGE ts1 RENAME TO ts2");
        assertEquals("ts2/e.csv", listed("ts2"));
    }
}
