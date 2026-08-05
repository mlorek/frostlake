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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests ALTER FUNCTION / ALTER PROCEDURE — RENAME TO and SET/UNSET COMMENT.
 */
public class AlterRoutineTest extends BaseDatabaseTest {

    @Test
    public void alterFunctionRename() {
        engine.execute("CREATE FUNCTION add1(x INTEGER) RETURNS INTEGER AS $$ x + 1 $$");
        engine.execute("ALTER FUNCTION add1(INTEGER) RENAME TO plus1");

        final ResultSet rs = engine.executeQuery("SELECT plus1(5)");
        assertEquals(6, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT add1(5)");
            }
        });
    }

    @Test
    public void alterFunctionSetComment() {
        engine.execute("CREATE FUNCTION fc(x INTEGER) RETURNS INTEGER AS $$ x $$");
        engine.execute("ALTER FUNCTION fc(INTEGER) SET COMMENT = 'doubler'");

        // The function is unaffected functionally.
        final ResultSet rs = engine.executeQuery("SELECT fc(3)");
        assertEquals(3, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void alterProcedureRename() {
        engine.execute("CREATE PROCEDURE pr() RETURNS STRING LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        engine.execute("ALTER PROCEDURE pr() RENAME TO qr");

        // The new name describes; the old name no longer exists. DESCRIBE needs the argument-type list
        // — live-verified on a real account, the bare DESCRIBE PROCEDURE qr fails
        // "Argument types of function 'QR' must be specified." while DESCRIBE PROCEDURE qr() describes.
        engine.executeQuery("DESCRIBE PROCEDURE qr()");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE PROCEDURE qr");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE PROCEDURE pr()");
            }
        });
    }
}
