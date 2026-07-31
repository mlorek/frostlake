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
 * TO_DATE / TRY_TO_DATE parsing: an explicit Snowflake format is honored and an ISO string parses
 * without one. A numeric argument is a Unix epoch only under the {@code DATE()} spelling — TO_DATE and
 * TRY_TO_DATE reject it. Previously the format argument was ignored (ISO-only parsing).
 */
public class ToDateTest extends BaseDatabaseTest {

    private String date(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0).toString();
    }

    @Test
    public void isoWithoutFormat() {
        assertEquals("2021-01-15", date("SELECT TO_DATE('2021-01-15')"));
    }

    @Test
    public void explicitFormatIsHonored() {
        assertEquals("2021-01-15", date("SELECT TO_DATE('01/15/2021', 'MM/DD/YYYY')"));
    }

    @Test
    public void numericArgumentIsEpochSecondsOnlyForTheDateSpelling() {
        // Live-verified on a real account: TO_DATE(1631711999) and TRY_TO_DATE(1631711999)
        // both fail "invalid type [TO_DATE(1631711999)] for parameter 'TO_DATE'", while the DATE() alias
        // of the very same function reads the epoch and answers 2021-09-15 — and a numeric STRING is
        // fine either way.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                date("SELECT TO_DATE(1631711999)");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                date("SELECT TRY_TO_DATE(1631711999)");
            }
        });
        assertEquals("2021-09-15", date("SELECT DATE(1631711999)"));
        assertEquals("2021-09-15", date("SELECT TO_DATE('1631711999')"));
    }

    @Test
    public void tryToDateValidFormat() {
        assertEquals("2021-01-15", date("SELECT TRY_TO_DATE('01/15/2021', 'MM/DD/YYYY')"));
    }

    @Test
    public void tryToDateInvalidReturnsNull() {
        assertNull(engine.executeQuery("SELECT TRY_TO_DATE('not-a-date', 'MM/DD/YYYY') AS r").getRows().get(0).getValue(0));
    }
}
