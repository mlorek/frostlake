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
 * The ANSI {@code EXTRACT(<part> FROM <expr>)} syntax is accepted (desugared to the two-argument
 * {@code EXTRACT('<part>', <expr>)} form), in addition to the comma form. Previously only the comma
 * form parsed.
 */
public class ExtractFromSyntaxTest extends BaseDatabaseTest {

    private long extract(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void fromSyntaxOnDate() {
        assertEquals(5, extract("SELECT EXTRACT(month FROM '2023-05-08'::DATE)"));
        assertEquals(2023, extract("SELECT EXTRACT(year FROM '2023-05-08'::DATE)"));
        assertEquals(8, extract("SELECT EXTRACT(day FROM '2023-05-08'::DATE)"));
    }

    @Test
    public void fromSyntaxOnTimestamp() {
        assertEquals(14, extract("SELECT EXTRACT(hour FROM '2023-05-08 14:30:00'::TIMESTAMP)"));
        assertEquals(30, extract("SELECT EXTRACT(minute FROM '2023-05-08 14:30:00'::TIMESTAMP)"));
    }

    @Test
    public void commaFormStillWorks() {
        assertEquals(5, extract("SELECT EXTRACT('month', '2023-05-08'::DATE)"));
    }

    @Test
    public void fromSyntaxInsideExpression() {
        assertEquals(2028, extract("SELECT EXTRACT(year FROM '2023-05-08'::DATE) + 5"));
    }
}
