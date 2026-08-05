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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Builds and validates the file-metadata object that IS a FILE value.
 *
 * <p>Measured live: {@code SELECT TO_FILE('@sse/hello.txt')} yields exactly
 * <pre>{@code
 * {"CONTENT_TYPE":"text/plain","ETAG":"b7dddf722cfdc51710087d369f8d9e6b",
 *  "LAST_MODIFIED":"Mon, 03 Aug 2026 11:24:13 GMT","RELATIVE_PATH":"hello.txt",
 *  "SIZE":24,"STAGE":"@PROBE135_DB.S.SSE"}
 * }</pre>
 * — keys in alphabetical order, {@code SIZE} a JSON number, {@code LAST_MODIFIED} an RFC-1123 HTTP
 * date in GMT with a ZERO-PADDED two-digit day (note {@code LIST @stage} formats the same instant with
 * an unpadded day, so the two are not interchangeable), and {@code ETAG} the file's MD5 hex — verified
 * equal to the {@code md5} column of {@code LIST @stage} for every staged file on a server-side
 * encrypted stage.
 *
 * <p>Frostlake's stages are real local directories, so every one of those fields is read from the
 * actual file: {@code SIZE} from its length, {@code LAST_MODIFIED} from its mtime, {@code ETAG} from
 * its MD5, {@code CONTENT_TYPE} from its extension (see {@link FileContentTypes}). Nothing here is
 * synthesised from the path text.
 *
 * <p>The two URL fields are the exception. {@code SCOPED_FILE_URL} and {@code STAGE_FILE_URL} are
 * absent from a descriptor {@code TO_FILE} builds from a stage path — live, both
 * {@code FL_GET_STAGE_FILE_URL} and {@code FL_GET_SCOPED_FILE_URL} return NULL for such a file, even on
 * a directory-enabled stage. They are plain descriptor FIELDS that only a caller-supplied metadata
 * object populates (proved by feeding {@code TRY_TO_FILE} an object carrying them and reading them
 * straight back). So the accessors read the field and Frostlake leaves it unset — returning NULL is the
 * measured behaviour, not a shortfall.
 */
public final class FileDescriptor {

    /** The descriptor's {@code CONTENT_TYPE} field. */
    public static final String CONTENT_TYPE = "CONTENT_TYPE";
    /** The descriptor's {@code ETAG} field — the file's MD5 hex. */
    public static final String ETAG = "ETAG";
    /** The descriptor's {@code LAST_MODIFIED} field — an RFC-1123 date string. */
    public static final String LAST_MODIFIED = "LAST_MODIFIED";
    /** The descriptor's {@code RELATIVE_PATH} field. */
    public static final String RELATIVE_PATH = "RELATIVE_PATH";
    /** The descriptor's {@code SIZE} field — a number of bytes. */
    public static final String SIZE = "SIZE";
    /** The descriptor's {@code STAGE} field, e.g. {@code @DB.SCHEMA.NAME}. */
    public static final String STAGE = "STAGE";
    /** The descriptor's {@code SCOPED_FILE_URL} field — only ever caller-supplied. */
    public static final String SCOPED_FILE_URL = "SCOPED_FILE_URL";
    /** The descriptor's {@code STAGE_FILE_URL} field — only ever caller-supplied. */
    public static final String STAGE_FILE_URL = "STAGE_FILE_URL";

    /**
     * Snowflake's message when a stage path names no existing file, reproduced verbatim (live)
     *. {@code TO_FILE} raises it; {@code TRY_TO_FILE} returns NULL instead — that is the
     * only difference between the two.
     */
    private static final String NOT_FOUND_SUFFIX = "' was not found. There are several potential causes."
        + " The file might not exist. The required credentials may be missing or invalid. If you are"
        + " running a copy command, please make sure files are not deleted when they are being loaded"
        + " or files are not being loaded into two different tables concurrently with auto purge option.";

    /** Every field a caller-supplied metadata object may carry; anything else is rejected by name. */
    private static final List<String> ALLOWED_FIELDS = Arrays.asList(
        CONTENT_TYPE, ETAG, LAST_MODIFIED, RELATIVE_PATH, SIZE, STAGE, SCOPED_FILE_URL, STAGE_FILE_URL);

    /**
     * Fields every metadata object must carry. {@code RELATIVE_PATH} is NOT one of them (live)
     * an object of {@code STAGE} and {@code SIZE} fails "Missing required fields:
     * CONTENT_TYPE, ETAG, LAST_MODIFIED" — path and stage are the separate identity rule below).
     * Live lists the missing names in a different order call to call, so this order is just
     * Frostlake's own; tests match per-field.
     */
    private static final List<String> REQUIRED_FIELDS = Arrays.asList(
        CONTENT_TYPE, SIZE, LAST_MODIFIED, ETAG);

