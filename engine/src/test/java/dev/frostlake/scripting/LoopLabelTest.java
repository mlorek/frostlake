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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Snowflake Scripting loop labels. The label is declared by the TRAILING label —
 * {@code END LOOP my_loop} / {@code END FOR sum_loop} / {@code END WHILE w} — and referenced by
 * {@code BREAK <label>} / {@code CONTINUE <label>}, which propagate outward through nested loops.
 *
 * <p>All live-verified. The proof that the labelled forms are real syntax is that a {@code BREAK} naming
 * no trailing label fails SEMANTICALLY there — {@code Label 'MY_LOOP' not found} — rather than as a
 * syntax error. The one spelling Snowflake does NOT have is the LEADING declaration {@code my_loop:},
 * which stops at the {@code ':'}.
 */
public class LoopLabelTest extends BaseDatabaseTest {

    private long ret(final String block) {
        return ((Number) engine.executeQuery(block).getRows().get(0).getValue(0)).longValue();
    }

    private void assertRejected(final String block) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(block);
            }
        });
    }

    @Test
    public void leadingLabelDeclarationIsRejected() {
        // `my_loop: LOOP` — a real account stops at the ':'. Only the trailing form declares a label.
        assertRejected("""
            BEGIN
              LET i INTEGER := 0;
              my_loop: LOOP
                i := i + 1;
                IF (i >= 5) THEN
                  BREAK;
                END IF;
              END LOOP;
              RETURN i;
            END;""");
    }

    @Test
    public void trailingLabelOnALoopIsAccepted() {
        assertEquals(5L, ret("""
            BEGIN
              LET i INTEGER := 0;
              LOOP
                i := i + 1;
                IF (i >= 5) THEN
                  BREAK my_loop;
                END IF;
              END LOOP my_loop;
              RETURN i;
            END;"""));
    }

    @Test
    public void trailingLabelOnAForLoopDrivesLabelledContinue() {
        // Skips the odd values, summing 2 + 4 + 6.
        assertEquals(12L, ret("""
            BEGIN
              LET total INTEGER := 0;
              FOR i IN 1 TO 6 DO
                IF (MOD(i, 2) = 1) THEN
                  CONTINUE sum_loop;
                END IF;
                total := total + i;
              END FOR sum_loop;
              RETURN total;
            END;"""));
    }

    @Test
    public void trailingLabelNeedsNoReference() {
        assertEquals(6L, ret("""
            BEGIN
              LET total INTEGER := 0;
              FOR i IN 1 TO 3 DO
                total := total + i;
              END FOR any_name;
              RETURN total;
            END;"""));
    }

    @Test
    public void trailingLabelOnAWhileLoopIsAccepted() {
        assertEquals(3L, ret("""
            BEGIN
              LET i INTEGER := 0;
              WHILE (i < 3) DO
                i := i + 1;
              END WHILE w_loop;
              RETURN i;
            END;"""));
    }

    @Test
    public void labelledBreakLeavesTheOuterLoop() {
        // The inner BREAK names the OUTER loop, so both stop: outer runs once, inner once.
        assertEquals(1L, ret("""
            BEGIN
              LET outerCount INTEGER := 0;
              FOR i IN 1 TO 3 DO
                outerCount := outerCount + 1;
                FOR j IN 1 TO 3 DO
                  BREAK outer_loop;
                END FOR;
              END FOR outer_loop;
              RETURN outerCount;
            END;"""));
    }

    @Test
    public void unlabeledBreakBreaksInnermostOnly() {
        // Bare BREAK stops only the inner loop; the outer still runs its 3 iterations.
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
}
