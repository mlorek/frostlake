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
 * {@code FL_GET_RELATIVE_PATH(file)} — the descriptor's {@code RELATIVE_PATH} field, the file's path
 * WITHIN its stage.
 *
 * <p>Every expectation was measured against live Snowflake. The path is
 * relative and stays relative: a file in a sub-directory keeps the sub-path, and there is no leading
 * slash and no stage name in it — the stage lives in its own field, which {@code FL_GET_STAGE} reads.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_RELATIVE_PATH(NULL)} and
 * {@code FL_GET_RELATIVE_PATH(TO_FILE(NULL))} are both NULL. That CONTRASTS with
 * {@code FL_GET_FILE_TYPE}, which answers {@code 'unknown'} for a NULL file, and with the
 * {@code FL_IS_*} family, which answers FALSE.
 */
public class FlGetRelativePathTest extends StagedFileTestSupport {

    private String relativePath(final String fileExpression) {
        return scalar("SELECT FL_GET_RELATIVE_PATH(" + fileExpression + ")");
    }

    /** Live: {@code @sse/hello.txt} reported the bare {@code hello.txt} — stage stripped, no slash. */
    @Test
    public void readsTheDescriptorsRelativePath() {
        assertEquals("hello.txt", relativePath(helloTextFile()));
    }

    /** Live: a file at the stage root reports just its name, with no leading slash. */
    @Test
    public void stageRootFileIsJustItsName() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertEquals("hello.txt", relativePath("TO_FILE('@st/hello.txt')"));
    }

    /** Live: a file in a sub-directory keeps the sub-path, still with no leading slash. */
    @Test
    public void subDirectoryIsKeptInThePath() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("sub/nested.txt", "nested file\n");
        stage("sub/deeper/leaf.txt", "deeper file\n");

        assertEquals("sub/nested.txt", relativePath("TO_FILE('@st/sub/nested.txt')"));
        assertEquals("sub/deeper/leaf.txt", relativePath("TO_FILE('@st/sub/deeper/leaf.txt')"));
    }

    /** Live: NULL in, NULL out — unlike FL_GET_FILE_TYPE and the FL_IS_* family. */
    @Test
    public void propagatesNull() {
        assertNull(relativePath("NULL"));
        assertNull(relativePath("TO_FILE(NULL)"));
    }
}
