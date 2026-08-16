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
 * A quoted name is its own object, kept exactly as written: {@code "kv"} lives beside {@code KV}, each with its
 * own columns, and dropping one leaves the other. Sequences, stages and functions do the same, and a name
 * holding a dot ({@code "a.b"} beside {@code "A.B"}) is two objects as well. SHOW's LIKE still folds, so it
 * lists both. Every cell is live-verified.
 */
public class QuotedNameCoexistenceTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aQuotedNameIsItsOwnObject() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P472_DB");
            engine.execute("CREATE TABLE KV (a INT)");
            engine.execute("INSERT INTO KV VALUES (1)");
            engine.execute("CREATE TABLE \"kv\" (b INT)");
            engine.execute("INSERT INTO \"kv\" VALUES (2)");
            assertEquals("1",
                rows("SELECT a FROM KV"));
            assertEquals("2",
                rows("SELECT b FROM \"kv\""));
            assertEquals("B, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE \"kv\""));
            engine.execute("DROP TABLE \"kv\"");
            assertEquals("1",
                rows("SELECT a FROM KV"));
            engine.execute("CREATE SEQUENCE SQ");
            engine.execute("CREATE SEQUENCE \"sq\"");
            engine.execute("CREATE STAGE ST");
            engine.execute("CREATE STAGE \"st\"");
            engine.execute("CREATE FUNCTION FN() RETURNS INT AS '1'");
            engine.execute("CREATE FUNCTION \"fn\"() RETURNS INT AS '2'");
            assertEquals("1, 2",
                rows("SELECT FN(), \"fn\"()"));
            engine.execute("CREATE TABLE \"a.b\" (x INT)");
            engine.execute("CREATE TABLE \"A.B\" (y INT)");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM \"a.b\""));
            assertEquals("0",
                rows("SELECT COUNT(*) FROM \"A.B\""));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P472_DB");
        }
    }
}
