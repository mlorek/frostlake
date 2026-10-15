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

import static dev.frostlake.query.QueryAnswers.answer;
import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * GET_DDL's object is looked up wherever the call stands, so a view whose body names a missing object is refused
 * whenever it is read, a read that keeps no row or reads no column included, and so is a statement calling a SQL
 * function whose body names one, wherever the call stands; its constant-arguments refusal prints a context function
 * folded to its value, as the plan holds it (live-verified).
 */
public class GetDdlCompileTest extends BaseDatabaseTest {

    @Test
    public void aViewOverAMissingObjectIsRefusedWheneverItIsRead() {
        engine.execute("CREATE VIEW gdbad AS SELECT GET_DDL('table', 'nosuch_t') AS d");
        engine.execute("CREATE VIEW gd2 AS SELECT * FROM gdbad");
        for (final String sql : new String[] {"SELECT * FROM gdbad WHERE 1 = 0", "SELECT COUNT(*) FROM gdbad",
            "SELECT 1 FROM gdbad", "SELECT COUNT(*) FROM gd2"}) {
            final String refused = answer(engine, sql);
            assertTrue(refused.startsWith("SQL compilation error:|Table 'NOSUCH_T' does not exist or not authorized."),
                sql + " ==> " + refused);
        }
    }

    @Test
    public void aCalledSqlFunctionsBodyIsCompiledWhereverTheCallStands() {
        engine.execute("CREATE TABLE gtt (x INT)");
        engine.execute("CREATE FUNCTION gdf() RETURNS VARCHAR AS $$ GET_DDL('table', 'nosuch_t') $$");
        for (final String sql : new String[] {"SELECT SYSTEM$TYPEOF(gdf())", "SELECT gdf() FROM gtt WHERE 1 = 0",
            "SELECT CASE WHEN FALSE THEN gdf() END", "SELECT COUNT(*) FROM gtt WHERE gdf() IS NULL"}) {
            final String refused = answer(engine, sql);
            assertTrue(refused.startsWith("SQL compilation error:|Table 'NOSUCH_T' does not exist or not authorized."),
                sql + " ==> " + refused);
        }
    }

    @Test
    public void aContextFunctionIsEchoedFolded() {
        final String today = answer(engine, "SELECT CURRENT_DATE::VARCHAR");
        assertCells(engine, new String[][] {
            {"SELECT GET_DDL('table', CURRENT_DATE)", "SQL compilation error:|Invalid value [CAST('" + today
                + "' AS VARCHAR(134217728))] for function '2', parameter EXPORT_DDL: constant arguments expected"},
        });
    }
}
