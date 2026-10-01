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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * {@code LET c CURSOR FOR <resultset>} declares the cursor over a RESULTSET variable, exactly as the
 * DECLARE section's {@code c CURSOR FOR res} does. Frostlake took only a SELECT after LET, so the name
 * was a syntax error (live-verified).
 */
public class LetCursorOverResultSetTest extends BaseDatabaseTest {

    /** The one cell of a block's answer, as text. */
    private String cell(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        return String.valueOf(result.getRows().get(0).getValue(0));
    }

    @Test
    public void aLetCursorReadsAResultSetByName() {
        assertEquals("2.25", cell("""
            BEGIN
              LET rs RESULTSET := (SELECT 1.25::NUMBER(5,2) AS c);
              LET c1 CURSOR FOR rs;
              FOR rec IN c1 DO
                RETURN rec.c + 1;
              END FOR;
            END;"""));
        // The DECLARE section's spelling is unchanged, and so is a LET cursor over a query.
        assertEquals("2.25", cell("""
            DECLARE
              rs RESULTSET DEFAULT (SELECT 1.25::NUMBER(5,2) AS c);
              c1 CURSOR FOR rs;
            BEGIN
              FOR rec IN c1 DO
                RETURN rec.c + 1;
              END FOR;
            END;"""));
        assertEquals("3", cell("""
            BEGIN
              LET c1 CURSOR FOR SELECT 3 AS c;
              FOR rec IN c1 DO
                RETURN rec.c;
              END FOR;
            END;"""));
        // Declared and left unread, the cursor is simply there.
        assertEquals("1", cell("""
            BEGIN
              LET rs RESULTSET := (SELECT 1 AS c);
              LET c1 CURSOR FOR rs;
              RETURN 1;
            END;"""));
    }
}
