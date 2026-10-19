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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A stage path with a doubled slash names no file: two slashes straight inside a stage reference are its
 * path's, not a comment, and a path segment is never empty — a LIST, a staged-file query and a REMOVE find
 * nothing there. An internal stage lists each file at the size it is stored at, its bytes padded to the
 * cipher's 16-byte block. Every answer is live-verified.
 */
public class StageListingPathTest extends MeasuredCellsTestSupport {

    @Test
    public void aDoubledSlashMatchesNothingAndSizesAreStoredOnes() {
        engine.execute("CREATE OR REPLACE STAGE lst");
        assertAnswer(
            "1|2|2",
            "COPY INTO @lst/plain FROM (SELECT 'x') FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer(
            "1|27|27",
            "COPY INTO @lst/dir/deep FROM (SELECT 'abcdefghijklmnopqrstuvwxyz') FILE_FORMAT = (TYPE = CSV "
                + "COMPRESSION = NONE) SINGLE = TRUE");
        assertAnswer(
            "1|27|47",
            "COPY INTO @lst/zipped FROM (SELECT 'abcdefghijklmnopqrstuvwxyz') FILE_FORMAT = (TYPE = CSV) "
                + "SINGLE = TRUE");
        engine.execute("LIST @lst");
        assertAnswer(
            "lst/dir/deep|32 ;; lst/plain|16 ;; lst/zipped|48",
            "SELECT \"name\", \"size\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        engine.execute("LIST @lst/plain");
        assertAnswer(
            "lst/plain|16",
            "SELECT \"name\", \"size\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        engine.execute("LIST @lst//plain");
        assertAnswer(
            "",
            "SELECT \"name\", \"size\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        engine.execute("LIST @lst/dir//deep");
        assertAnswer(
            "",
            "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        engine.execute("LIST @lst//");
        assertAnswer(
            "",
            "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        engine.execute("LIST @lst/dir/");
        assertAnswer(
            "lst/dir/deep|32",
            "SELECT \"name\", \"size\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM @lst//plain");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM @lst/plain");
        assertAnswer(
            "",
            "REMOVE @lst//plain");
        assertAnswer(
            "REFUSED DIRECTORY not enabled for the stage lst",
            "SELECT relative_path, size FROM DIRECTORY(@lst) ORDER BY 1");
        assertShape(
            "name:VARCHAR(16777216), size:NUMBER(38,0), md5:VARCHAR(16777216), last_modified:VARCHAR(16777216)",
            "LIST @lst");
    }
}
