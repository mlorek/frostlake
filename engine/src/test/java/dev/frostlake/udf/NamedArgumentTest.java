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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Named function arguments — {@code f(name => value)} — previously threw "not yet ported". A call now maps
 * each named argument to the parameter of that name (case-insensitively) and reorders the values to the
 * function's declared parameter order, so order-sensitive UDFs give the right answer regardless of the
 * written order. Leading positional arguments are still allowed before the named ones.
 */
public class NamedArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Order-sensitive bodies make reordering observable: a - b, and a||b||c.
        engine.execute("CREATE FUNCTION sub_it(a INTEGER, b INTEGER) RETURNS INTEGER AS 'a - b'");
        engine.execute("CREATE FUNCTION cat3(a VARCHAR, b VARCHAR, c VARCHAR) RETURNS VARCHAR AS 'a || b || c'");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void positionalStillWorks() {
        assertEquals(7L, ((Number) scalar("SELECT sub_it(10, 3)")).longValue());
    }

    @Test
    public void namedInDeclaredOrder() {
        assertEquals(7L, ((Number) scalar("SELECT sub_it(a => 10, b => 3)")).longValue());
    }

    @Test
    public void namedReorderedMatchesParameterNames() {
        // Written b-first, a-second, but the result is a - b = 10 - 3 = 7 (not 3 - 10).
        assertEquals(7L, ((Number) scalar("SELECT sub_it(b => 3, a => 10)")).longValue());
    }

    @Test
    public void threeWayReorder() {
        assertEquals("123", scalar("SELECT cat3(c => '3', a => '1', b => '2')").toString());
    }

    @Test
    public void mixedPositionalThenNamed() {
        assertEquals(7L, ((Number) scalar("SELECT sub_it(10, b => 3)")).longValue());
    }

    @Test
    public void namedArgumentIsCaseInsensitive() {
        assertEquals(7L, ((Number) scalar("SELECT sub_it(B => 3, A => 10)")).longValue());
    }

    @Test
    public void namedArgumentsOverColumns() {
        engine.execute("CREATE TABLE t (x INTEGER, y INTEGER)");
        engine.execute("INSERT INTO t VALUES (100, 40)");
        assertEquals(60L, ((Number) scalar("SELECT sub_it(b => y, a => x) FROM t")).longValue());
    }

    @Test
    public void unknownArgumentNameThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT sub_it(a => 10, z => 3)");
            }
        });
    }
}
