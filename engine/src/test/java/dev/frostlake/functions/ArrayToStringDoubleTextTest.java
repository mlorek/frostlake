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

/**
 * ARRAY_TO_STRING spells each element as that element is cast to text. A DOUBLE was spelled as Java prints a
 * double, {@code 0.12345670163631439}, {@code 3.0}, {@code 1.0E20}, where the account spells it as a FLOAT is cast:
 * {@code 0.1234567016}, {@code 3}, {@code 1e+20}. A nested array or object is spelled as the variant itself
 * displays, its DOUBLEs in their fifteen-decimal form; a decimal, a text and a BOOLEAN are unchanged. All
 * live-verified.
 */
public class ArrayToStringDoubleTextTest extends BaseDatabaseTest {

    private String text(final String expression) {
        return String.valueOf(engine.executeQuery("SELECT " + expression).getRows().get(0).getValue(0));
    }

    @Test
    public void aDoubleElementIsSpelledAsAFloatIsCast() {
        assertEquals("1.5|0.1234567016|3|1e+20|1e-07|123456789012.345", text("ARRAY_TO_STRING(ARRAY_CONSTRUCT("
            + "1.5::FLOAT, 0.12345670163631439::FLOAT, 3::FLOAT, 1e20::FLOAT, 1e-7::FLOAT, 123456789012.345::FLOAT), '|')"));
        assertEquals("1.5|0.1234567016|3|3|100|1",
            text("ARRAY_TO_STRING(PARSE_JSON('[1.5, 1.234567016363144e-01, 3.0, 3, 1e2, 1.0e0]'), '|')"));
    }

    @Test
    public void aNestedValueIsSpelledAsTheVariantDisplays() {
        assertEquals("[1.500000000000000e+00]|{\"k\":2.500000000000000e+00}", text("ARRAY_TO_STRING(ARRAY_CONSTRUCT("
            + "ARRAY_CONSTRUCT(1.5::FLOAT), OBJECT_CONSTRUCT('k', 2.5::FLOAT)), '|')"));
    }

    @Test
    public void theOtherFamiliesAreUnchanged() {
        assertEquals("1.5|0.12345670163631439|3|3",
            text("ARRAY_TO_STRING(ARRAY_CONSTRUCT(1.5, 0.12345670163631439, 3, 3.0), '|')"));
        assertEquals("1.5|a|true||2", text("ARRAY_TO_STRING(ARRAY_CONSTRUCT(1.5::FLOAT, 'a', TRUE, NULL, 2), '|')"));
    }
}
