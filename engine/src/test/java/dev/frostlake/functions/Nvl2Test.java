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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** NVL2(expr1, expr2, expr3) — expr2 when expr1 is NOT NULL, else expr3. */
public class Nvl2Test extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void notNullReturnsSecond() {
        assertEquals("a", scalar("SELECT NVL2(1, 'a', 'b')").toString());
    }

    @Test
    public void nullReturnsThird() {
        assertEquals("b", scalar("SELECT NVL2(NULL, 'a', 'b')").toString());
    }

    @Test
    public void worksOverColumns() {
        engine.execute("CREATE TABLE t (id INTEGER, phone VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, '555-1234'), (2, NULL)");
        assertEquals("has phone", scalar("SELECT NVL2(phone, 'has phone', 'no phone') FROM t WHERE id = 1").toString());
        assertEquals("no phone", scalar("SELECT NVL2(phone, 'has phone', 'no phone') FROM t WHERE id = 2").toString());
    }

    @Test
    public void thirdMayBeNull() {
        assertNull(scalar("SELECT NVL2(NULL, 'a', NULL)"));
    }
}
