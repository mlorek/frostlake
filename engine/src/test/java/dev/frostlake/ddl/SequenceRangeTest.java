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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A sequence's values stay inside the 64-bit range: a draw whose next step would leave it fails, and so does every draw
 * after it (live-verified). After ALTER SEQUENCE ... SET INCREMENT the value already computed comes next and the new
 * step applies from there (documented; the account's own values may skip ahead, as it caches them).
 */
public class SequenceRangeTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return refused.getMessage();
    }

    private String first(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private static String exceeded(final String sequence) {
        return "Sequence value has exceeded valid range in sequence object " + sequence + ".";
    }

    @Test
    public void aDrawWhoseNextStepLeavesTheRangeFails() {
        engine.execute("CREATE SEQUENCE s_max START = 9223372036854775806 INCREMENT = 1");
        assertEquals("9223372036854775806", first("SELECT s_max.NEXTVAL"));
        assertEquals(exceeded("S_MAX"), refusal("SELECT s_max.NEXTVAL"));
        assertEquals(exceeded("S_MAX"), refusal("SELECT s_max.NEXTVAL"));
        engine.execute("CREATE SEQUENCE s_big START = 1 INCREMENT = 9223372036854775807");
        assertEquals(exceeded("S_BIG"), refusal("SELECT s_big.NEXTVAL"));
        engine.execute("CREATE SEQUENCE s_min START = -9223372036854775807 INCREMENT = -1");
        assertEquals("-9223372036854775807", first("SELECT s_min.NEXTVAL"));
        assertEquals(exceeded("S_MIN"), refusal("SELECT s_min.NEXTVAL"));
    }

    @Test
    public void aDefaultThatDrawsPastTheRangeFailsItsInsert() {
        engine.execute("CREATE SEQUENCE s_tbl START = 9223372036854775806");
        engine.execute("CREATE TABLE sq (i INT DEFAULT s_tbl.NEXTVAL, j INT)");
        engine.execute("INSERT INTO sq (j) VALUES (1)");
        assertEquals(exceeded("S_TBL"), refusal("INSERT INTO sq (j) VALUES (2)"));
        assertEquals("1", first("SELECT COUNT(*) FROM sq"));
    }

    @Test
    public void setIncrementServesTheComputedValueFirst() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account caches sequence values, so its own skip ahead");
        engine.execute("CREATE SEQUENCE seq_wrap START = 1 INCREMENT = 1");
        engine.execute("CREATE TABLE wraparound (i INT DEFAULT seq_wrap.NEXTVAL, j INT)");
        engine.execute("INSERT INTO wraparound (j) VALUES (1)");
        engine.execute("INSERT INTO wraparound (j) VALUES (2)");
        engine.execute("INSERT INTO wraparound (j) VALUES (3)");
        engine.execute("ALTER SEQUENCE seq_wrap SET INCREMENT = -4");
        engine.execute("INSERT INTO wraparound (j) VALUES (4)");
        engine.execute("INSERT INTO wraparound (j) VALUES (5)");
        final StringBuilder values = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT i FROM wraparound ORDER BY j").getRows()) {
            values.append(values.length() > 0 ? "," : "").append(row.getValue(0));
        }
        assertEquals("1,2,3,4,0", values.toString());
    }
}
