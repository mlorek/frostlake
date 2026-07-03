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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REPEAT … UNTIL &lt;condition&gt; END REPEAT — a post-test loop executed in anonymous BEGIN…END blocks. The
 * body runs at least once, then iterates while the condition is false and stops once it becomes true.
 */
public class RepeatUntilTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private int runReturningInt(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    @Test
    public void repeatLoopsUntilConditionTrue() {
        assertEquals(5, runReturningInt("""
            BEGIN
                LET i INTEGER := 0;
                REPEAT
                    i := i + 1;
                UNTIL i >= 5
                END REPEAT;
                RETURN i;
            END;
            """));
    }

    @Test
    public void repeatRunsBodyAtLeastOnce() {
        // The UNTIL condition is already true, but a post-test loop still runs the body once.
        assertEquals(1, runReturningInt("""
            BEGIN
                LET i INTEGER := 10;
                LET runs INTEGER := 0;
                REPEAT
                    runs := runs + 1;
                UNTIL i >= 5
                END REPEAT;
                RETURN runs;
            END;
            """));
    }

    @Test
    public void repeatAccumulatesAcrossIterations() {
        assertEquals(10, runReturningInt("""
            BEGIN
                LET i INTEGER := 0;
                LET total INTEGER := 0;
                REPEAT
                    i := i + 1;
                    total := total + i;
                UNTIL i >= 4
                END REPEAT;
                RETURN total;
            END;
            """), "1+2+3+4 = 10");
    }

    @Test
    public void breakExitsRepeatLoop() {
        assertEquals(3, runReturningInt("""
            BEGIN
                LET i INTEGER := 0;
                REPEAT
                    i := i + 1;
                    IF i = 3 THEN
                        BREAK;
                    END IF;
                UNTIL i >= 100
                END REPEAT;
                RETURN i;
            END;
            """));
    }
}
