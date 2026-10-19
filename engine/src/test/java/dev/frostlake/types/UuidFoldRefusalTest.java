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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A UUID beside another type in the functions that fold their branches: it meets a text and an untyped NULL, and
 * stays a UUID; beside a number, a date or a boolean the statement is refused while it compiles, naming the later
 * of the two operands and the type the first one set. TO_ARRAY and TO_OBJECT take no UUID, where TO_VARIANT and
 * the constructors do (all live-verified).
 */
public class UuidFoldRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ut (u UUID, s VARCHAR)");
        engine.execute("INSERT INTO ut SELECT UUID_STRING()::UUID, 'x'");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aUuidBesideANumberADateOrABooleanIsRefused() {
        final String[][] refused = {
            {"TO_ARRAY(u)", "SQL compilation error:\ninvalid type [TO_ARRAY(UT.U)] for parameter 'TO_ARRAY'"},
            {"TO_OBJECT(u)", "SQL compilation error:\ninvalid type [TO_OBJECT(UT.U)] for parameter 'TO_OBJECT'"},
            {"DECODE(u, NULL, 1, u)", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"DECODE(u, NULL, u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"DECODE(1, 1, u, 2)", "SQL compilation error:\nCan not convert parameter '2' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"IFF(TRUE, 1, u)", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"IFF(TRUE, u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"CASE WHEN TRUE THEN 1 ELSE u END", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"CASE WHEN TRUE THEN u ELSE 1 END", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"COALESCE(u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"COALESCE(1, u)", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"NVL(u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"NVL(1, u)", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"NVL2(u, 1, u)", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"NVL2(u, u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"IFNULL(u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"GREATEST(u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"LEAST(1, u)", "SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type [NUMBER(1,0)]"},
            {"NULLIF(u, 1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"IFF(TRUE, u, CURRENT_DATE)", "SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected type [UUID]"},
            {"IFF(TRUE, u, TRUE)", "SQL compilation error:\nCan not convert parameter 'TRUE' of type [BOOLEAN] into expected type [UUID]"},
            {"IFF(TRUE, u, 1.5)", "SQL compilation error:\nCan not convert parameter '1.5' of type [NUMBER(2,1)] into expected type [UUID]"},
            {"u = 1", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
            {"u IN (1)", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [UUID]"},
        };
        for (final String[] cell : refused) {
            assertEquals(cell[1], refusal("SELECT SYSTEM$TYPEOF(" + cell[0] + ") FROM ut"), cell[0]);
        }
    }

    @Test
    public void theRefusalIsTheSameOutsideSystemTypeof() {
        assertEquals("SQL compilation error:\nCan not convert parameter 'UT.U' of type [UUID] into expected type"
            + " [NUMBER(1,0)]", refusal("SELECT IFF(TRUE, 1, u) IS NULL FROM ut"));
        assertEquals("SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type"
            + " [UUID]", refusal("SELECT COALESCE(u, 1) IS NULL FROM ut"));
        assertEquals("SQL compilation error:\ninvalid type [TO_ARRAY(UT.U)] for parameter 'TO_ARRAY'",
            refusal("SELECT TO_ARRAY(u) IS NULL FROM ut"));
    }

    @Test
    public void aUuidBesideATextOrANullStaysAUuid() {
        final String[][] typed = {
            {"TO_VARIANT(u)", "VARIANT[LOB]"},
            {"ARRAY_CONSTRUCT(u)", "ARRAY[LOB]"},
            {"ARRAY_CONSTRUCT_COMPACT(u)", "ARRAY[LOB]"},
            {"OBJECT_CONSTRUCT('k', u)", "OBJECT[LOB]"},
            {"IFF(TRUE, u, 'a')", "UUID[SB16]"},
            {"COALESCE(u, 'a')", "UUID[SB16]"},
            {"IFF(TRUE, u, s)", "UUID[SB16]"},
            {"COALESCE(u, s)", "UUID[SB16]"},
            {"IFF(TRUE, u, NULL)", "UUID[SB16]"},
            {"u = 'a'", "BOOLEAN[ROWINDEX]"},
            {"ARRAY_AGG(u)", "ARRAY[LOB]"},
            {"TO_VARCHAR(u)", "VARCHAR[LOB]"},
            {"u::VARIANT", "VARIANT[LOB]"},
            {"LENGTH(u)", "NUMBER(18,0)[SB8]"},
        };
        for (final String[] cell : typed) {
            final ResultSet rs = engine.executeQuery("SELECT SYSTEM$TYPEOF(" + cell[0] + ") FROM ut");
            assertTrue(rs.next(), cell[0]);
            assertEquals(cell[1], rs.getValue(0), cell[0]);
        }
    }
}
