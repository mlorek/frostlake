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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cursor query may contain positional bind placeholders ({@code ?}) supplied at OPEN via
 * {@code OPEN <cursor> USING (v1, v2, …)}. Previously the lexer rejected {@code ?} and OPEN had no USING
 * clause.
 */
public class CursorPositionalBindTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE invoices (id NUMBER, price NUMBER)");
        engine.execute("INSERT INTO invoices VALUES (1, 10), (2, 20), (3, 30), (4, 40), (5, 50)");
    }

    private long ret(final String block) {
        return ((Number) engine.executeQuery(block).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void cursorWithTwoPositionalBinds() {
        // price > 15 AND price < 45 → {20, 30, 40} → sum 90.
        assertEquals(90L, ret("""
            DECLARE c1 CURSOR FOR SELECT price FROM invoices WHERE price > ? AND price < ?;
            BEGIN
              LET total INTEGER := 0;
              OPEN c1 USING (15, 45);
              FOR rec IN c1 DO
                total := total + rec.price;
              END FOR;
              RETURN total;
            END;"""));
    }

    @Test
    public void cursorWithSinglePositionalBind() {
        // price >= 40 → {40, 50} → count 2 via manual OPEN/FETCH.
        assertEquals(2L, ret("""
            DECLARE c CURSOR FOR SELECT id FROM invoices WHERE price >= ?;
            BEGIN
              LET cnt INTEGER := 0;
              OPEN c USING (40);
              FOR r IN c DO
                cnt := cnt + 1;
              END FOR;
              RETURN cnt;
            END;"""));
    }

    @Test
    public void cursorWithoutBindsStillWorks() {
        // Regression: a plain cursor (no placeholders, no USING) still opens.
        assertEquals(150L, ret("""
            DECLARE c CURSOR FOR SELECT price FROM invoices;
            BEGIN
              LET total INTEGER := 0;
              OPEN c;
              FOR r IN c DO
                total := total + r.price;
              END FOR;
              RETURN total;
            END;"""));
    }
}
