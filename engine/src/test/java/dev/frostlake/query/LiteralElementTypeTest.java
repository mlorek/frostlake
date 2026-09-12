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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An array or object literal is built from its elements' values, as ARRAY_CONSTRUCT and OBJECT_CONSTRUCT
 * build theirs: a FLOAT element stays a DOUBLE and displays in its exponent form, a DECIMAL stays a
 * DECIMAL, a temporal keeps its type, and a string stays a string however much its text looks like JSON.
 * A SQL NULL element is undefined, a NULL-valued pair is dropped, and keys are sorted. Every cell is
 * live-verified.
 */
public class LiteralElementTypeTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** An array or object literal's elements keep their own types: a FLOAT stays a DOUBLE, nested or not. */
    @Test
    public void aLiteralKeepsEachElementsOwnType() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P457_DB");
            assertEquals("DOUBLE",
                rows("SELECT TYPEOF([1.5::FLOAT][0])"));
            assertEquals("DOUBLE",
                rows("SELECT TYPEOF({'a': 1.5::FLOAT}:a)"));
            assertEquals("DOUBLE",
                rows("SELECT TYPEOF([SQRT(2)][0])"));
            assertEquals("[1.500000000000000e+00,2,\"x\"]",
                rows("SELECT [1.5::FLOAT, 2, 'x']"));
            assertEquals("{\"a\":1.500000000000000e+00,\"b\":2}",
                rows("SELECT {'a': 1.5::FLOAT, 'b': 2}"));
            assertEquals("[1.414213562373095e+00]",
                rows("SELECT [SQRT(2)]"));
            assertEquals("DECIMAL",
                rows("SELECT TYPEOF([1.5][0])"));
            assertEquals("[1.5]",
                rows("SELECT [1.5]"));
            assertEquals("INTEGER",
                rows("SELECT TYPEOF([2][0])"));
            assertEquals("[1,undefined,2]",
                rows("SELECT [1, NULL, 2]"));
            assertEquals("{\"b\":1}",
                rows("SELECT {'a': NULL, 'b': 1}"));
            assertEquals("{\"a\":2,\"b\":1}",
                rows("SELECT {'b': 1, 'a': 2}"));
            assertEquals("[true,\"x\",\"2024-01-01\"]",
                rows("SELECT [TRUE, 'x', '2024-01-01'::DATE]"));
            assertEquals("BOOLEAN, DATE, VARCHAR",
                rows("SELECT TYPEOF([TRUE][0]), TYPEOF(['2024-01-01'::DATE][0]), TYPEOF(['x'][0])"));
            assertEquals("[[1.500000000000000e+00]]",
                rows("SELECT [[1.5::FLOAT]]"));
            assertEquals("{\"a\":[1.500000000000000e+00]}",
                rows("SELECT {'a': [1.5::FLOAT]}"));
            assertEquals("DOUBLE",
                rows("SELECT TYPEOF([[1.5::FLOAT]][0][0])"));
            assertEquals("[1.500000000000000e+00], DOUBLE",
                rows("SELECT [PARSE_JSON('1.5e0')], TYPEOF([PARSE_JSON('1.5e0')][0])"));
            assertEquals("true",
                rows("SELECT [1.5::FLOAT] = ARRAY_CONSTRUCT(1.5::FLOAT)"));
            assertEquals("1, [undefined]",
                rows("SELECT ARRAY_SIZE([NULL]), [NULL]"));
            assertEquals("[1.500000000000000e+00,1.5]",
                rows("SELECT [1.5::FLOAT, 1.5]"));
            assertEquals("DECIMAL",
                rows("SELECT TYPEOF({'a': 1.5::FLOAT, 'b': 1.5}:b)"));
            assertEquals("[100], INTEGER",
                rows("SELECT [1e2], TYPEOF([1e2][0])"));
            assertEquals("[3.000000000000000e-01]",
                rows("SELECT [0.1::FLOAT + 0.2::FLOAT]"));
            assertEquals("{\"k\":1.414213562373095e+00}",
                rows("SELECT {'k': SQRT(2)}::VARCHAR"));
            assertEquals("[1.5], DECIMAL",
                rows("SELECT [1.5::NUMBER(5,3)], TYPEOF([1.5::NUMBER(5,3)][0])"));
            assertEquals("[\"a\",1,2.500000000000000e+00]",
                rows("SELECT ['a', 1::VARIANT, TO_VARIANT(2.5::FLOAT)]"));
            assertEquals("[1.000000000000000e+20]",
                rows("SELECT [100000000000000000000::FLOAT]"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P457_DB");
        }
    }

    /** A string element stays a string however much it looks like JSON; a temporal keeps its type. */
    @Test
    public void aStringElementStaysAStringAndATypedOneKeepsItsType() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P457B_DB");
            assertEquals("[\"[1,2]\"], VARCHAR",
                rows("SELECT ['[1,2]'], TYPEOF(['[1,2]'][0])"));
            assertEquals("{\"a\":\"{\\\"b\\\":1}\"}, VARCHAR",
                rows("SELECT {'a': '{\"b\":1}'}, TYPEOF({'a': '{\"b\":1}'}:a)"));
            assertEquals("[\"null\"], VARCHAR",
                rows("SELECT ['null'], TYPEOF(['null'][0])"));
            assertEquals("[null], NULL_VALUE",
                rows("SELECT [PARSE_JSON('null')], TYPEOF([PARSE_JSON('null')][0])"));
            assertEquals("[{\"a\":1}], [[1.500000000000000e+00]]",
                rows("SELECT [OBJECT_CONSTRUCT('a', 1)], [ARRAY_CONSTRUCT(1.5::FLOAT)]"));
            assertEquals("[\"x\",\"y\"]",
                rows("SELECT ['x', TO_VARIANT('y')]"));
            assertEquals("[\"2024-01-01 10:00:00.000\"], TIMESTAMP_NTZ",
                rows("SELECT [TO_TIMESTAMP_NTZ('2024-01-01 10:00:00')], TYPEOF([TO_TIMESTAMP_NTZ('2024-01-01 10:00:00')][0])"));
            assertEquals("{\"a\":[1,undefined]}",
                rows("SELECT {'a': [1, NULL]}"));
            assertEquals("{\"a\":{\"b\":2.500000000000000e+00}}",
                rows("SELECT {'a': {'c': NULL, 'b': 2.5::FLOAT}}"));
            assertEquals("2",
                rows("SELECT ARRAY_SIZE([1.5::FLOAT, NULL])"));
            assertEquals("2.5, DOUBLE",
                rows("SELECT [1.5::FLOAT][0] + 1, TYPEOF([1.5::FLOAT][0] + 1)"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P457B_DB");
        }
    }
}
