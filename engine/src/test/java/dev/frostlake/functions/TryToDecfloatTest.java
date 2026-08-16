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
 * TRY_TO_DECFLOAT is TO_DECFLOAT answering NULL where TO_DECFLOAT fails on the value — a text that spells
 * no number, a format model the text does not fit, a model that is not one — and it reads text as
 * TO_DECFLOAT does, not as TRY_TO_DOUBLE: a non-finite word, a d suffix and hex digits are NULL here.
 * Every cell is live-verified.
 */
public class TryToDecfloatTest extends BaseDatabaseTest {

    private Object value(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private double number(final String sql) {
        return ((Number) value(sql)).doubleValue();
    }

    @Test
    public void aNumberInTextConverts() {
        assertEquals(1.5, number("SELECT TRY_TO_DECFLOAT('1.5')"));
        assertEquals(1.5, number("SELECT TRY_TO_DECFLOAT(' 1.5 ')"));
        assertEquals(100000.0, number("SELECT TRY_TO_DECFLOAT('1e5', '9EEEE')"));
    }

    @Test
    public void whatToDecfloatRefusesIsNull() {
        assertNull(value("SELECT TRY_TO_DECFLOAT('abc')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('123', '99')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('1e5', '9e9')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('1', '9+')"));
    }

    @Test
    public void itIsNotTryToDouble() {
        assertNull(value("SELECT TRY_TO_DECFLOAT('inf')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('nan')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('1d')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('0x10')"));
        assertEquals(Double.POSITIVE_INFINITY, number("SELECT TRY_TO_DOUBLE('inf')"));
        assertEquals(1.0, number("SELECT TRY_TO_DOUBLE('1d')"));
    }
}
