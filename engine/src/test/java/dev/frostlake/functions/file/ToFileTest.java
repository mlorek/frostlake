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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@code TO_FILE} — building a FILE value.
 *
 * <p>Every expectation was measured against live Snowflake. The stage-path
 * tests read REAL file metadata — size, mtime, MD5 and the extension's content type — from a
 * {@code file://} stage, so they prove the descriptor is not synthesised from the path text.
 */
public class ToFileTest extends StagedFileTestSupport {

    private static final Logger logger = LoggerFactory.getLogger(ToFileTest.class);

    /**
     * Live: {@code TO_FILE('@sse/hello.txt')} returns the descriptor of the real file — content type
     * from the extension, size and MD5 from the bytes, and the stage fully qualified and upper-cased.
     */
    @Test
    public void buildsADescriptorFromARealStagedFile() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@st/hello.txt'))"));
        assertEquals("text/plain", scalar("SELECT FL_GET_CONTENT_TYPE(TO_FILE('@st/hello.txt'))"));
        assertEquals("24", scalar("SELECT FL_GET_SIZE(TO_FILE('@st/hello.txt'))"));
        // The live account's own hello.txt of exactly these bytes reported this ETAG, and it equalled
        // the md5 column of LIST @sse — so ETAG is the file's MD5, computed here over the real bytes.
        assertEquals("b7dddf722cfdc51710087d369f8d9e6b",
            scalar("SELECT FL_GET_ETAG(TO_FILE('@st/hello.txt'))"));
        assertEquals("@TEST_DB.TEST_SCHEMA.ST", scalar("SELECT FL_GET_STAGE(TO_FILE('@st/hello.txt'))"));
    }

    /**
     * Live: the CONTENT_TYPE comes from the file NAME's extension and never from its bytes. A file of
     * real PNG bytes named {@code .txt} is {@code text/plain}; a file of plain text named {@code .png}
     * is {@code image/png}. This is the pair that rules out content sniffing.
     */
    @Test
    public void contentTypeComesFromTheExtensionNotTheBytes() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stageBytes("png_named.txt", PNG_BYTES);
        stage("text_named.png", "this is not a png, it is plain text\n");

        assertEquals("text/plain", scalar("SELECT FL_GET_CONTENT_TYPE(TO_FILE('@st/png_named.txt'))"));
        assertEquals("image/png", scalar("SELECT FL_GET_CONTENT_TYPE(TO_FILE('@st/text_named.png'))"));
        // And the classification follows the content type, so the names decide the categories too.
        assertEquals("document", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/png_named.txt'))"));
        assertEquals("image", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/text_named.png'))"));
    }

    /** Live: a file with no extension at all is {@code application/octet-stream}, hence {@code unknown}. */
    @Test
    public void fileWithoutAnExtensionIsOctetStream() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("noext", "no extension here\n");

        assertEquals("application/octet-stream", scalar("SELECT FL_GET_CONTENT_TYPE(TO_FILE('@st/noext'))"));
        assertEquals("unknown", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/noext'))"));
    }

    /** Live: a file in a sub-directory keeps the sub-path in RELATIVE_PATH, with no leading slash. */
    @Test
    public void subDirectoryIsKeptInTheRelativePath() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("sub/nested.txt", "nested file\n");

        assertEquals("sub/nested.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@st/sub/nested.txt'))"));
        assertEquals("text/plain", scalar("SELECT FL_GET_CONTENT_TYPE(TO_FILE('@st/sub/nested.txt'))"));
    }

    /**
     * Live: {@code TO_FILE} VALIDATES existence — a missing file fails with Snowflake's own wording,
     * quoting the path exactly as the caller wrote it.
     */
    @Test
    public void missingFileRaisesSnowflakesNotFoundError() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE('@st/missing.txt')");
            }
        });
        assertTrue(error.getMessage().contains("Remote file '@st/missing.txt' was not found."),
            "unexpected message: " + error.getMessage());
        assertTrue(error.getMessage().contains("There are several potential causes."),
            "unexpected message: " + error.getMessage());
        logger.info("TO_FILE over a missing file: {}", error.getMessage());
    }

    /** Live: a DIRECTORY is not a file — both {@code @st/sub} and {@code @st/} fail "was not found". */
    @Test
    public void directoryIsNotAFile() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("sub/nested.txt", "nested file\n");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE('@st/sub')");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE('@st/')");
            }
        });
    }

    /**
     * Live: the two-argument form joins its arguments with a single slash and does nothing else — no
     * {@code @} is added and no doubled separator is collapsed, as the quoted paths in the errors show.
     */
    @Test
    public void twoArgumentFormJoinsWithASlash() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@st', 'hello.txt'))"));

        final RuntimeException doubled = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE('@st', '/hello.txt')");
            }
        });
        assertTrue(doubled.getMessage().contains("'@st//hello.txt'"),
            "unexpected message: " + doubled.getMessage());

        final RuntimeException noAt = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE('st', 'hello.txt')");
            }
        });
        assertTrue(noAt.getMessage().contains("'st/hello.txt'"),
            "unexpected message: " + noAt.getMessage());
    }

    /** Live: a database- or schema-qualified stage name resolves to the same fully-qualified STAGE. */
    @Test
    public void qualifiedStageNameResolves() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertEquals("@TEST_DB.TEST_SCHEMA.ST",
            scalar("SELECT FL_GET_STAGE(TO_FILE('@test_db.test_schema.st/hello.txt'))"));
        assertEquals("@TEST_DB.TEST_SCHEMA.ST",
            scalar("SELECT FL_GET_STAGE(TO_FILE('@test_schema.st/hello.txt'))"));
    }

    /** Live: {@code TO_FILE(NULL)} is NULL, with no error. */
    @Test
    public void nullInNullOut() {
        assertNull(scalar("SELECT TO_FILE(NULL)"));
    }

    /**
     * Live: the metadata-object form validates STRUCTURE but does not resolve the file — an object
     * naming a file that is not on the stage round-trips unchanged.
     */
    @Test
    public void metadataObjectIsAcceptedWithoutResolvingTheFile() {
        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(" + helloTextFile() + ")"));
        assertEquals("24", scalar("SELECT FL_GET_SIZE(" + helloTextFile() + ")"));
        assertEquals("@PROBE135_DB.S.SSE", scalar("SELECT FL_GET_STAGE(" + helloTextFile() + ")"));
    }

    /** Live: an unknown field is named in the error — "Invalid file metadata field A." */
    @Test
    public void unknownMetadataFieldIsRejectedByName() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE(OBJECT_CONSTRUCT('STAGE', '@D.S.ST',"
                    + " 'RELATIVE_PATH', 'x.txt', 'SIZE', 1, 'LAST_MODIFIED', '" + LAST_MODIFIED + "',"
                    + " 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1', 'A', 1))");
            }
        });
        assertTrue(error.getMessage().contains("Invalid file metadata field A."),
            "unexpected message: " + error.getMessage());
    }

    /**
     * Live: the missing fields are listed — exactly {@code CONTENT_TYPE}, {@code SIZE},
     * {@code LAST_MODIFIED} and {@code ETAG} are required; {@code RELATIVE_PATH} is NOT (it belongs
     * to the identity rule). Live's list order varies call to call, so each name is matched on its
     * own.
     */
    @Test
    public void missingRequiredFieldsAreListed() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT TO_FILE(OBJECT_CONSTRUCT('RELATIVE_PATH', 'hello.txt', 'SIZE', 24))");
            }
        });
        final String message = String.valueOf(error.getMessage());
        assertTrue(message.contains("Invalid file metadata. Missing required fields:"),
            "unexpected message: " + message);
        assertTrue(message.contains("LAST_MODIFIED"), "unexpected message: " + message);
        assertTrue(message.contains("CONTENT_TYPE"), "unexpected message: " + message);
        assertTrue(message.contains("ETAG"), "unexpected message: " + message);
        assertFalse(message.contains("RELATIVE_PATH"), "unexpected message: " + message);
    }

    /** Live: a descriptor must identify its file by stage-and-path or by URL. */
    @Test
    public void metadataObjectMustCarryAnIdentity() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE(OBJECT_CONSTRUCT('RELATIVE_PATH', 'x.txt',"
                    + " 'SIZE', 1, 'LAST_MODIFIED', '" + LAST_MODIFIED + "',"
                    + " 'CONTENT_TYPE', 'image/png', 'ETAG', 'e1'))");
            }
        });
        assertTrue(error.getMessage().contains("Invalid file metadata. Must provide (STAGE and"
                + " RELATIVE_PATH), SCOPED_FILE_URL, or STAGE_FILE_URL."),
            "unexpected message: " + error.getMessage());
    }

    /** Live: a non-object, non-path argument fails "Unsupported cast to FILE." */
    @Test
    public void numberArgumentIsNotCastableToFile() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT FL_GET_SIZE(42)");
            }
        });
        assertTrue(error.getMessage().contains("Unsupported cast to FILE."),
            "unexpected message: " + error.getMessage());
    }
}
