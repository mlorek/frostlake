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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * SUM / AVG over a VARIANT column return DOUBLE (live-verified: SUM of PARSE_JSON('1') and
 * PARSE_JSON('2') is 3.0 with SYSTEM$TYPEOF FLOAT), even for whole-number JSON values.
 */
public class SumVariantTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vt (v VARIANT)");
        // INSERT..VALUES rejects semi-structured expressions — populate via INSERT..SELECT.
        engine.execute("INSERT INTO vt SELECT PARSE_JSON('1') UNION ALL SELECT PARSE_JSON('2')");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void sumOverVariantColumnIsDouble() {
        final Object result = scalar("SELECT SUM(v) FROM vt");
        assertInstanceOf(Double.class, result, "SUM over VARIANT is DOUBLE in Snowflake");
        assertEquals(3.0, result);
    }

    @Test
    public void avgOverVariantColumnIsDouble() {
        final Object result = scalar("SELECT AVG(v) FROM vt");
        assertInstanceOf(Double.class, result, "AVG over VARIANT takes the double path");
        assertEquals(1.5, result);
    }
}
