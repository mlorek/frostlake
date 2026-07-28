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

    @Test
    public void ciCollationAppliesInAJoinOnCondition() {
        // The config-lookup shape: a lookup table keyed by an 'en-ci' column, joined on a computed
        // lowercase key. The ON equality must honor the column's collation — it silently compared
        // case-sensitively, so every LEFT JOIN row nulled and the loader concatenated ':<instance>'.
        engine.execute("CREATE TABLE cfg (module_id VARCHAR COLLATE 'en-ci', qualified VARCHAR)");
        engine.execute("INSERT INTO cfg VALUES ('VENDOR:MODULE::PUB', 'VENDOR:MODULE::PUB')");
        engine.execute("CREATE TABLE stg (lookup_key VARCHAR)");
        engine.execute("INSERT INTO stg VALUES ('vendor:module::pub'), ('other:key::x')");
        assertEquals(1L, count("""
            SELECT COUNT(l.qualified)
            FROM stg s LEFT JOIN cfg l ON l.module_id = s.lookup_key"""));
    }

    @Test
    public void ciCollationAppliesToAnUnqualifiedColumnInAJoin() {
        engine.execute("CREATE TABLE cfg3 (module_id VARCHAR COLLATE 'en-ci', v VARCHAR)");
        engine.execute("INSERT INTO cfg3 VALUES ('AbC', 'hit')");
        engine.execute("CREATE TABLE stg3 (k VARCHAR)");
        engine.execute("INSERT INTO stg3 VALUES ('aBc')");
        assertEquals(1L, count(
            "SELECT COUNT(l.v) FROM stg3 s INNER JOIN cfg3 l ON module_id = k"));
    }
}
