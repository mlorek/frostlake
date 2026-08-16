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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Named exception-handler matching: {@code EXCEPTION WHEN <user_exception_name>} must catch a {@code RAISE}
 * of that same declared exception — and only that one. The exception messages here deliberately do NOT
 * contain the exception name, so a catch proves real name-matching rather than a coincidental message
 * substring (the previous behavior).
 */
public class NamedExceptionHandlerTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE caught (marker INTEGER)");
    }

    private int caughtMarker() {
        final ResultSet rs = engine.executeQuery("SELECT marker FROM caught");
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    private long caughtCount() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM caught");
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void namedHandlerCatchesMatchingRaise() {
        engine.execute("""
            DECLARE
                my_exc EXCEPTION (-20002, 'boom');
            BEGIN
                RAISE my_exc;
            EXCEPTION
                WHEN my_exc THEN
                    INSERT INTO caught VALUES (1);
            END;
            """);
        assertEquals(1, caughtMarker(), "WHEN my_exc must catch RAISE my_exc");
    }

    @Test
    public void nonMatchingNamedHandlerDoesNotCatch() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    DECLARE
                        err_a EXCEPTION (-20001, 'aaa');
                        err_b EXCEPTION (-20002, 'bbb');
                    BEGIN
                        RAISE err_a;
                    EXCEPTION
                        WHEN err_b THEN
                            INSERT INTO caught VALUES (1);
                    END;
                    """);
            }
        });
        assertTrue(ex.getMessage().contains("aaa"),
            "a non-matching named handler must not catch; err_a propagates: " + ex.getMessage());
        assertEquals(0, caughtCount(), "the handler body must not have run");
    }

    @Test
    public void matchingNamedHandlerWinsOverWhenOther() {
        engine.execute("""
            DECLARE
                my_exc EXCEPTION (-20002, 'boom');
            BEGIN
                RAISE my_exc;
            EXCEPTION
                WHEN my_exc THEN
                    INSERT INTO caught VALUES (1);
                WHEN OTHER THEN
                    INSERT INTO caught VALUES (2);
            END;
            """);
        assertEquals(1, caughtMarker(), "the matching named handler must run, not the WHEN OTHER catch-all");
    }

    @Test
    public void whenOtherStillCatchesRaise() {
        // Regression guard: WHEN OTHER remains a catch-all for a RAISEd user exception.
        engine.execute("""
            DECLARE
                my_exc EXCEPTION (-20002, 'boom');
            BEGIN
                RAISE my_exc;
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO caught VALUES (7);
            END;
            """);
        assertEquals(7, caughtMarker());
    }
}
