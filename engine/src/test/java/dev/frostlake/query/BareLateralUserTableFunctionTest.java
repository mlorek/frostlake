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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A user-defined table function written after LATERAL without TABLE( … ) is resolved and its arguments matched to
 * its parameters by type, as inside TABLE( … ); a name no table function carries is refused; and a table function's
 * arguments are judged by type whatever its language (live-verified).
 */
public class BareLateralUserTableFunctionTest extends BaseDatabaseTest {

    private static final String ARGUMENTS = "SQL compilation error: error line 1 at position 26|"
        + "Invalid argument types for function 'PTF': ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION ptf(p NUMBER) RETURNS TABLE (x VARCHAR) AS $$ SELECT TO_VARCHAR(p) $$");
        engine.execute("CREATE TABLE tt (d NUMBER(10,2), n NUMBER(38,0))");
        engine.execute("INSERT INTO tt VALUES (1.5, 2)");
    }

    @Test
    public void aBareCallIsResolvedAndJudged() {
        assertCells(engine, new String[][] {
            {"SELECT x FROM tt, LATERAL ptf(tt.n)", "2"},
            {"SELECT f.x FROM tt, LATERAL ptf(tt.n) AS f", "2"},
            {"SELECT x FROM tt, LATERAL ptf(p => tt.n)", "2"},
            {"SELECT x FROM tt, LATERAL ptf(tt.d)", ARGUMENTS + "(NUMBER(10,2))"},
            {"SELECT x FROM tt, LATERAL ptf('a')", ARGUMENTS + "(VARCHAR(1))"},
            {"SELECT x FROM tt, LATERAL ptf(tt.n, 1)", ARGUMENTS + "(NUMBER(38,0), NUMBER(1,0))"},
            {"SELECT x FROM tt, LATERAL ptf()", ARGUMENTS + "()"},
            {"SELECT x FROM tt, LATERAL nosuch_tf(tt.n)", "SQL compilation error:|Unknown table function NOSUCH_TF"},
            {"SELECT x FROM tt, LATERAL \"nosuch_tf\"(tt.n)",
                "SQL compilation error:|Unknown table function \"nosuch_tf\""},
        });
    }

    @Test
    public void aJavaScriptTableFunctionsArgumentsAreJudgedByType() {
        engine.execute("""
            CREATE FUNCTION jtf(p FLOAT) RETURNS TABLE (x VARCHAR) LANGUAGE JAVASCRIPT AS
            $$ {processRow: function (row, rowWriter, context) { rowWriter.writeRow({X: String(row.P)}); }} $$""");
        final String refused = "SQL compilation error: error line 1 at position 20|"
            + "Invalid argument types for function 'JTF': ";
        assertCells(engine, new String[][] {
            {"SELECT * FROM TABLE(jtf('abc'))", refused + "(VARCHAR(3))"},
            {"SELECT * FROM TABLE(jtf(1, 2))", refused + "(NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT * FROM TABLE(jtf(TRUE))", refused + "(BOOLEAN)"},
            {"SELECT * FROM TABLE(jtf())", refused + "()"},
            {"SELECT x FROM tt, LATERAL jtf(tt.d)", "SQL compilation error: error line 1 at position 26|"
                + "Invalid argument types for function 'JTF': (NUMBER(10,2))"},
        });
    }
}
