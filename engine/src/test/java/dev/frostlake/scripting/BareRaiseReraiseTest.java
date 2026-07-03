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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A bare {@code RAISE;} inside an exception handler re-raises the exception being handled (keeping its
 * original error), instead of throwing a fresh generic "Error raised" (Snowflake Scripting).
 */
public class BareRaiseReraiseTest extends BaseDatabaseTest {

    @Test
    public void bareRaiseReRaisesTheOriginalErrorNotAGenericOne() {
        engine.execute("CREATE TABLE rr (a INTEGER NOT NULL)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    BEGIN
                        INSERT INTO rr VALUES (NULL);
                    EXCEPTION
                        WHEN OTHER THEN
                            RAISE;
                    END;
                    """);
            }
        });
        // Fixed: the original NOT-NULL error propagates; before, the bare RAISE threw a generic "Error raised".
        assertFalse(ex.getMessage().contains("Error raised"), ex.getMessage());
    }

    @Test
    public void handlerWithoutReraiseSwallowsTheError() {
        engine.execute("CREATE TABLE rr2 (a INTEGER NOT NULL)");
        engine.execute("CREATE TABLE raise_log (m VARCHAR)");
        engine.execute("""
            BEGIN
                INSERT INTO rr2 VALUES (NULL);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO raise_log VALUES ('caught');
            END;
            """);
        assertEquals(1L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM raise_log")
            .getRows().get(0).getValue(0)).longValue());
    }
}
