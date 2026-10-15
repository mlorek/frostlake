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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A long chain of ANDs over compared calls is typed in time that grows with its length, not with two to its
 * length: the interval each operand carries is worked out once, not once for every operator above it.
 */
public class LongConjunctionTest extends BaseDatabaseTest {

    private static String chain(final int terms, final String term) {
        final StringBuilder conjunction = new StringBuilder();
        for (int i = 0; i < terms; i++) {
            if (i > 0) {
                conjunction.append(" AND ");
            }
            conjunction.append(term);
        }
        return conjunction.toString();
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void aLongChainOfComparedCallsAnswersAtOnce() {
        assertEquals("true", scalar("SELECT " + chain(40, "UPPER('x') = 'X'")));
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void aLongChainOfCallsOverACommonTableExpressionAnswersAtOnce() {
        assertEquals("true", scalar("WITH d AS (SELECT 'C0 NUMBER(38,0)' AS g) SELECT "
            + chain(40, "CONTAINS(g, 'C0')") + " FROM d"));
    }
}
