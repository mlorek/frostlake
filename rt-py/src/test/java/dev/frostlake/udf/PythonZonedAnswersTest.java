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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Python handler's datetime answered for a zoned timestamp, by a function, a procedure and a table function alike
 * (live-verified). A TIMESTAMP_TZ takes only an aware datetime and keeps its offset - a naive one is refused, Datetime
 * object must be aware to be convertible to TIMESTAMP_TZ - and a TIMESTAMP_LTZ only a naive one, whose wall clock is
 * read in the session's zone: one a gap skips moves on past the gap, one a fold repeats takes the earlier offset. A
 * date is no datetime for either.
 */
public class PythonZonedAnswersTest extends MeasuredCellsTestSupport {

    /** Naive, aware, date, skipped and repeated answers in two zones. */
    @Test
    public void zonedAnswers() {
        engine.execute("CREATE OR REPLACE FUNCTION pftz799(w VARCHAR) RETURNS TIMESTAMP_TZ LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'f' AS $$\nimport datetime\ndef f(w):\n    if w == 'aware':\n        "
            + "return datetime.datetime(2024, 7, 2, 3, 4, 5, 6000, "
            + "tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n    if w == 'gap':\n        return "
            + "datetime.datetime(2024, 3, 10, 2, 30, 0)\n    if w == 'fold':\n        return datetime.datetime(2024, "
            + "11, 3, 1, 30, 0)\n    if w == 'date':\n        return datetime.date(2024, 7, 2)\n    return "
            + "datetime.datetime(2024, 7, 2, 3, 4, 5, 6000)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pfltz799(w VARCHAR) RETURNS TIMESTAMP_LTZ LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'f' AS $$\nimport datetime\ndef f(w):\n    if w == 'aware':\n        "
            + "return datetime.datetime(2024, 7, 2, 3, 4, 5, 6000, "
            + "tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n    if w == 'gap':\n        return "
            + "datetime.datetime(2024, 3, 10, 2, 30, 0)\n    if w == 'fold':\n        return datetime.datetime(2024, "
            + "11, 3, 1, 30, 0)\n    if w == 'date':\n        return datetime.date(2024, 7, 2)\n    return "
            + "datetime.datetime(2024, 7, 2, 3, 4, 5, 6000)\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pptz799(w VARCHAR) RETURNS TIMESTAMP_TZ LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'f' AS $$\nimport "
            + "datetime\ndef f(session, w):\n    if w == 'aware':\n        return datetime.datetime(2024, 7, 2, 3, 4, "
            + "5, 6000, tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n    if w == 'gap':\n        return "
            + "datetime.datetime(2024, 3, 10, 2, 30, 0)\n    if w == 'fold':\n        return datetime.datetime(2024, "
            + "11, 3, 1, 30, 0)\n    if w == 'date':\n        return datetime.date(2024, 7, 2)\n    return "
            + "datetime.datetime(2024, 7, 2, 3, 4, 5, 6000)\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE ppltz799(w VARCHAR) RETURNS TIMESTAMP_LTZ LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'f' AS $$\nimport "
            + "datetime\ndef f(session, w):\n    if w == 'aware':\n        return datetime.datetime(2024, 7, 2, 3, 4, "
            + "5, 6000, tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n    if w == 'gap':\n        return "
            + "datetime.datetime(2024, 3, 10, 2, 30, 0)\n    if w == 'fold':\n        return datetime.datetime(2024, "
            + "11, 3, 1, 30, 0)\n    if w == 'date':\n        return datetime.date(2024, 7, 2)\n    return "
            + "datetime.datetime(2024, 7, 2, 3, 4, 5, 6000)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION pttz799(w VARCHAR) RETURNS TABLE (v TIMESTAMP_TZ) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'H' AS $$\nimport datetime\ndef f(w):\n    if w == 'aware':\n        "
            + "return datetime.datetime(2024, 7, 2, 3, 4, 5, 6000, "
            + "tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n    if w == 'gap':\n        return "
            + "datetime.datetime(2024, 3, 10, 2, 30, 0)\n    if w == 'fold':\n        return datetime.datetime(2024, "
            + "11, 3, 1, 30, 0)\n    if w == 'date':\n        return datetime.date(2024, 7, 2)\n    return "
            + "datetime.datetime(2024, 7, 2, 3, 4, 5, 6000)\n\nclass H:\n    def process(self, w):\n        yield "
            + "(f(w),)\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION ptltz799(w VARCHAR) RETURNS TABLE (v TIMESTAMP_LTZ) LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' HANDLER = 'H' AS $$\nimport datetime\ndef f(w):\n    if w == 'aware':\n        "
            + "return datetime.datetime(2024, 7, 2, 3, 4, 5, 6000, "
            + "tzinfo=datetime.timezone(datetime.timedelta(hours=5)))\n    if w == 'gap':\n        return "
            + "datetime.datetime(2024, 3, 10, 2, 30, 0)\n    if w == 'fold':\n        return datetime.datetime(2024, "
            + "11, 3, 1, 30, 0)\n    if w == 'date':\n        return datetime.date(2024, 7, 2)\n    return "
            + "datetime.datetime(2024, 7, 2, 3, 4, 5, 6000)\n\nclass H:\n    def process(self, w):\n        yield "
            + "(f(w),)\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'",
                "Statement executed successfully."},
            {"SELECT REPLACE((pftz799('naive'))::VARCHAR, '-', '/')",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PFTZ799 with "
                + "handler f"},
            {"SELECT REPLACE((pfltz799('naive'))::VARCHAR, '-', '/')",
                "2024/07/02 03:04:05.006 +0900"},
            {"CALL pptz799('naive')",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PPTZ799 with "
                + "handler f"},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("CALL ppltz799('naive')");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/07/02 03:04:05.006 +0900"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(pttz799('naive'))",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PTTZ799 with "
                + "handler H"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(ptltz799('naive'))",
                "2024/07/02 03:04:05.006 +0900"},
            {"SELECT REPLACE((pftz799('aware'))::VARCHAR, '-', '/')",
                "2024/07/02 03:04:05.006 +0500"},
            {"SELECT REPLACE((pfltz799('aware'))::VARCHAR, '-', '/')",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_LTZ in function PFLTZ799 with "
                + "handler f"},
        });
        engine.execute("CALL pptz799('aware')");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/07/02 03:04:05.006 +0500"},
            {"CALL ppltz799('aware')",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_LTZ in function PPLTZ799 with "
                + "handler f"},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertCells(new String[][] {
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(pttz799('aware'))",
                "2024/07/02 03:04:05.006 +0500"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(ptltz799('aware'))",
                "REFUSED Datetime object must be naive to be convertible to TIMESTAMP_LTZ in function PTLTZ799 with "
                + "handler H"},
            {"SELECT REPLACE((pftz799('date'))::VARCHAR, '-', '/')",
                "REFUSED Return value should be a datetime object in function PFTZ799 with handler f"},
            {"SELECT REPLACE((pfltz799('date'))::VARCHAR, '-', '/')",
                "REFUSED Return value should be a datetime object in function PFLTZ799 with handler f"},
            {"CALL pptz799('date')",
                "REFUSED Return value should be a datetime object in function PPTZ799 with handler f"},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertCells(new String[][] {
            {"CALL ppltz799('date')",
                "REFUSED Return value should be a datetime object in function PPLTZ799 with handler f"},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertCells(new String[][] {
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(pttz799('date'))",
                "REFUSED Return value should be a datetime object in function PTTZ799 with handler H"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(ptltz799('date'))",
                "REFUSED Return value should be a datetime object in function PTLTZ799 with handler H"},
            {"ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'",
                "Statement executed successfully."},
            {"SELECT REPLACE((pftz799('gap'))::VARCHAR, '-', '/')",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PFTZ799 with "
                + "handler f"},
            {"SELECT REPLACE((pfltz799('gap'))::VARCHAR, '-', '/')",
                "2024/03/10 03:30:00.000 /0700"},
            {"CALL pptz799('gap')",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PPTZ799 with "
                + "handler f"},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("CALL ppltz799('gap')");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/03/10 03:30:00.000 /0700"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(pttz799('gap'))",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PTTZ799 with "
                + "handler H"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(ptltz799('gap'))",
                "2024/03/10 03:30:00.000 /0700"},
            {"SELECT REPLACE((pftz799('fold'))::VARCHAR, '-', '/')",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PFTZ799 with "
                + "handler f"},
            {"SELECT REPLACE((pfltz799('fold'))::VARCHAR, '-', '/')",
                "2024/11/03 01:30:00.000 /0700"},
            {"CALL pptz799('fold')",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PPTZ799 with "
                + "handler f"},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("CALL ppltz799('fold')");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/11/03 01:30:00.000 /0700"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(pttz799('fold'))",
                "REFUSED Datetime object must be aware to be convertible to TIMESTAMP_TZ in function PTTZ799 with "
                + "handler H"},
            {"SELECT REPLACE(v::VARCHAR, '-', '/') FROM TABLE(ptltz799('fold'))",
                "2024/11/03 01:30:00.000 /0700"},
            {"SELECT REPLACE((SYSTEM$TYPEOF(pfltz799('naive')))::VARCHAR, '-', '/')",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'DD.MM.YYYY HH24:MI'",
                "Statement executed successfully."},
            {"SELECT REPLACE((pfltz799('naive'))::VARCHAR, '-', '/')",
                "02.07.2024 03:04"},
            {"ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
