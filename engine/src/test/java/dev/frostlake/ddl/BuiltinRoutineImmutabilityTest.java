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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Built-in functions and procedures cannot be dropped or altered — not through a guard, but by
 * NAMESPACE: DROP/ALTER FUNCTION and DROP/ALTER PROCEDURE resolve schema-scoped objects only, and
 * built-ins live outside every schema. The refusal is therefore the ordinary
 * "does not exist or not authorized" naming the fully qualified schema object, IF EXISTS forgives
 * it like any absence (leaving the built-in untouched), and a user routine that merely shares a
 * built-in's name creates and drops freely. All live-verified.
 */
public class BuiltinRoutineImmutabilityTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private void assertRefusedAsAbsent(final String sql, final String kind, final String name) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql + " must be refused");
        assertTrue(e.getMessage().contains(kind) && e.getMessage().contains(name)
                && e.getMessage().contains("does not exist or not authorized"),
            sql + " answered: " + e.getMessage());
    }

    @Test
    public void testDropBuiltinFunctionIsRefusedAsAbsent() {
        assertRefusedAsAbsent("DROP FUNCTION abs(NUMBER)", "Function", "ABS");
        assertRefusedAsAbsent("DROP FUNCTION upper(VARCHAR)", "Function", "UPPER");

        // The built-ins are untouched.
        assertEquals("5", scalar("SELECT ABS(-5)"));
        assertEquals("X", scalar("SELECT UPPER('x')"));
    }

    @Test
    public void testDropBuiltinFunctionIfExistsIsForgiven() {
        // IF EXISTS forgives the absence — and the built-in survives.
        engine.execute("DROP FUNCTION IF EXISTS abs(NUMBER)");

        assertEquals("5", scalar("SELECT ABS(-5)"));
    }

    @Test
    public void testAlterBuiltinFunctionIsRefusedAsAbsent() {
        assertRefusedAsAbsent("ALTER FUNCTION abs(NUMBER) RENAME TO abs2", "Function", "ABS");
        assertRefusedAsAbsent("ALTER FUNCTION abs(NUMBER) SET COMMENT = 'x'", "Function", "ABS");

        assertEquals("5", scalar("SELECT ABS(-5)"));
    }

    @Test
    public void testDropOrAlterBuiltinProcedureIsRefusedAsAbsent() {
        assertRefusedAsAbsent("DROP PROCEDURE associate_semantic_category_tags(VARCHAR, OBJECT)",
            "Procedure", "ASSOCIATE_SEMANTIC_CATEGORY_TAGS");
        assertRefusedAsAbsent("ALTER PROCEDURE associate_semantic_category_tags(VARCHAR, OBJECT) SET COMMENT = 'x'",
            "Procedure", "ASSOCIATE_SEMANTIC_CATEGORY_TAGS");
    }

    @Test
    public void testUserFunctionSharingBuiltinNameIsDroppable() {
        // A schema function may share a built-in's name; dropping it removes only the schema
        // object and the built-in keeps answering.
        engine.execute("CREATE FUNCTION abs(x VARCHAR) RETURNS VARCHAR AS 'x'");
        engine.execute("DROP FUNCTION abs(VARCHAR)");

        assertEquals("5", scalar("SELECT ABS(-5)"));
    }
}
