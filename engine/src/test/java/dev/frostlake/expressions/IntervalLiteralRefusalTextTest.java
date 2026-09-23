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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A standalone interval literal is refused with live's full text: the unpositioned prefix, then a detail line
 * that opens with a colon. Every cell is live-verified.
 */
public class IntervalLiteralRefusalTextTest extends BaseDatabaseTest {

    private static final String REFUSAL =
        "SQL compilation error: error line 0 at position -1\n: interval literal is not supported in this form.";

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void theDetailOpensWithAColon() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        assertEquals(REFUSAL, refusal("SELECT INTERVAL '1 day'"));
        assertEquals(REFUSAL, refusal("SELECT 1, INTERVAL '1 day'"));
        assertEquals(REFUSAL, refusal("SELECT INTERVAL '1 day, 2 hours'"));
        assertEquals(REFUSAL, refusal("SELECT INTERVAL '1 day' AS x"));
        assertEquals(REFUSAL, refusal("SELECT INTERVAL '1 day' FROM t"));
    }
}
