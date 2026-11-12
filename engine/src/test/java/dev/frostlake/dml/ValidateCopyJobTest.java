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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VALIDATE(table, JOB_ID => …) "validates the files loaded in a past execution of the COPY INTO <table> command and
 * returns all the errors encountered during the load, rather than just the first error" (documented): the COPY its
 * query ID names, or {@code '_last'}, the session's last load. A COPY under ON_ERROR = ABORT_STATEMENT returns no
 * results. The function was unknown, so the rows a CONTINUE load skipped could not be read back.
 */
public class ValidateCopyJobTest extends MeasuredCellsTestSupport {

    private static final String ERRORS = "SELECT error, line, character, category, column_name, row_number, "
        + "rejected_record FROM TABLE(VALIDATE(vcj_t, JOB_ID => '%s'))";

    /** The rejected record is the line as the file holds it, its line break included. */
    private static final String REJECTED = "Numeric value 'x' is not recognized|3|1|conversion|\"VCJ_T\"[\"A\":1]|2|"
        + "x,two | ";

    @BeforeEach
    public void stage() {
        engine.execute("CREATE STAGE vcj_s FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("COPY INTO @vcj_s/bad.csv FROM (SELECT column1, column2 FROM VALUES ('1', 'one', 1), "
            + "('x', 'two', 2), ('3', 'three', 3) ORDER BY column3) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) "
            + "HEADER = TRUE SINGLE = TRUE");
        engine.execute("CREATE TABLE vcj_t (a INT, b VARCHAR)");
    }

    @Test
    public void theLastLoadsRejectedRowsReadBack() {
        engine.execute("COPY INTO vcj_t FROM @vcj_s ON_ERROR = CONTINUE");
        assertAnswer("1|one ;; 3|three", "SELECT a, b FROM vcj_t ORDER BY a");
        assertAnswer(REJECTED, String.format(ERRORS, "_last"));
    }

    @Test
    public void aLoadIsFoundByItsQueryId() {
        engine.execute("COPY INTO vcj_t FROM @vcj_s ON_ERROR = CONTINUE");
        final String id = String.valueOf(engine.executeQuery("SELECT LAST_QUERY_ID()").getRows().get(0).getValue(0));
        engine.execute("CREATE TABLE vcj_other (a INT)");
        assertAnswer(REJECTED, String.format(ERRORS, id));
    }

    @Test
    public void anAbortingLoadReturnsNothing() {
        engine.execute("CREATE TABLE vcj_text (a VARCHAR, b VARCHAR)");
        engine.execute("COPY INTO vcj_text FROM @vcj_s ON_ERROR = ABORT_STATEMENT");
        assertAnswer("", "SELECT * FROM TABLE(VALIDATE(vcj_text, JOB_ID => '_last'))");
    }
}
