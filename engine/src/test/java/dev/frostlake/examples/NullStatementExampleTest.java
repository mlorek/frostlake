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

package dev.frostlake.examples;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The NULL statement of Snowflake Scripting: a no-op standing where a block needs a statement but no action is
 * wanted. It is a statement only inside a block; written on its own it is a syntax error. Every cell is
 * live-verified.
 */
public class NullStatementExampleTest extends BaseDatabaseTest {

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void demonstrateNullStatement() {
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, active BOOLEAN)");
        assertEquals("SQL compilation error:|syntax error line 1 at position 0 unexpected 'NULL'.", answer("NULL;"));

        engine.execute("INSERT INTO employees VALUES (1, 'Alice', true)");
        engine.execute("BEGIN NULL; END;");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', true)");
        engine.execute("""
            BEGIN
                INSERT INTO employees VALUES (3, 'Charlie', false);
                NULL;
                INSERT INTO employees VALUES (4, 'Diana', true);
            END;
            """);
        engine.execute("""
            BEGIN
                NULL;
                NULL;
                NULL;
            END;
            """);
        assertEquals("4", answer("SELECT COUNT(*) FROM employees"));
    }

    @Test
    public void demonstrateNullStatementUseCase() {
        engine.execute("CREATE TABLE audit_log (id INTEGER, message VARCHAR)");
        // One branch of the conditional needs a statement but no action.
        engine.execute("""
            BEGIN
                FOR i IN 1 TO 3 DO
                    IF (i = 2) THEN
                        NULL;
                    ELSE
                        INSERT INTO audit_log VALUES (:i, 'Processing record ' || :i);
                    END IF;
                END FOR;
            END;
            """);
        assertEquals("Processing record 1, Processing record 3",
            answer("SELECT LISTAGG(message, ', ') WITHIN GROUP (ORDER BY id) FROM audit_log"));
    }
}
