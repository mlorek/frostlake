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
 * {@code FL_GET_STAGE_FILE_URL(file)} — the descriptor's {@code STAGE_FILE_URL} field.
 *
 * <p>Every expectation was measured against live Snowflake, and the key
 * measured fact is that this is a PLAIN DESCRIPTOR FIELD and nothing more. It does not build a URL. A
 * descriptor that {@code TO_FILE} produced from a stage PATH simply does not carry the field, so live
 * the accessor returns NULL for such a file — including on a stage created with
 * {@code DIRECTORY = (ENABLE = TRUE)}. That NULL is Snowflake's real answer, not an engine shortfall.
 * The field is only ever populated by a CALLER-SUPPLIED metadata object, and then it is handed straight
 * back.
 *
 * <p>This getter PROPAGATES NULL: {@code FL_GET_STAGE_FILE_URL(NULL)} and
 * {@code FL_GET_STAGE_FILE_URL(TO_FILE(NULL))} are both NULL. That CONTRASTS with
 * {@code FL_GET_FILE_TYPE}, which answers {@code 'unknown'} for a NULL file, and with the
 * {@code FL_IS_*} family, which answers FALSE.
 */
public class FlGetStageFileUrlTest extends StagedFileTestSupport {

    private String stageFileUrl(final String fileExpression) {
        return scalar("SELECT FL_GET_STAGE_FILE_URL(" + fileExpression + ")");
    }

    /** Live: the descriptor of a real staged file carries no URL field at all, so the answer is NULL. */
    @Test
    public void descriptorFromAStagePathHasNoStageFileUrl() {
        assertNull(stageFileUrl(helloTextFile()));
    }

    /**
     * Live: supply the field and it comes straight back. A URL identifies the file on its own, so this
     * descriptor carries no STAGE — which is exactly why the field can be read in isolation.
     */
    @Test
    public void returnsACallerSuppliedStageFileUrl() {
        assertEquals("https://example/u", stageFileUrl(urlFile("STAGE_FILE_URL", "https://example/u")));
    }

    /**
     * Live: {@code TO_FILE} over a REAL staged file never populates the URL fields — neither this one nor
     * {@code SCOPED_FILE_URL} — however the stage was created.
     */
    @Test
    public void realStagedFileReturnsNullForBothUrlAccessors() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertNull(stageFileUrl("TO_FILE('@st/hello.txt')"));
        assertNull(scalar("SELECT FL_GET_SCOPED_FILE_URL(TO_FILE('@st/hello.txt'))"));
    }

    /** Live: NULL in, NULL out — unlike FL_GET_FILE_TYPE and the FL_IS_* family. */
    @Test
    public void propagatesNull() {
        assertNull(stageFileUrl("NULL"));
        assertNull(stageFileUrl("TO_FILE(NULL)"));
    }

    /** A descriptor identified by URL alone, in the live-verified shape: no STAGE, one URL field. */
    private static String urlFile(final String field, final String url) {
        return "TRY_TO_FILE(OBJECT_CONSTRUCT('RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1',"
            + " '" + field + "', '" + url + "'))";
    }
}
