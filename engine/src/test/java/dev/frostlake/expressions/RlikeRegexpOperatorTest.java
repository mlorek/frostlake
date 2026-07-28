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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@code <subject> [NOT] RLIKE|REGEXP <pattern>} infix operators — Snowflake defines both as
 * REGEXP_LIKE(subject, pattern), a FULL-string regex match with SQL NULL propagation. The
 * pre-existing RLIKE(subject, pattern) function-call form must keep working alongside the
 * operator (RLIKE is also still usable as a plain identifier).
 */
public class RlikeRegexpOperatorTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void rlikeIsAFullStringRegexMatch() {
        assertEquals(Boolean.TRUE, scalar("SELECT 'san francisco' RLIKE 'san.*'"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'san francisco' RLIKE 'san'"),
            "RLIKE matches the WHOLE subject, not a substring");
        assertEquals(Boolean.FALSE, scalar("SELECT 'foo' RLIKE 'bar'"));
    }

    @Test
    public void regexpIsASynonym() {
        assertEquals(Boolean.FALSE, scalar("SELECT 'foo' REGEXP 'bar'"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'foo' REGEXP 'f.o'"));
    }

    @Test
    public void notVariantsNegate() {
        assertEquals(Boolean.TRUE, scalar("SELECT 'foo' NOT RLIKE 'bar'"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'foo' NOT REGEXP 'f.*'"));
    }

    @Test
    public void nullPropagates() {
        assertNull(scalar("SELECT NULL RLIKE 'a.*'"));
        assertNull(scalar("SELECT 'a' RLIKE NULL"));
    }

    @Test
    public void operatorWorksOverColumns() {
        engine.execute("CREATE TABLE rl (s VARCHAR, p VARCHAR)");
        engine.execute("INSERT INTO rl VALUES ('abc', 'a.c'), ('abc', 'z.*')");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM rl WHERE s RLIKE p");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void functionCallFormStillWorks() {
        assertEquals(Boolean.TRUE, scalar("SELECT RLIKE('san francisco', 'san.*')"));
        assertEquals(Boolean.FALSE, scalar("SELECT RLIKE('san francisco', 'nope')"));
    }
}
