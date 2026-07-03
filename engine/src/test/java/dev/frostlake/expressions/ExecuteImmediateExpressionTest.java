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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EXECUTE IMMEDIATE used as an expression — previously it parsed but the AST builder threw "not ported". The
 * SQL string (a literal, bind variable, or concatenation) is evaluated, USING values bind to its {@code ?}
 * placeholders, the dynamic statement runs, and its first column of the first row is returned as a scalar.
 */
public class ExecuteImmediateExpressionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20), (3, 30)");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void literalSqlReturnsScalar() {
        assertEquals(1L, ((Number) scalar("SELECT EXECUTE IMMEDIATE 'SELECT 1'")).longValue());
    }

    @Test
    public void dynamicQueryReturnsAggregate() {
        assertEquals(60L, ((Number) scalar("SELECT EXECUTE IMMEDIATE 'SELECT SUM(v) FROM t'")).longValue());
    }

    @Test
    public void usingBindingsSubstitutePlaceholders() {
        assertEquals(20L, ((Number) scalar(
            "SELECT EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = ?' USING (2)")).longValue());
    }

    @Test
    public void sqlStringMayBeAConcatenation() {
        assertEquals(30L, ((Number) scalar(
            "SELECT EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = ' || '3'")).longValue());
    }

    @Test
    public void emptyResultYieldsNull() {
        assertNull(scalar("SELECT EXECUTE IMMEDIATE 'SELECT v FROM t WHERE id = 99'"));
    }
}
