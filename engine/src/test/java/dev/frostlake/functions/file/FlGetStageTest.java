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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@code FL_GET_STAGE(file)} — the descriptor's {@code STAGE} field, the stage the file lives on.
 *
 * <p>Every expectation was measured against live Snowflake. A NAMED stage is
 * always rendered FULLY QUALIFIED and UPPER-CASED however the caller spelled it, so {@code @st},
 * {@code @test_schema.st} and {@code @test_db.test_schema.st} all report the one canonical name. The
 * other two stage kinds render differently: the user stage comes back as {@code @"~"}, and a table stage
 * as the bare table name such as {@code @TSTG} — neither is qualified or {@code @}-prefixed beyond that.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_STAGE(NULL)} and {@code FL_GET_STAGE(TO_FILE(NULL))} are
 * both NULL. That CONTRASTS with {@code FL_GET_FILE_TYPE}, which answers {@code 'unknown'} for a NULL
 * file, and with the {@code FL_IS_*} family, which answers FALSE.
 */
public class FlGetStageTest extends StagedFileTestSupport {

    private String stageOf(final String fileExpression) {
        return scalar("SELECT FL_GET_STAGE(" + fileExpression + ")");
    }

    /** Live: {@code @sse/hello.txt} reported its stage fully qualified and upper-cased. */
    @Test
    public void readsTheDescriptorsStage() {
        assertEquals("@PROBE135_DB.S.SSE", stageOf(helloTextFile()));
    }

    /**
     * Live: the spelling of the stage reference does not survive into the descriptor. Bare, schema- and
     * database-qualified references to the SAME stage all canonicalise to one fully-qualified,
     * upper-cased name.
     */
    @Test
    public void namedStageIsAlwaysFullyQualifiedAndUpperCased() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertEquals("@TEST_DB.TEST_SCHEMA.ST", stageOf("TO_FILE('@st/hello.txt')"));
        assertEquals("@TEST_DB.TEST_SCHEMA.ST", stageOf("TO_FILE('@test_schema.st/hello.txt')"));
        assertEquals("@TEST_DB.TEST_SCHEMA.ST", stageOf("TO_FILE('@test_db.test_schema.st/hello.txt')"));
    }

    /** Live: every file on the stage reports the same STAGE, sub-directories included. */
    @Test
    public void subDirectoryFileReportsTheSameStage() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("sub/nested.txt", "nested file\n");

        assertEquals("@TEST_DB.TEST_SCHEMA.ST", stageOf("TO_FILE('@st/sub/nested.txt')"));
    }

    /** Live: NULL in, NULL out — unlike FL_GET_FILE_TYPE and the FL_IS_* family. */
    @Test
    public void propagatesNull() {
        assertNull(stageOf("NULL"));
        assertNull(stageOf("TO_FILE(NULL)"));
    }
}