    /** RFC-1123, e.g. {@code Mon, 03 Aug 2026 11:24:13 GMT} — Snowflake's exact LAST_MODIFIED shape. */
    private static final DateTimeFormatter RFC_1123 =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);

    private FileDescriptor() {
    }

    /** Snowflake's "Remote file '<i>x</i>' was not found…" message for the given location. */
    public static String notFoundMessage(final String location) {
        return "Remote file '" + location + NOT_FOUND_SUFFIX;
    }

    /**
     * Resolve a stage reference and read the real file's metadata.
     *
     * @param location the stage reference as the caller wrote it (it is echoed in the error)
     * @param locator  resolves the reference to a file on disk
     * @return the descriptor, or null when the file does not exist (the caller decides whether that is
     *         an error, as {@code TO_FILE} does, or a NULL, as {@code TRY_TO_FILE} does)
     */
    public static VariantValue fromLocation(final String location, final StageFileLocator locator) {
        if (locator == null) {
            throw new RuntimeException("TO_FILE requires a stage-aware engine session");
        }
        final StagedFile staged = locator.locate(location);
        if (staged == null || staged.getPath() == null || !Files.isRegularFile(staged.getPath())) {
            return null;
        }
        final Path path = staged.getPath();
        final long size;
        final Instant modified;
        final byte[] bytes;
        try {
            size = Files.size(path);
            modified = Files.getLastModifiedTime(path).toInstant();
            bytes = Files.readAllBytes(path);
        } catch (final IOException e) {
            throw new RuntimeException("Cannot read staged file '" + location + "': " + e.getMessage(), e);
        }
        final ObjectNode node = ArrayFunctionHelper.MAPPER.createObjectNode();
        // Alphabetical, matching Snowflake's own rendering of the descriptor.
        node.put(CONTENT_TYPE, FileContentTypes.forPath(staged.getRelativePath()));
        node.put(ETAG, SharedFunctionHelpers.toHex(SharedFunctionHelpers.digest("MD5", bytes)));
        node.put(LAST_MODIFIED, formatLastModified(modified));
        node.put(RELATIVE_PATH, staged.getRelativePath());
        node.put(SIZE, size);
        node.put(STAGE, staged.getStage());
        return ArrayFunctionHelper.toCanonicalVariant(node);
    }

    /**
     * Validate a caller-supplied metadata object, as {@code TO_FILE(OBJECT)} does. Unlike the stage-path
     * form this does NOT check that the file exists — live, an object naming
     * {@code x.txt} on a real stage round-trips even though no such file is there.
     *
     * @param value    the object, as an OBJECT/VARIANT runtime value
     * @param tryMode  true for {@code TRY_TO_FILE} (invalid input yields null instead of an error)
     * @return the descriptor, or null when {@code tryMode} and the object is invalid
     */
    public static VariantValue fromMetadataObject(final Object value, final boolean tryMode) {
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node == null || !node.isObject()) {
            return failOrNull(tryMode, "Unsupported cast to FILE.");
        }
        for (final Iterator<String> names = node.propertyNames().iterator(); names.hasNext();) {
            final String field = names.next();
            if (!ALLOWED_FIELDS.contains(field.toUpperCase(Locale.ROOT))) {
                return failOrNull(tryMode, "Invalid file metadata field " + field + ".");
            }
        }
        final List<String> missing = new ArrayList<String>();
        for (final String required : REQUIRED_FIELDS) {
            final JsonNode field = node.get(required);
            if (field == null || field.isNull()) {
                missing.add(required);
            }
        }
        final boolean hasStageIdentity = isSet(node, STAGE) && isSet(node, RELATIVE_PATH);
        final boolean hasUrlIdentity = isSet(node, SCOPED_FILE_URL) || isSet(node, STAGE_FILE_URL);
        if (!missing.isEmpty()) {
            return failOrNull(tryMode, "Invalid file metadata. Missing required fields: "
                + String.join(", ", missing) + ".");
        }
        // The identity rule is separate from the required fields and survives them (re-measured live:
        // an object of just the four required fields fails with exactly this message).
        if (!hasStageIdentity && !hasUrlIdentity) {
            return failOrNull(tryMode, "Invalid file metadata. Must provide (STAGE and RELATIVE_PATH),"
                + " SCOPED_FILE_URL, or STAGE_FILE_URL.");
        }
        // LAST_MODIFIED is NOT validated: live, both '2026-08-03 11:24:13' and 'not a date'
        // round-trip verbatim; FL_GET_LAST_MODIFIED is where lenient parsing happens (NULL on junk).
        final ObjectNode out = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final String field : ALLOWED_FIELDS) {
            final JsonNode existing = node.get(field);
            if (existing == null || existing.isNull()) {
                continue;
            }
            if (RELATIVE_PATH.equals(field) && !hasStageIdentity) {
                // Live: a URL-identified descriptor renders without RELATIVE_PATH even when one was given.
                continue;
            }
            out.set(field, existing);
        }
        return ArrayFunctionHelper.toCanonicalVariant(out);
    }

    /** A descriptor field as text, or null when the value is not a descriptor or the field is unset. */
    public static String textField(final Object value, final String field) {
        final JsonNode node = descriptorNode(value);
        if (node == null) {
            return null;
        }
        final JsonNode found = node.get(field);
        return found == null || found.isNull() ? null : found.asString();
    }

    /** The descriptor as a JSON object node, or null when the value is not one. */
    public static JsonNode descriptorNode(final Object value) {
        if (value == null) {
            return null;
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        return node != null && node.isObject() ? node : null;
    }

    /** Parses a descriptor {@code LAST_MODIFIED} string, or null when it is not RFC-1123. */
    public static Instant parseLastModified(final String text) {
        if (text == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(text, RFC_1123.withZone(ZoneOffset.UTC)).toInstant();
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    /** Renders an instant the way a descriptor's {@code LAST_MODIFIED} reads. */
    public static String formatLastModified(final Instant instant) {
        return RFC_1123.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static boolean isSet(final JsonNode node, final String field) {
        final JsonNode found = node.get(field);
        return found != null && !found.isNull();
    }

    private static VariantValue failOrNull(final boolean tryMode, final String message) {
        if (tryMode) {
            return null;
        }
        throw new RuntimeException(message);
    }
}
