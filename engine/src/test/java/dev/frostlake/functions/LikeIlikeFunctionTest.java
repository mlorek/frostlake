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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The function-call forms {@code LIKE(subject, pattern)} / {@code ILIKE(subject, pattern)} and the RLIKE ==
 * REGEXP_LIKE synonym — alternatives to the {@code subject LIKE pattern} operators. LIKE is case-sensitive,
 * ILIKE case-insensitive, RLIKE a regular-expression (anchored) match.
 */
public class LikeIlikeFunctionTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void likeFunctionIsCaseSensitive() {
        assertEquals(Boolean.TRUE, scalar("SELECT LIKE('High fidelity method', 'High%')"));
        assertEquals(Boolean.FALSE, scalar("SELECT LIKE('High fidelity method', 'high%')"));
        assertEquals(Boolean.TRUE, scalar("SELECT LIKE('abc', 'a_c')"));
    }

    @Test
    public void ilikeFunctionIsCaseInsensitive() {
        assertEquals(Boolean.TRUE, scalar("SELECT ILIKE('High fidelity method', 'high%')"));
        assertEquals(Boolean.TRUE, scalar("SELECT ILIKE('High fidelity method', '%FIDELITY method')"));
        assertEquals(Boolean.FALSE, scalar("SELECT ILIKE('High fidelity method', 'low%')"));
    }

    @Test
    public void rlikeFunctionIsRegexpLikeSynonym() {
        assertEquals(Boolean.TRUE, scalar("SELECT RLIKE('High fidelity method', '.*method')"));
        assertEquals(Boolean.FALSE, scalar("SELECT RLIKE('High fidelity method', '^low')"));
    }

    @Test
    public void nullArgumentsYieldNull() {
        assertNull(scalar("SELECT LIKE(NULL, 'a%')"));
        assertNull(scalar("SELECT ILIKE('a', NULL)"));
    }

    @Test
    public void functionFormUsableInWhere() {
        engine.execute("CREATE TABLE os (m VARCHAR)");
        engine.execute("INSERT INTO os VALUES ('High fidelity method'), ('low signal')");
        assertEquals(1, engine.executeQuery(
            "SELECT m FROM os WHERE ILIKE(m, '%fidelity method')").getRowCount());
    }

    @Test
    public void likeOperatorStillWorks() {
        // Regression: allowing LIKE/ILIKE as function names must not disturb the infix operators.
        assertEquals(Boolean.TRUE, scalar("SELECT 'abc' LIKE 'a%'"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'ABC' ILIKE 'abc'"));
    }

    @Test
    public void percentAndUnderscoreSpanNewlines() {
        // Snowflake's % and _ match ANY character including newlines — a multi-line subject must
        // match '%needle%' when the needle sits between newlines.
        assertEquals(Boolean.TRUE, scalar("SELECT '\\n The remote host is a virtual machine.\\n' "
            + "ILIKE '%The remote host is a virtual machine%'"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a\\nb' LIKE '%b%'"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a\\nb' LIKE 'a_b'"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a\\nb' LIKE '%c%'"));
    }
}
