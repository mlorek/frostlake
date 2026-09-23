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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared assertions of the suites that pin how a FROM clause of several relations resolves its names and
 * positions: a result read as text, and a refusal matched by the fragments of its message.
 */
public abstract class JoinScopeTestSupport extends BaseDatabaseTest {

    /**
     * Every row of a result, columns joined by '|' and rows by ';'.
     *
     * @param sql the query
     * @return the rows as text, empty for none
     */
    protected final String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            for (int c = 0; c < rs.getColumnCount(); c++) {
                text.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
            }
        }
        return text.toString();
    }

    /**
     * Asserts that a statement is refused with a message holding every fragment.
     *
     * @param sql       the statement
     * @param fragments the parts the message must hold
     */
    protected final void assertRefused(final String sql, final String... fragments) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        for (final String fragment : fragments) {
            assertTrue(String.valueOf(error.getMessage()).contains(fragment), sql + " -> " + error.getMessage());
        }
    }
}
