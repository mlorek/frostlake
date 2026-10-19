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
package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A sequence nothing has drawn from serves its START value first, whatever its step is changed to in the meantime
 * (live-verified): the step is how far the NEXT value lies from the one served, not where the first one starts.
 */
public class SequenceFirstValueTest extends BaseDatabaseTest {

    private String nextValueShown(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW SEQUENCES LIKE '" + name + "'");
        return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("next_value")));
    }

    private String drawn(final String sequence) {
        return String.valueOf(engine.executeQuery("SELECT " + sequence + ".nextval").getRows().get(0).getValue(0));
    }

    @Test
    public void aChangedStepDoesNotMoveTheFirstValue() {
        engine.execute("CREATE SEQUENCE sfv_a START = 10");
        engine.execute("ALTER SEQUENCE sfv_a SET INCREMENT = 3");
        assertEquals("10", nextValueShown("SFV_A"));
        assertEquals("10", drawn("sfv_a"));
    }

    @Test
    public void aStepChangedTwiceStillStartsAtStart() {
        engine.execute("CREATE SEQUENCE sfv_b START = 10");
        engine.execute("ALTER SEQUENCE sfv_b SET INCREMENT = 5");
        engine.execute("ALTER SEQUENCE sfv_b SET INCREMENT = 7");
        assertEquals("10", nextValueShown("SFV_B"));
        assertEquals("10", drawn("sfv_b"));
    }

    @Test
    public void aNegativeStepStartsAtStartToo() {
        engine.execute("CREATE SEQUENCE sfv_c START = 10");
        engine.execute("ALTER SEQUENCE sfv_c SET INCREMENT = -2");
        assertEquals("10", nextValueShown("SFV_C"));
        assertEquals("10", drawn("sfv_c"));
    }
}
