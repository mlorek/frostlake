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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A lateral table function's arguments are compiled before a row is read: a column or a function they name and
 * nothing answers to is refused over an empty relation exactly as over one that holds rows.
 */
public class LateralArgumentCompileTest extends BaseDatabaseTest {

    private static final String NO_COLUMN = "\ninvalid identifier 'LAC.NOSUCH'";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE lac (a INT, b INT, v VARIANT)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void assertRefusedOverNoRowAndOverOne() {
        assertEquals("SQL compilation error: error line 1 at position 44" + NO_COLUMN,
            refusalOf("SELECT * FROM lac, LATERAL FLATTEN(input => lac.nosuch)"));
        assertEquals("SQL compilation error: error line 1 at position 42" + NO_COLUMN,
            refusalOf("SELECT * FROM lac, TABLE(FLATTEN(input => lac.nosuch))"));
        assertEquals("SQL compilation error: error line 1 at position 40" + NO_COLUMN,
            refusalOf("SELECT * FROM lac, TABLE(SPLIT_TO_TABLE(lac.nosuch, ','))"));
        assertEquals("SQL compilation error: error line 1 at position 48" + NO_COLUMN,
            refusalOf("SELECT * FROM lac JOIN LATERAL FLATTEN(input => lac.nosuch)"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusalOf("SELECT * FROM lac, LATERAL FLATTEN(input => nosuchfn(lac.a))"));
    }

    @Test
    public void anUnknownNameIsRefusedOverAnEmptyRelation() {
        assertRefusedOverNoRowAndOverOne();
        // What does resolve answers no row.
        assertEquals(0, engine.executeQuery("SELECT * FROM lac, LATERAL FLATTEN(input => lac.v)").getRowCount());
        assertEquals(0, engine.executeQuery(
            "SELECT ARRAY_CONSTRUCT(1) obs, f.value FROM lac, TABLE(FLATTEN(obs)) f").getRowCount());
    }

    @Test
    public void theSameRefusalsOverARelationHoldingRows() {
        engine.execute("INSERT INTO lac SELECT 1, 2, PARSE_JSON('[1]')");
        assertRefusedOverNoRowAndOverOne();
        assertTypesRefused();
    }

    /** An argument of a type its parameter does not take is refused the same way, the parameter named as written. */
    @Test
    public void anArgumentOfAnotherTypeIsRefusedOverAnEmptyRelation() {
        assertTypesRefused();
    }

    private void assertTypesRefused() {
        final String number = "SQL compilation error:\ninvalid type [NUMBER(38,0)] for parameter '";
        assertEquals(number + "input'", refusalOf("SELECT * FROM lac, LATERAL FLATTEN(input => lac.a)"));
        assertEquals(number + "INPUT'", refusalOf("SELECT * FROM lac, LATERAL FLATTEN(INPUT => lac.a)"));
        assertEquals(number + "Input'", refusalOf("SELECT * FROM lac, LATERAL FLATTEN(Input => lac.a)"));
        assertEquals(number + "1'", refusalOf("SELECT * FROM lac, LATERAL FLATTEN(lac.a)"));
        assertEquals(number + "input'", refusalOf("SELECT * FROM lac, TABLE(FLATTEN(input => lac.a))"));
        assertEquals(number + "input'", refusalOf("SELECT * FROM lac JOIN LATERAL FLATTEN(input => lac.a)"));
        assertEquals(number + "1'", refusalOf("SELECT * FROM lac, LATERAL SPLIT_TO_TABLE(lac.a, ',')"));
        assertEquals("SQL compilation error:\ninvalid type [NUMBER(1,0)] for parameter 'path'",
            refusalOf("SELECT * FROM lac, LATERAL FLATTEN(input => lac.v, path => 1)"));
    }
}
