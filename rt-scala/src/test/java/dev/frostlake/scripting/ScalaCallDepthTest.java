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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How deep Scala procedures run inside one another: sixteen in full, and a seventeenth is ended before its code
 * runs, since the session it is handed issues a statement as it opens. The body takes Snowpark's Java Session,
 * which a Scala handler may name as well as the Scala API's. Each recursion here ends by its own counter. The frames
 * an uncaught refusal is worded with differ between the account and this engine, so a refusal is asked for its
 * sentence (live-verified).
 */
public class ScalaCallDepthTest extends MeasuredCellsTestSupport {

    private static final String REFUSAL = "Stored procedure execution error: Stored procedure exceeds the maximum "
        + "allowed number of recursive calls.";

    private void assertEnded(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED ") && answered.contains(REFUSAL), answered);
    }

    @Test
    public void sixteenBodiesRunAndTheSeventeenthIsEndedAsItOpens() {
        engine.execute("CREATE OR REPLACE PROCEDURE sc_rec(n INT) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.Session\nobject H {\n  def run(s: Session, n: Int): Int =\n    if (n <= 0) "
            + "0 else s.sql(s\"CALL sc_rec(${n - 1})\").collect()(0).getInt(0) + 1\n}\n$$");
        assertAnswer("14", "CALL sc_rec(14)");
        assertAnswer("15", "CALL sc_rec(15)");
        // The seventeenth procedure would answer 0 without a statement of its own.
        assertEnded("CALL sc_rec(16)");
        assertEnded("CALL sc_rec(20)");
    }
}
