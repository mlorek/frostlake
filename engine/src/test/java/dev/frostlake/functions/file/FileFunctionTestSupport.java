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

package dev.frostlake.functions.file;

import dev.frostlake.BaseDatabaseTest;

/**
 * Shared scaffolding for the FILE-function suites, and the reason most of them run on BOTH backends.
 *
 * <p>A FILE value can be built two ways, and only one of them needs a file to exist. {@code TO_FILE}
 * over a stage PATH must resolve a real staged file, which on a live account means a PUT and on this
 * engine means a {@code file://} stage — a Frostlake-only extension for local testing, so those tests
 * are engine-only and say so via {@link #stageOnlyReason()}. But {@code TO_FILE} / {@code TRY_TO_FILE}
 * over a METADATA OBJECT resolves nothing (an object naming a file that is
 * not on the stage round-trips fine), so every rule that reads a descriptor — the whole classification
 * surface, the NULL policies, and each accessor's field — is expressed here against a hand-built
 * descriptor and runs unchanged against live Snowflake.
 */
public abstract class FileFunctionTestSupport extends BaseDatabaseTest {

    /** A live-account LAST_MODIFIED, in the RFC-1123 shape Snowflake requires. */
    protected static final String LAST_MODIFIED = "Mon, 03 Aug 2026 11:24:13 GMT";

    /**
     * A SQL expression for a valid FILE value with the given content type, built from a metadata object
     * so it needs no staged file. The stage and path are nominal — no existing file is implied.
     *
     * @param contentType the descriptor's CONTENT_TYPE, or null for a descriptor without one
     * @return a SQL expression of type FILE
     */
    protected static String fileOf(final String contentType) {
        return "TRY_TO_FILE(OBJECT_CONSTRUCT('STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.dat',"
            + " 'SIZE', 1, 'LAST_MODIFIED', '" + LAST_MODIFIED + "',"
            + " 'CONTENT_TYPE', " + (contentType == null ? "NULL" : "'" + contentType + "'")
            + ", 'ETAG', 'e1'))";
    }

    /**
     * A SQL expression for the descriptor Snowflake actually produced for {@code @sse/hello.txt}
     * (live), so accessors can be asserted against real captured field values.
     *
     * @return a SQL expression of type FILE
     */
    protected static String helloTextFile() {
        return "TRY_TO_FILE(OBJECT_CONSTRUCT("
            + "'CONTENT_TYPE', 'text/plain',"
            + " 'ETAG', 'b7dddf722cfdc51710087d369f8d9e6b',"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "',"
            + " 'RELATIVE_PATH', 'hello.txt',"
            + " 'SIZE', 24,"
            + " 'STAGE', '@PROBE135_DB.S.SSE'))";
    }

    /** Why a stage-path test cannot run against a live account. */
    protected static String stageOnlyReason() {
        return "TO_FILE over a stage PATH needs a staged file; this engine stages via a file:// URL "
            + "stage, which is a Frostlake local-testing extension a live account does not accept";
    }

    /** The first column of the first row, as text. */
    protected String scalar(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }
}
