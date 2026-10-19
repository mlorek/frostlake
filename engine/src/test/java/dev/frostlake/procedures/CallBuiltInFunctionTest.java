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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CALL runs a built-in function as it runs a procedure: one row, in a column named after the function. A
 * user-defined function is never called this way.
 */
public class CallBuiltInFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cbf_t (a INT)");
        engine.execute("CREATE OR REPLACE FUNCTION cbf_f() RETURNS INT AS '1'");
    }

    /** A CALL's one column and one value, as NAME=value. */
    private String called(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getColumns().get(0).getName() + "=" + rs.getRows().get(0).getValue(0);
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aBuiltInFunctionIsCalledInAColumnOfItsName() {
        assertEquals("UPPER=A", called("CALL UPPER('a')"));
        assertEquals("CONCAT=ab", called("CALL CONCAT('a', 'b')"));
        assertEquals("ABS=1", called("CALL ABS(-1)"));
        assertEquals("CURRENT_DATABASE=TEST_DB", called("CALL CURRENT_DATABASE()"));
        assertEquals("SUM=1", called("CALL SUM(1)"));
        assertEquals("TO_VARCHAR=1", called("CALL TO_VARCHAR(1)"));
        assertEquals("SYSTEM$WAIT=waited 0 seconds", called("CALL SYSTEM$WAIT(0)"));
    }

    @Test
    public void itsArgumentsAreExpressions() {
        assertEquals("UPPER=A", called("CALL UPPER(LOWER('A'))"));
        assertEquals("UPPER=AB", called("CALL UPPER('a' || 'b')"));
        assertEquals("UPPER=A", called("CALL UPPER((SELECT 'a'))"));
        assertEquals("SQL compilation error: error line 1 at position 11\ninvalid identifier 'A'",
            refusalOf("CALL UPPER(a)"));
    }

    @Test
    public void aBlockTakesItsAnswerInto() {
        assertEquals("A", String.valueOf(engine.executeQuery(
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN CALL UPPER('a') INTO :r; RETURN r; END; $$")
            .getRows().get(0).getValue(0)));
    }

    @Test
    public void aUserDefinedFunctionAndAnUnknownNameAreNoProcedure() {
        assertEquals("SQL compilation error:\nUnknown function CBF_F.", refusalOf("CALL cbf_f()"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.", refusalOf("CALL NOSUCHFN(1)"));
        assertEquals("SQL compilation error:\nUnknown function SYSTEM$NOSUCH.", refusalOf("CALL SYSTEM$NOSUCH(1)"));
    }
}
