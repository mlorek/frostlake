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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@code TRY_TO_FILE(...)} — {@code TO_FILE} with every failure turned into NULL.
 *
 * <p>Every expectation was measured against live Snowflake. Valid input gives the identical descriptor, so the ONLY difference between the
 * two functions is what happens on bad input — which is why each case below asserts the NULL and the
 * matching {@code TO_FILE} error side by side. The structural failures (an unknown field, a missing
 * required field, no identity) and the resolution failure (a stage path naming no file) are covered;
 * LAST_MODIFIED is NOT one of them — any string is accepted verbatim.
 */
public class TryToFileTest extends StagedFileTestSupport {

    /** A valid descriptor object, as SQL, with one field overridden — for the LAST_MODIFIED cases. */
    private static String objectWithLastModified(final String lastModified) {
        return "OBJECT_CONSTRUCT('STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + lastModified + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1')";
    }

    /** An otherwise-valid descriptor object carrying one field Snowflake does not define. */
    private static String objectWithUnknownField() {
        return "OBJECT_CONSTRUCT('STAGE', '@D.S.ST', 'RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain',"
            + " 'ETAG', 'e1', 'A', 1)";
    }

    /** A descriptor object that names neither a STAGE nor a URL, so it identifies no file. */
    private static String objectWithoutIdentity() {
        return "OBJECT_CONSTRUCT('RELATIVE_PATH', 'x.txt', 'SIZE', 1,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1')";
    }

    private void assertToFileFails(final String argument, final String expectedFragment) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_FILE(" + argument + ")");
            }
        });
        assertTrue(error.getMessage().contains(expectedFragment),
            "unexpected message: " + error.getMessage());
    }

    /**
     * Live: on VALID input the two functions are interchangeable — {@code TRY_TO_FILE} of the descriptor
     * Snowflake produced for {@code @sse/hello.txt} reads back field for field.
     */
    @Test
    public void validMetadataObjectGivesTheSameDescriptorAsToFile() {
        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(" + helloTextFile() + ")"));
        assertEquals("24", scalar("SELECT FL_GET_SIZE(" + helloTextFile() + ")"));
        assertEquals("text/plain", scalar("SELECT FL_GET_CONTENT_TYPE(" + helloTextFile() + ")"));
        assertEquals("@PROBE135_DB.S.SSE", scalar("SELECT FL_GET_STAGE(" + helloTextFile() + ")"));
    }

    /** Live: {@code TRY_TO_FILE(NULL)} is NULL, exactly as {@code TO_FILE(NULL)} is. */
    @Test
    public void nullInNullOut() {
        assertNull(scalar("SELECT TRY_TO_FILE(NULL)"));
    }

    /** Live: an unknown field is a NULL here and "Invalid file metadata field A." for {@code TO_FILE}. */
    @Test
    public void unknownFieldIsNullInsteadOfAnError() {
        assertNull(scalar("SELECT TRY_TO_FILE(" + objectWithUnknownField() + ")"));
        assertToFileFails(objectWithUnknownField(), "Invalid file metadata field A.");
    }

    /**
     * Live: missing required fields are a NULL here and a listing of them for {@code TO_FILE} —
     * matched per name because live's list order varies call to call.
     */
    @Test
    public void missingRequiredFieldsAreNullInsteadOfAnError() {
        final String object = "OBJECT_CONSTRUCT('RELATIVE_PATH', 'hello.txt', 'SIZE', 24)";
        assertNull(scalar("SELECT TRY_TO_FILE(" + object + ")"));
        assertToFileFails(object, "Invalid file metadata. Missing required fields:");
        assertToFileFails(object, "LAST_MODIFIED");
        assertToFileFails(object, "CONTENT_TYPE");
        assertToFileFails(object, "ETAG");
    }

    /**
     * Live: a descriptor with every required field but no STAGE and no URL identifies no file — NULL
     * here, and the "Must provide (STAGE and RELATIVE_PATH), SCOPED_FILE_URL, or STAGE_FILE_URL." error
     * for {@code TO_FILE}.
     */
    @Test
    public void missingIdentityIsNullInsteadOfAnError() {
        assertNull(scalar("SELECT TRY_TO_FILE(" + objectWithoutIdentity() + ")"));
        assertToFileFails(objectWithoutIdentity(), "Invalid file metadata. Must provide (STAGE and"
            + " RELATIVE_PATH), SCOPED_FILE_URL, or STAGE_FILE_URL.");
    }

    /**
     * Live: LAST_MODIFIED is NOT validated — the RFC-1123 shape, the ISO
     * timestamp {@code 11:24:13} and even free text all round-trip verbatim through the
     * constructor. Leniency lives in {@code FL_GET_LAST_MODIFIED}, which parses what it can and
     * yields NULL for the rest.
     */
    @Test
    public void lastModifiedIsAcceptedVerbatim() {
        assertEquals("x.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TRY_TO_FILE("
            + objectWithLastModified(LAST_MODIFIED) + "))"));
        // A descriptor with each once-"invalid" shape still IS a file — both constructors accept it.
        assertEquals("x.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TRY_TO_FILE("
            + objectWithLastModified("2026-08-03 11:24:13") + "))"));
        assertEquals("x.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TRY_TO_FILE("
            + objectWithLastModified("not a date") + "))"));
        assertEquals("x.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TO_FILE("
            + objectWithLastModified("not a date") + "))"));
    }

    /**
     * Live: over a stage PATH the difference is resolution, not structure — an existing file gives the
     * same descriptor {@code TO_FILE} gives, and a missing one is NULL where {@code TO_FILE} raises
     * Snowflake's "Remote file … was not found." error.
     */
    @Test
    public void resolvesAStagedFileAndNullsAMissingOne() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());
        stage("hello.txt", "hello world\nsecond line\n");

        assertEquals("hello.txt", scalar("SELECT FL_GET_RELATIVE_PATH(TRY_TO_FILE('@st/hello.txt'))"));
        assertEquals("24", scalar("SELECT FL_GET_SIZE(TRY_TO_FILE('@st/hello.txt'))"));

        assertNull(scalar("SELECT TRY_TO_FILE('@st/missing.txt')"));
        assertToFileFails("'@st/missing.txt'", "Remote file '@st/missing.txt' was not found.");
    }
}
