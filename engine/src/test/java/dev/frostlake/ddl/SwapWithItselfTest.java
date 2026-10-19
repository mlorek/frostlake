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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER TABLE t SWAP WITH t, however either name is spelled, leaves the table as it was; a view named as the other
 * table is refused by its kind; and the table to swap with is looked for before IF EXISTS forgives a missing one
 * (live-verified).
 */
public class SwapWithItselfTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String first(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void aTableSwappedWithItselfKeepsItsRows() {
        engine.execute("CREATE TABLE keep (a INT)");
        engine.execute("INSERT INTO keep VALUES (1), (2), (3)");
        final String qualified = first("SELECT CURRENT_DATABASE()") + "." + first("SELECT CURRENT_SCHEMA()") + ".keep";
        engine.execute("ALTER TABLE keep SWAP WITH keep");
        engine.execute("ALTER TABLE keep SWAP WITH " + qualified);
        engine.execute("ALTER TABLE keep SWAP WITH \"KEEP\"");
        engine.execute("ALTER TABLE IF EXISTS keep SWAP WITH keep");
        assertEquals("3", first("SELECT COUNT(*) FROM keep"));
        engine.execute("CREATE TEMPORARY TABLE tk (a INT)");
        engine.execute("INSERT INTO tk VALUES (1)");
        engine.execute("ALTER TABLE tk SWAP WITH tk");
        assertEquals("1", first("SELECT COUNT(*) FROM tk"));
    }

    @Test
    public void aViewIsNoTableToSwapWith() {
        engine.execute("CREATE TABLE keep (a INT)");
        engine.execute("CREATE VIEW vk AS SELECT * FROM keep");
        assertEquals("SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            refusal("ALTER TABLE keep SWAP WITH vk"));
    }

    @Test
    public void ifExistsForgivesOnlyTheTableItNames() {
        engine.execute("CREATE TABLE other (a INT)");
        engine.execute("ALTER TABLE IF EXISTS nope SWAP WITH other");
        assertTrue(refusal("ALTER TABLE IF EXISTS nope SWAP WITH nope2").contains("NOPE2' does not exist"));
        assertTrue(refusal("ALTER TABLE IF EXISTS nope SWAP WITH nope").contains("NOPE' does not exist"));
        assertTrue(refusal("ALTER TABLE IF EXISTS other SWAP WITH nope").contains("NOPE' does not exist"));
    }
}
