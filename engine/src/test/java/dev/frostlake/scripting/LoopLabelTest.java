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
 * Snowflake Scripting loop labels (`<label>: LOOP … END LOOP <label>;`) and labeled BREAK / CONTINUE,
 * which target a specific enclosing loop rather than the innermost. Previously any label failed to parse.
 */
public class LoopLabelTest extends BaseDatabaseTest {

    private long ret(final String block) {
        return ((Number) engine.executeQuery(block).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void labeledBreakOnSingleLoop() {
        assertEquals(5L, ret("""
            BEGIN
              LET i INTEGER := 0;
              my_loop: LOOP
                i := i + 1;
                IF (i >= 5) THEN
                  BREAK my_loop;
                END IF;
              END LOOP my_loop;
              RETURN i;
            END;"""));
    }

    @Test
    public void labeledBreakFromInnerToOuterLoop() {
        // outer increments; inner either CONTINUEs the outer (while count<5) or BREAKs the outer.
        assertEquals(5L, ret("""
            BEGIN
              LET count INTEGER := 0;
              outer_loop: WHILE (TRUE) DO
                count := count + 1;
                inner_loop: WHILE (TRUE) DO
                  IF (count < 5) THEN
                    count := count + 1;
                    CONTINUE outer_loop;
                  ELSE
                    BREAK outer_loop;
                  END IF;
                END WHILE inner_loop;
              END WHILE outer_loop;
              RETURN count;
            END;"""));
    }

    @Test
    public void unlabeledBreakBreaksInnermostOnly() {
        // Inner BREAK (unlabeled) only stops the inner loop; the outer runs its 3 iterations.
        assertEquals(3L, ret("""
            BEGIN
              LET outerCount INTEGER := 0;
              FOR i IN 1 TO 3 DO
                outerCount := outerCount + 1;
                LOOP
                  BREAK;
                END LOOP;
              END FOR;
              RETURN outerCount;
            END;"""));
    }

    @Test
    public void labeledContinueInForRange() {
        // sum only even i in 1..6 by CONTINUEing the labeled loop on odd values.
        assertEquals(12L, ret("""
            BEGIN
              LET total INTEGER := 0;
              sum_loop: FOR i IN 1 TO 6 DO
                IF (MOD(i, 2) = 1) THEN
                  CONTINUE sum_loop;
                END IF;
                total := total + i;
              END FOR sum_loop;
              RETURN total;
            END;"""));
    }
}
