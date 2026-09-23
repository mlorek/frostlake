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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

/**
 * A statement's answer on one line, for tests that pin many statements at once: every row's cells joined with
 * ", ", the rows with " | ", "no row" for an empty result, or the refusal's message with its line breaks as "|".
 */
final class QueryAnswers {

    private QueryAnswers() {
    }

    /**
     * The answer {@code engine} gives {@code sql}.
     *
     * @param engine the engine, embedded or standing in for the live account
     * @param sql    the statement
     * @return the answer on one line
     */
    static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.length() > 0 ? rows.toString() : "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /**
     * Asserts every {@code {statement, answer}} pair.
     *
     * @param engine the engine
     * @param cells  the pairs
     */
    static void assertCells(final DatabaseEngine engine, final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(engine, cell[0]), cell[0]);
        }
    }
}
