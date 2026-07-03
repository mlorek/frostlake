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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Time Travel (AT / BEFORE) does not apply to a stream (Snowflake) — a stream source with an AT/BEFORE
 * clause is now rejected rather than silently read at its current offset as if the clause were absent.
 * Plain stream reads and table Time Travel are unaffected.
 */
public class StreamTimeTravelUnsupportedTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE base (id INTEGER)");
        engine.execute("INSERT INTO base VALUES (1), (2)");
        engine.execute("CREATE STREAM strm ON TABLE base");
        engine.execute("INSERT INTO base VALUES (3)"); // one unconsumed change (row 3)
    }

    @Test
    public void plainStreamReadStillWorks() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) AS c FROM strm");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void atClauseOnStreamIsRejected() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM strm AT(OFFSET => -60)");
            }
        });
    }

    @Test
    public void beforeClauseOnStreamIsRejected() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM strm BEFORE(STATEMENT => 'x')");
            }
        });
    }

    @Test
    public void timeTravelOnRegularTableIsUnaffected() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM base AT(OFFSET => -1)");
            }
        });
    }
}
