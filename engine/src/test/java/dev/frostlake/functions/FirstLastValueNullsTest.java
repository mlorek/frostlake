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

/**
 * FIRST_VALUE / LAST_VALUE null treatment: the default is RESPECT NULLS (the first/last row's value,
 * even if NULL) — previously the engine unconditionally skipped NULLs. The IGNORE NULLS modifier
 * selects the first/last non-null value; RESPECT NULLS is also accepted explicitly. Frame w over
 * (1,NULL),(2,20),(3,30),(4,NULL): the whole-partition frame makes every row see first=NULL, last=NULL.
 */
public class FirstLastValueNullsTest extends BaseDatabaseTest {

    private static final String FRAME =
        "OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE w (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO w VALUES (1, NULL), (2, 20), (3, 30), (4, NULL)");
    }

    /** The window value on the first output row (identical across rows for a whole-partition frame). */
    private Object win(final String funcExpr) {
        return engine.executeQuery("SELECT id, " + funcExpr + " " + FRAME + " AS r FROM w ORDER BY id")
            .getRows().get(0).getValue(1);
    }

    @Test
    public void firstValueRespectsNullsByDefault() {
        assertNull(win("FIRST_VALUE(v)"));
    }

    @Test
    public void firstValueIgnoreNullsReturnsFirstNonNull() {
        assertEquals(20, ((Number) win("FIRST_VALUE(v) IGNORE NULLS")).intValue());
    }

    @Test
    public void firstValueExplicitRespectNulls() {
        assertNull(win("FIRST_VALUE(v) RESPECT NULLS"));
    }

    @Test
    public void lastValueRespectsNullsByDefault() {
        assertNull(win("LAST_VALUE(v)"));
    }

    @Test
    public void lastValueIgnoreNullsReturnsLastNonNull() {
        assertEquals(30, ((Number) win("LAST_VALUE(v) IGNORE NULLS")).intValue());
    }

    @Test
    public void noNullsBehaveAsPlainFirstAndLast() {
        assertEquals(1, ((Number) win("FIRST_VALUE(id)")).intValue());
        assertEquals(4, ((Number) win("LAST_VALUE(id)")).intValue());
    }
}
