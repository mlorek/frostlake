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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A NUMBER/DECIMAL cast or column must honor its declared (precision, scale): round to the scale (HALF_UP)
 * and reject values whose integer part overflows the precision — matching Snowflake.
 */
public class NumberScalePrecisionTest extends BaseDatabaseTest {

    private double scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void castRoundsHalfUpToScale() {
        assertEquals(1.99, scalar("SELECT 1.987::NUMBER(10,2)"), 1e-9);
        assertEquals(1.99, scalar("SELECT CAST(1.987 AS NUMBER(10,2))"), 1e-9);
    }

    @Test
    public void castValueThatFitsTheScaleIsPreserved() {
        assertEquals(1.5, scalar("SELECT 1.5::NUMBER(10,2)"), 1e-9);
    }

    @Test
    public void castIntegerPartOverflowThrows() {
        // 12345.6 rounded to NUMBER(3,0) needs 5 integer digits > 3 -> out of range
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 12345.6::NUMBER(3,0)");
            }
        });
    }

    @Test
    public void insertRoundsToDeclaredColumnScale() {
        engine.execute("CREATE TABLE nums (v NUMBER(5,2))");
        engine.execute("INSERT INTO nums VALUES (123.456)");
        assertEquals(123.46, scalar("SELECT v FROM nums"), 1e-9);
    }

    @Test
    public void doubleColumnValueIsNotRounded() {
        engine.execute("CREATE TABLE dbls (d DOUBLE)");
        engine.execute("INSERT INTO dbls VALUES (1.23456789)");
        assertEquals(1.23456789, scalar("SELECT d FROM dbls"), 1e-12);
    }
}
