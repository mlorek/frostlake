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

package dev.frostlake.types;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A NUMBER prints its digits in place on every text surface (live-verified): 0.00000001 stays
 * {@code 0.00000001} and a zero at scale twenty is twenty zeros — never java.math's scientific
 * {@code 1E-8} / {@code 0E-20}, which {@code BigDecimal.toString} switches to once the exponent falls
 * below minus six and which used to leak through TO_VARCHAR, the VARCHAR cast, concatenation and
 * the driver's getString.
 */
public class SmallNumberTextTest extends BaseDatabaseTest {

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aSmallOrZeroNumberPrintsItsDigits() {
        assertEquals("0.00000000000000000000", cell("SELECT TO_VARCHAR(0::NUMBER(38,20))"));
        assertEquals("0.00000000000000000000", cell("SELECT (0::NUMBER(38,20))::VARCHAR"));
        assertEquals("0.00000000000000000000", cell("SELECT 0::NUMBER(38,20) || ''"));
        assertEquals("0.00000000000000000000", cell("SELECT TO_VARCHAR((0.0::FLOAT)::NUMBER(38,20))"));
        assertEquals("0.00000001", cell("SELECT TO_VARCHAR(0.00000001::NUMBER(10,8))"));
        assertEquals("0.0000001", cell("SELECT TO_VARCHAR(0.0000001)"));
        assertEquals("0.00000001", cell("SELECT (0.00000001::NUMBER(10,8))::VARCHAR"));
        assertEquals("0.00000000000000000000",
            cell("SELECT TO_VARCHAR(VARIANCE_POP(f)::NUMBER(38,20)) FROM (SELECT 0.1::FLOAT AS f)"));
    }
}
