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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code TRY_TO_DECIMAL} / {@code TRY_TO_NUMERIC} — Snowflake synonyms of {@code TRY_TO_NUMBER}
 * (mirroring {@code TO_DECIMAL} / {@code TO_NUMERIC} for {@code TO_NUMBER}). Neither synonym was
 * registered, so a loader using {@code TRY_TO_DECIMAL} failed with "Unknown function".
 */
public class TryToDecimalTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void tryToDecimalParsesAndReturnsNullOnGarbage() {
        assertEquals(42L, ((Number) scalar("SELECT TRY_TO_DECIMAL('42')")).longValue());
        assertNull(scalar("SELECT TRY_TO_DECIMAL('not a number')"));
        // TRY_TO_* is TRY_CAST under the hood and needs a VARCHAR source: an UNTYPED NULL errors
        // "Function TRY_CAST cannot be used with arguments of types NULL and NUMBER(38,0)"
        // (live-verified), while NULL::VARCHAR converts to NULL.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                scalar("SELECT TRY_TO_DECIMAL(NULL)");
            }
        });
        assertNull(scalar("SELECT TRY_TO_DECIMAL(NULL::VARCHAR)"));
    }

    @Test
    public void tryToNumericIsTheSameFunction() {
        assertEquals(7L, ((Number) scalar("SELECT TRY_TO_NUMERIC('7')")).longValue());
        assertNull(scalar("SELECT TRY_TO_NUMERIC('x')"));
    }

    @Test
    public void behavesExactlyLikeTryToNumber() {
        assertEquals(String.valueOf(scalar("SELECT TRY_TO_NUMBER('12.5')")),
            String.valueOf(scalar("SELECT TRY_TO_DECIMAL('12.5')")));
    }
}
