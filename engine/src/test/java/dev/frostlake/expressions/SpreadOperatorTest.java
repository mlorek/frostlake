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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code **} spread operator. It is argument SPLATTING, not an array feature — {@code […]} is sugar
 * for {@code ARRAY_CONSTRUCT}, so one rule serves both the array-literal and the function-argument forms,
 * and every function gets it (live-verified: {@code GREATEST(** [1,5,3])} → 5,
 * {@code ARRAY_APPEND(** [[1,2], 3])} splices TWO arguments).
 *
 * <p>The operand must be a CONSTANT array, decided structurally: an array literal or an
 * {@code ARRAY_CONSTRUCT} call. A runtime expression is refused even when its value is an array —
 * {@code ** PARSE_JSON('[1,2]')} fails live — as are a column, a scalar, a string and an OBJECT.
 */
public class SpreadOperatorTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private void assertRejected(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    /** A rejection whose message must carry Snowflake's non-constant-operand wording. */
    private void assertNonConstantOperand(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("spread argument with non-constant array input"),
            "expected the non-constant-array-input wording, got: " + error.getMessage());
    }

    @Test
    public void arrayLiteralSpreadSplicesElements() {
        assertEquals("[1,2,3,4]", String.valueOf(scalar("SELECT [** [1, 2], ** [3, 4]]")));
        assertEquals("[1,2,3,4]", String.valueOf(scalar("SELECT [1, 2, ** [3, 4]]")));
        assertEquals("[1,2,3]", String.valueOf(scalar("SELECT [** [1, 2], 3]")));
        assertEquals("[0,1,2,3,4,5]", String.valueOf(scalar("SELECT [0, ** [1, 2], 3, ** [4], 5]")));
        assertEquals("[1,2]", String.valueOf(scalar("SELECT [** [1, 2]]")));
    }

    @Test
    public void spreadOfEmptyArrayContributesNothing() {
        assertEquals("[]", String.valueOf(scalar("SELECT [** []]")));
        assertEquals("[7]", String.valueOf(scalar("SELECT [** [], 7]")));
    }

    @Test
    public void spreadIsOneLevelDeepAndKeepsNesting() {
        // Only the outer array is flattened; a nested array stays an element.
        assertEquals("[1,[2,3]]", String.valueOf(scalar("SELECT [** [1, [2, 3]]]")));
    }

    @Test
    public void spreadKeepsMixedElementTypes() {
        assertEquals("[\"a\",1,true]", String.valueOf(scalar("SELECT [** ['a', 1, TRUE]]")));
    }

    @Test
    public void arrayConstructCallIsAConstantOperand() {
        assertEquals("[1,2,3]", String.valueOf(scalar("SELECT [** ARRAY_CONSTRUCT(1, 2), 3]")));
    }

    @Test
    public void spreadAsFunctionArgumentsSplatsThem() {
        assertEquals("[3,4]", String.valueOf(scalar("SELECT ARRAY_CONSTRUCT(** [3, 4])")));
        assertEquals("[1,2,3,4]", String.valueOf(scalar("SELECT ARRAY_CONSTRUCT(1, ** [2, 3], 4)")));
        assertEquals("[1,2]", String.valueOf(scalar("SELECT ARRAY_CONSTRUCT(** [1], ** [2])")));
    }

    @Test
    public void spreadSplatsArgumentsOfOrdinaryFunctions() {
        // Not array-specific: the spliced elements become ordinary positional arguments.
        assertEquals("[1,2,3]", String.valueOf(scalar("SELECT ARRAY_APPEND(** [[1, 2], 3])")));
        assertEquals(5L, ((Number) scalar("SELECT GREATEST(** [1, 5, 3])")).longValue());
        assertEquals("ab", String.valueOf(scalar("SELECT CONCAT(** ['a', 'b'])")));
        assertEquals("{\"a\":1,\"b\":2}",
            String.valueOf(scalar("SELECT OBJECT_CONSTRUCT('a', 1, ** ['b', 2])")));
    }

    @Test
    public void splicedArrayBehavesLikeAnyOtherArray() {
        assertEquals(3L, ((Number) scalar("SELECT ARRAY_SIZE([** [1, 2], 3])")).longValue());
        assertEquals(1L, ((Number) scalar("SELECT ([** [1, 2], 3])[0]")).longValue());
    }

    @Test
    public void nonConstantOperandIsRejected() {
        // A runtime expression is refused even though its VALUE is an array (live-verified).
        assertNonConstantOperand("SELECT [** PARSE_JSON('[1,2]'), 3]");
        engine.execute("CREATE TABLE sp_a (a ARRAY)");
        engine.execute("INSERT INTO sp_a SELECT [10, 20]");
        assertNonConstantOperand("SELECT [** a, 99] FROM sp_a");
        assertNonConstantOperand("SELECT ARRAY_CONSTRUCT(** a, 99) FROM sp_a");
    }

    @Test
    public void nonArrayOperandIsRejected() {
        assertNonConstantOperand("SELECT [** 5]");
        assertNonConstantOperand("SELECT [** 'ab']");
        assertNonConstantOperand("SELECT [** OBJECT_CONSTRUCT('k', 'v')]");
    }

    @Test
    public void objectLiteralSpreadAndBareRowSpreadStayRejected() {
        // Snowflake has no object-literal spread and no bare/qualified row spread — all syntax errors.
        assertRejected("SELECT {'a': 1, ** {'b': 2}}");
        assertRejected("SELECT ** 5");
        engine.execute("CREATE TABLE sp_r (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO sp_r VALUES (1, 2)");
        assertRejected("SELECT sp_r.** FROM sp_r");
        assertRejected("SELECT ** sp_r FROM sp_r");
    }

    @Test
    public void plainArrayAndObjectLiteralsStillWork() {
        assertEquals("[3,4]", String.valueOf(scalar("SELECT [3, 4]")));
        assertEquals("{\"a\":1,\"b\":2}", String.valueOf(scalar("SELECT {'a': 1, 'b': 2}")));
    }
}
