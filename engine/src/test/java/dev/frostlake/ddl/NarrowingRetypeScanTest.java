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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Narrowing a NUMBER's precision with ALTER TABLE scans the DATA already in the column: the widths
 * alone never refuse a precision decrease — the stored values decide whether the sentence fires, and
 * the sentence names the two PRECISIONS, never a value (live-verified).
 *
 * <p>★ THE SCAN IS A DDL-TIME PASS OVER EVERY ROW. One offending value refuses the whole ALTER and
 * leaves the column untouched — a five-digit insert still lands afterwards, because the type never
 * changed. NULLs pass, an empty column narrows freely, and values that fit let the same narrowing
 * through.
 *
 * <p>★ THE DIRECTION REFUSALS OUTRANK THE DATA. A scale change and a varchar shrink each keep their
 * own data-independent sentence — a varchar may not shrink even when every value would fit.
 */
public class NarrowingRetypeScanTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void aStoredValuePastTheNewPrecisionRefusesTheAlter() {
        engine.execute("CREATE OR REPLACE TABLE nsc (s NUMBER(38,0))");
        engine.execute("INSERT INTO nsc VALUES (99), (1000)");
        assertEquals("SQL compilation error: cannot change column S from type NUMBER(38,0)"
            + " to NUMBER(2,0) because some existing values cannot be represented"
            + " using precision 2 instead of precision 38.\n",
            refusal("ALTER TABLE nsc ALTER COLUMN s SET DATA TYPE NUMBER(2,0)"));
        // The refusal left the column untouched: a five-digit value still lands.
        engine.execute("INSERT INTO nsc VALUES (12345)");
    }

    @Test
    public void fittingValuesLetTheSameNarrowingThrough() {
        engine.execute("CREATE OR REPLACE TABLE nsc2 (s NUMBER(38,0))");
        engine.execute("INSERT INTO nsc2 VALUES (99), (1000)");
        engine.execute("ALTER TABLE nsc2 ALTER COLUMN s SET DATA TYPE NUMBER(5,0)");
        assertEquals(2, engine.executeQuery("SELECT s FROM nsc2").getRows().size());
    }

    @Test
    public void anEmptyColumnAndANullOnlyColumnNarrowFreely() {
        engine.execute("CREATE OR REPLACE TABLE nsc3 (s NUMBER(38,0))");
        engine.execute("ALTER TABLE nsc3 ALTER COLUMN s SET DATA TYPE NUMBER(2,0)");
        engine.execute("CREATE OR REPLACE TABLE nsc4 (s NUMBER(38,0))");
        engine.execute("INSERT INTO nsc4 VALUES (NULL)");
        engine.execute("ALTER TABLE nsc4 ALTER COLUMN s SET DATA TYPE NUMBER(2,0)");
    }

    @Test
    public void precisionMayDropWhenTheScaleStays() {
        engine.execute("CREATE OR REPLACE TABLE nsc5 (p NUMBER(10,2))");
        engine.execute("INSERT INTO nsc5 VALUES (1.25)");
        engine.execute("ALTER TABLE nsc5 ALTER COLUMN p SET DATA TYPE NUMBER(4,2)");
    }

    @Test
    public void theDirectionRefusalsOutrankTheData() {
        engine.execute("CREATE OR REPLACE TABLE nsc6 (v VARCHAR(10), p NUMBER(10,2))");
        engine.execute("INSERT INTO nsc6 VALUES ('ab', 1.25)");
        // A varchar may not shrink even when every stored value fits the new length.
        assertEquals("SQL compilation error: cannot change column V from type VARCHAR(10)"
            + " to VARCHAR(8) because reducing the byte-length of a varchar is not supported.\n",
            refusal("ALTER TABLE nsc6 ALTER COLUMN v SET DATA TYPE VARCHAR(8)"));
        assertEquals("SQL compilation error: cannot change column P from type NUMBER(10,2)"
            + " to NUMBER(10,1) because changing the scale of a number is not supported.\n",
            refusal("ALTER TABLE nsc6 ALTER COLUMN p SET DATA TYPE NUMBER(10,1)"));
    }
}
