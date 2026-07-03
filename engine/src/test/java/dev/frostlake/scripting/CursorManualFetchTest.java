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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Manual cursor OPEN / FETCH / CLOSE in Snowflake Scripting — the explicit-fetch path the suite previously
 * exercised only via the implicit cursor FOR loop. Covers a multi-row FETCH advancing across rows, FETCH
 * past end-of-data (target NULLed), and the cursor error paths (close-unopened, double-open, undefined
 * cursor).
 */
public class CursorManualFetchTest {

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

    private Object returned(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void multiRowManualFetchAdvancesAcrossRows() {
        // One OPEN, three FETCHes must read distinct rows (sum 6, not 3x the first row).
        assertEquals(6L, ((Number) returned("""
            DECLARE
                c CURSOR FOR SELECT 1 AS n UNION ALL SELECT 2 AS n UNION ALL SELECT 3 AS n;
                v INTEGER;
                total INTEGER DEFAULT 0;
            BEGIN
                OPEN c;
                FETCH c INTO v;
                total := total + v;
                FETCH c INTO v;
                total := total + v;
                FETCH c INTO v;
                total := total + v;
                CLOSE c;
                RETURN total;
            END;
            """)).longValue());
    }

    @Test
    public void fetchPastEndOfDataYieldsNull() {
        // The cursor has one row; a second FETCH is past end-of-data and must NULL the target.
        assertNull(returned("""
            DECLARE
                c CURSOR FOR SELECT 1 AS n;
                v INTEGER DEFAULT 99;
            BEGIN
                OPEN c;
                FETCH c INTO v;
                FETCH c INTO v;
                CLOSE c;
                RETURN v;
            END;
            """));
    }

    @Test
    public void closingAnUnopenedCursorIsTolerated() {
        // Observed behavior: CLOSE on a declared-but-never-opened cursor is a no-op (does not throw) —
        // in contrast to double-OPEN and FETCH-of-an-undefined-cursor below, which do error.
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    DECLARE c CURSOR FOR SELECT 1 AS n;
                    BEGIN
                        CLOSE c;
                    END;
                    """);
            }
        });
    }

    @Test
    public void openingAnAlreadyOpenCursorThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    DECLARE c CURSOR FOR SELECT 1 AS n;
                    BEGIN
                        OPEN c;
                        OPEN c;
                    END;
                    """);
            }
        });
    }

    @Test
    public void fetchFromUndefinedCursorThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    DECLARE v INTEGER;
                    BEGIN
                        FETCH no_such_cursor INTO v;
                    END;
                    """);
            }
        });
    }
}
