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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A column declared with a case-insensitive collation ({@code COLLATE 'en-ci'}) compares equal regardless of
 * case, so {@code WHERE s = 'abc'} matches a stored {@code 'ABC'} — the collation is applied, not just stored.
 * (Scope: equality; ORDER BY / GROUP BY collation is a documented follow-on.)
 */
public class CollationCaseInsensitiveTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void caseInsensitiveEqualityMatchesRegardlessOfCase() {
        engine.execute("CREATE TABLE ct (s VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO ct VALUES ('ABC')");
        assertEquals(1L, count("SELECT COUNT(*) FROM ct WHERE s = 'abc'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM ct WHERE s = 'ABC'"));
        assertEquals(0L, count("SELECT COUNT(*) FROM ct WHERE s = 'xyz'"));
    }

    @Test
    public void withoutCiCollationEqualityIsCaseSensitive() {
        engine.execute("CREATE TABLE ct2 (s VARCHAR)");
        engine.execute("INSERT INTO ct2 VALUES ('ABC')");
        assertEquals(0L, count("SELECT COUNT(*) FROM ct2 WHERE s = 'abc'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM ct2 WHERE s = 'ABC'"));
    }
}
