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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block hands a call an argument that reads no name as SQL writes it, so the call reads it at the type SQL gives it
 * (live-verified): TO_VARCHAR(10 / 4) is 2.500000 and TO_VARCHAR(1.50::NUMBER(10,2)) is 1.50 in a block as in a
 * query, and a variable standing alone is its value converted to its declared type.
 */
public class BlockNameFreeArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aNameFreeArgumentIsTypedAsSqlTypesIt() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN TO_VARCHAR(1.50::NUMBER(10,2)); END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN TO_VARCHAR(10 / 4); END; $$", "2.500000"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN TO_VARCHAR(1.50::NUMBER(10,2) + 0); END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN TO_VARCHAR(2.0 * 1.25); END; $$", "2.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x NUMBER(10,2) := 1.50; RETURN TO_VARCHAR(x); END; $$", "1.50"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET r VARCHAR := TO_VARCHAR(10 / 4); RETURN r; END; $$", "2.500000"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (10 / 4)::VARCHAR; END; $$", "2.500000"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 10 / 4; END; $$", "2.500000"},
            {"SELECT TO_VARCHAR(10 / 4), TO_VARCHAR(1.50::NUMBER(10,2))", "2.500000|1.50"},
        });
    }
}
