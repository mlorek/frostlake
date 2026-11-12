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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An unload takes {@code VALIDATION_MODE = RETURN_ROWS}, which "instructs the COPY command to return the results of the
 * query in the SQL statement instead of unloading the results", and is "the only supported validation option"
 * (documented): the query's rows come back and no file is written. Every other value stays refused.
 */
public class UnloadReturnRowsTest extends MeasuredCellsTestSupport {

    @Test
    public void returnRowsAnswersTheQueryAndWritesNothing() {
        engine.execute("CREATE TABLE urr_t (a INT, b VARCHAR)");
        engine.execute("INSERT INTO urr_t VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE STAGE urr_s");
        assertAnswer("1|x ;; 2|y", "COPY INTO @urr_s/u7/ FROM (SELECT * FROM urr_t ORDER BY a) "
            + "VALIDATION_MODE = RETURN_ROWS");
        assertAnswer("1|x ;; 2|y", "COPY INTO @urr_s/u7/ FROM (SELECT * FROM urr_t ORDER BY a LIMIT 5) "
            + "VALIDATION_MODE = 'return_rows'");
        assertAnswer("", "LIST @urr_s/u7/");
        assertAnswer("REFUSED SQL compilation error: | invalid value [RETURN_ERRORS] for parameter 'VALIDATION_MODE'",
            "COPY INTO @urr_s/u7/ FROM urr_t VALIDATION_MODE = RETURN_ERRORS");
    }
}
