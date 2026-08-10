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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ExecuteImmediateCursorConstraintsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ExecuteImmediateCursorConstraintsTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE SCHEMA s1");
        logger.info("DatabaseEngine initialized for EXECUTE IMMEDIATE foreign key tests");
    }

    @Test
    public void testExecuteImmediateCursorQueryConstraints() {
        logger.info("Testing EXECUTE IMMEDIATE with cursor querying constraints");

        engine.execute("CREATE TABLE s1.t2 (c1 INTEGER)");

        final String query = """
            EXECUTE IMMEDIATE $$
            DECLARE res ARRAY DEFAULT [];
            BEGIN
                LET fks CURSOR FOR
                    SELECT constraint_name, table_schema, table_name, constraint_type
                    FROM information_schema.table_constraints
                    WHERE table_schema = 's1'
                        AND table_name = 't2';

                FOR rec IN fks DO
                    res := ARRAY_APPEND(res, 'Found constraint: ' || rec.constraint_name);
                END FOR;

                RETURN res;
            END;
            $$;
            """;

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute(query);
            }
        });

        logger.info("EXECUTE IMMEDIATE with cursor querying constraints executed successfully");
    }
}
