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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAP_CONSTRUCT(key, value …) — the one member of the family that BUILDS a map. Every expectation here
 * was measured live.
 */
public class MapConstructTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void buildsAMapFromAlternatingArguments() {
        assertEquals("{\"a\":1,\"b\":2}", String.valueOf(scalar("SELECT MAP_CONSTRUCT('a',1,'b',2)")));
    }

    @Test
    public void membersComeOutKeySortedNotInArgumentOrder() {
        assertEquals("{\"a\":2,\"b\":1}", String.valueOf(scalar("SELECT MAP_CONSTRUCT('b',1,'a',2)")));
    }

    @Test
    public void theResultIsAMapTheRestOfTheFamilyCanRead() {
        assertEquals("2", String.valueOf(scalar("SELECT MAP_SIZE(MAP_CONSTRUCT('a',1,'b',2))")));
        assertEquals("[\"a\",\"b\"]", String.valueOf(scalar(
            "SELECT MAP_KEYS(MAP_CONSTRUCT('a',1,'b',2))")));
    }

    @Test
    public void anOddArgumentCountIsRejected() {
        // Live: "not enough arguments for function [MAP_CONSTRUCT], expected 4, got 3" — the count named
        // is the next EVEN one rather than a maximum.
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_CONSTRUCT('a',1,'b')");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("expected 4, got 3"),
            "expected Snowflake's pairing error, got: " + error.getMessage());
    }

    @Test
    public void fewerThanTwoArgumentsIsRejected() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_CONSTRUCT('a')");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("expected 2, got 1"),
            "expected Snowflake's arity error, got: " + error.getMessage());
    }

    @Test
    public void aNullLiteralArgumentIsRejectedInEitherHalf() {
        assertNullArgumentRejected("SELECT MAP_CONSTRUCT(NULL,1)");
        assertNullArgumentRejected("SELECT MAP_CONSTRUCT('a',NULL)");
    }

    @Test
    public void aNullKeyDropsThePairWhileANullValueIsKept() {
        // The RUN-TIME rule, measured over a two-row table so that no all-NULL column could be folded
        // to a NULL literal: a NULL key drops the pair, a NULL value is stored as a JSON null.
        engine.execute("CREATE TABLE mcn (id INTEGER, k VARCHAR, v INTEGER)");
        engine.execute("INSERT INTO mcn VALUES (1, 'a', NULL), (2, NULL, 1), (3, 'b', 2)");
        assertEquals("{\"a\":null}",
            String.valueOf(scalar("SELECT MAP_CONSTRUCT(k, v) FROM mcn WHERE id = 1")));
        assertEquals("1", String.valueOf(scalar("SELECT MAP_SIZE(MAP_CONSTRUCT(k, v)) FROM mcn WHERE id = 1")));
        assertEquals("{}", String.valueOf(scalar("SELECT MAP_CONSTRUCT(k, v) FROM mcn WHERE id = 2")));
        assertEquals("{\"b\":2}",
            String.valueOf(scalar("SELECT MAP_CONSTRUCT(k, v) FROM mcn WHERE id = 3")));
    }

    @Test
    public void aBooleanDateOrVariantKeyIsRejectedThoughAllThreeAreFineAsValues() {
        // Measured both ways round live: the KEY half refuses each of the three, the VALUE
        // half takes all three.
        assertUnsupportedArgument("SELECT MAP_CONSTRUCT(TRUE,'x')", "BOOLEAN");
        assertUnsupportedArgument("SELECT MAP_CONSTRUCT('2020-01-01'::DATE,'x')", "DATE");
        assertUnsupportedArgument("SELECT MAP_CONSTRUCT('a'::VARIANT,1)", "VARIANT");
        assertEquals("{\"a\":true}", String.valueOf(scalar("SELECT MAP_CONSTRUCT('a',TRUE)")));
        assertEquals("{\"a\":\"2020-01-01\"}",
            String.valueOf(scalar("SELECT MAP_CONSTRUCT('a','2020-01-01'::DATE)")));
        assertEquals("{\"a\":1}", String.valueOf(scalar("SELECT MAP_CONSTRUCT('a',1::VARIANT)")));
    }

    @Test
    public void aDuplicateKeyIsAnError() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT MAP_CONSTRUCT('a',1,'a',2)");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("Duplicate field key 'a'"),
            "expected Snowflake's duplicate-key error, got: " + error.getMessage());
    }

    @Test
    public void aSemiStructuredValueIsKeptWhole() {
        assertEquals("{\"a\":{\"x\":1}}", String.valueOf(scalar(
            "SELECT MAP_CONSTRUCT('a',OBJECT_CONSTRUCT('x',1))")));
        assertEquals("{\"a\":[1,2]}", String.valueOf(scalar(
            "SELECT MAP_CONSTRUCT('a',ARRAY_CONSTRUCT(1,2))")));
    }

    private void assertUnsupportedArgument(final String sql, final String typeName) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Function MAP_CONSTRUCT does not support " + typeName + " argument type"),
            "expected Snowflake's unsupported-type error for [" + sql + "], got: " + error.getMessage());
    }

    private void assertNullArgumentRejected(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Function MAP_CONSTRUCT does not support NULL argument type"),
            "expected Snowflake's NULL-argument error for [" + sql + "], got: " + error.getMessage());
    }
}
