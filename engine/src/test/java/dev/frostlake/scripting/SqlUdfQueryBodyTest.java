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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL-UDF body is classified as a query vs a bare scalar expression by PARSING it (is it a
 * {@code queryStatement}?), not by a {@code startsWith("SELECT")} string prefix — so {@code WITH … SELECT}
 * CTE bodies are recognised as queries (the old prefix check fell through to expression evaluation).
 */
public class SqlUdfQueryBodyTest extends BaseDatabaseTest {

    private long scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void selectBodyExecutesAsQuery() {
        engine.execute("CREATE FUNCTION sel_one() RETURNS INTEGER AS 'SELECT 1'");
        assertEquals(1, scalar("SELECT sel_one()"));
    }

    @Test
    public void withCteBodyExecutesAsQuery() {
        // Body starts with WITH, not SELECT — the old startsWith("SELECT") missed it and fell through to
        // expression evaluation. Parsing recognises it as a query statement.
        engine.execute("CREATE FUNCTION cte_five() RETURNS INTEGER AS 'WITH c AS (SELECT 5 AS v) SELECT v FROM c'");
        assertEquals(5, scalar("SELECT cte_five()"));
    }

    @Test
    public void expressionBodyStillEvaluatesAsExpression() {
        engine.execute("CREATE FUNCTION dbl(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        assertEquals(42, scalar("SELECT dbl(21)"));
    }
}
