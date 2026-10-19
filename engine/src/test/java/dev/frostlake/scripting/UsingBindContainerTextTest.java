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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A USING list binds an ARRAY's and an OBJECT's value as its JSON text with each member on a line of its own and
 * nothing indented, {@code [\n1,\n2\n]} a VARCHAR(8), as it binds every other value as text (live-verified).
 */
public class UsingBindContainerTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aContainerIsBoundLinePerMember() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"DECLARE x VARIANT DEFAULT PARSE_JSON('{\"a\": 1}'); c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r "
                + "VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "VARCHAR(10)[LOB]"},
            {"DECLARE x ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2); c CURSOR FOR SELECT SYSTEM$TYPEOF(?); r VARCHAR; "
                + "BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "VARCHAR(8)[LOB]"},
            {"DECLARE x ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2); c CURSOR FOR SELECT ?; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;",
                "[ | 1, | 2 | ]"},
            {"DECLARE x VARIANT DEFAULT PARSE_JSON('{\"a\": 1, \"b\": [2, 3]}'); c CURSOR FOR SELECT ?; r "
                + "VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "{ | \"a\": 1, | \"b\": [ | 2, | 3 | ] | }"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz ORDER BY id LIMIT ?; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 94 : SQL compilation "
                + "error: | Invalid row count '?' in limit clause"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT SYSTEM$TYPEOF(? + 0); r VARCHAR; BEGIN OPEN c USING "
                + "(x); FETCH c INTO r; RETURN r; END;",
                "NUMBER(19,0)[SB1]"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT ROUND(1.2345, ?); r VARCHAR; BEGIN OPEN c USING (x); "
                + "FETCH c INTO r; RETURN r; END;",
                "1.2000"},
            {"SELECT ROUND(1.2345, '1')", "1.2000"},
            {"SELECT SYSTEM$TYPEOF('1' + 0)", "NUMBER(19,0)[SB1]"},
        });
    }
}
