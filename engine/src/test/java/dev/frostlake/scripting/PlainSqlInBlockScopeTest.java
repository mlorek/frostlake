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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain SQL statements inside a Snowflake Scripting body resolve their COLUMN references at
 * execution, not against the block's variable scope — the whole-block name check must not flag
 * them.
 */
public class PlainSqlInBlockScopeTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(PlainSqlInBlockScopeTest.class);

    @Test
    public void testInsertSelectUnionInsideProcedureBody() {
        logger.info("Testing INSERT ... SELECT col ... UNION ALL inside a procedure body");

        engine.execute("CREATE TABLE qa (row_id INT, src VARIANT)");
        engine.execute("""
            CREATE PROCEDURE p() RETURNS STRING LANGUAGE SQL EXECUTE AS CALLER AS $$
            BEGIN
                INSERT INTO qa (ROW_ID, SRC)
                SELECT ROW_ID, {} AS SRC FROM qa WHERE FALSE UNION ALL
                SELECT 1, {} AS SRC;
                RETURN 'ok';
            END
            $$""");

        assertEquals("ok", engine.executeQuery("CALL p()").getRows().get(0).getValue(0));
        assertEquals(1, engine.executeQuery("SELECT * FROM qa").getRowCount());

        logger.info("Column references in body SQL untouched by the scripting name check");
    }
}
