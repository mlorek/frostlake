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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The two lookup tables behind the FILE family, both measured against a live Snowflake account on
 * rather than inferred from the function names.
 *
 * <p><b>1. Extension &rarr; {@code CONTENT_TYPE}.</b> {@code TO_FILE} derives a staged file's
 * {@code CONTENT_TYPE} from its FILE NAME EXTENSION ALONE — never from its bytes. The decisive live
 * pair: a file holding real PNG bytes named {@code png_named.txt} reports {@code text/plain}, while a
 * file holding plain text named {@code text_named.png} reports {@code image/png}. Anything whose
 * extension is not in the table below — including a name with no extension at all — is
 * {@code application/octet-stream} (measured for {@code noext}, {@code .bz2}, {@code .avro},
 * {@code .orc}, {@code .parquet}, {@code .woff2} and {@code .yaml}).
 *
 * <p><b>2. {@code CONTENT_TYPE} &rarr; category.</b> {@code FL_GET_FILE_TYPE} and the five
 * {@code FL_IS_*} predicates classify on the descriptor's {@code CONTENT_TYPE} — not on the path, not
 * on the bytes. Proven live by feeding {@code TRY_TO_FILE} a hand-built descriptor whose
 * {@code RELATIVE_PATH} and {@code CONTENT_TYPE} disagree: {@code ('x.txt', 'image/png')} classifies
 * {@code image} and {@code ('x.png', 'text/plain')} classifies {@code document}.
 *
 * <p>The category tables are CLOSED EXACT-MATCH SETS — emphatically not a {@code image/*} style prefix
 * rule, and not case- or whitespace-insensitive. Every one of these was measured:
 * {@code image/x-bogus}, {@code video/x-bogus}, {@code audio/x-bogus}, {@code text/x-bogus} and
 * {@code application/x-bogus} are all {@code unknown}; so are {@code IMAGE/PNG} (wrong case),
 * {@code " image/png"} / {@code "image/png "} (untrimmed) and
 * {@code application/gzip;charset=utf-8} (parameters not stripped). Within a family, membership is
 * arbitrary and must not be guessed: {@code audio/mpeg} is audio but {@code audio/mp4} is
 * {@code unknown}; {@code text/plain} is a document but {@code text/tab-separated-values} is
 * {@code unknown}; {@code application/gzip} is compressed but {@code application/x-gzip} is
 * {@code unknown}; {@code application/zip} is compressed but {@code application/x-7z-compressed} is
 * {@code unknown}.
 *
 * <p>The sets also OVERLAP. {@code video/x-msvideo} (an {@code .avi} file) is in BOTH the video and the
 * audio set — live, {@code FL_IS_VIDEO} and {@code FL_IS_AUDIO} are both TRUE for it while
 * {@code FL_GET_FILE_TYPE} answers {@code video}. So {@code FL_IS_x(f)} is NOT
 * {@code FL_GET_FILE_TYPE(f) = 'x'}: each predicate is its own membership test, and
 * {@code FL_GET_FILE_TYPE} resolves the tie by checking image, then video, then audio, then document,
 * then compressed.
 *
 * <p>A content type absent from every set — and a NULL content type — is {@code unknown}, with all five
 * predicates FALSE.
 */
public final class FileContentTypes {

    /** {@code FL_GET_FILE_TYPE} for anything in no category, and for a NULL file or content type. */
    public static final String UNKNOWN = "unknown";

    private static final String OCTET_STREAM = "application/octet-stream";

    private static final Map<String, String> BY_EXTENSION = new HashMap<String, String>();

    private static final Set<String> IMAGE = new HashSet<String>();
    private static final Set<String> VIDEO = new HashSet<String>();
    private static final Set<String> AUDIO = new HashSet<String>();
    private static final Set<String> DOCUMENT = new HashSet<String>();
    private static final Set<String> COMPRESSED = new HashSet<String>();

    static {
        // Extension -> CONTENT_TYPE, every row read back from a real staged file.
        BY_EXTENSION.put("txt", "text/plain");
        BY_EXTENSION.put("log", "text/plain");
        BY_EXTENSION.put("md", "text/markdown");
        BY_EXTENSION.put("html", "text/html");
        BY_EXTENSION.put("csv", "text/csv");
        BY_EXTENSION.put("tsv", "text/tab-separated-values");
        BY_EXTENSION.put("py", "text/x-python");
        BY_EXTENSION.put("json", "application/json");
        BY_EXTENSION.put("xml", "application/xml");
        BY_EXTENSION.put("pdf", "application/pdf");
        BY_EXTENSION.put("doc", "application/msword");
        BY_EXTENSION.put("docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        BY_EXTENSION.put("xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        BY_EXTENSION.put("pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        BY_EXTENSION.put("png", "image/png");
        BY_EXTENSION.put("jpg", "image/jpeg");
        BY_EXTENSION.put("jpeg", "image/jpeg");
        BY_EXTENSION.put("gif", "image/gif");
        BY_EXTENSION.put("svg", "image/svg+xml");
        BY_EXTENSION.put("webp", "image/webp");
        BY_EXTENSION.put("bmp", "image/bmp");
        BY_EXTENSION.put("ico", "image/x-icon");
        BY_EXTENSION.put("tiff", "image/tiff");
        BY_EXTENSION.put("mp3", "audio/mpeg");
        BY_EXTENSION.put("wav", "audio/x-wav");
        BY_EXTENSION.put("mp4", "video/mp4");
        BY_EXTENSION.put("mov", "video/quicktime");
        BY_EXTENSION.put("avi", "video/x-msvideo");
        BY_EXTENSION.put("zip", "application/zip");
        BY_EXTENSION.put("gz", "application/gzip");
        BY_EXTENSION.put("tar", "application/x-tar");
        BY_EXTENSION.put("rar", "application/vnd.rar");
        BY_EXTENSION.put("7z", "application/x-7z-compressed");
        BY_EXTENSION.put("exe", "application/vnd.microsoft.portable-executable");

        IMAGE.add("image/png");
        IMAGE.add("image/jpeg");
        IMAGE.add("image/jpg");
        IMAGE.add("image/gif");
        IMAGE.add("image/webp");
        IMAGE.add("image/svg+xml");
        IMAGE.add("image/tiff");
        IMAGE.add("image/x-tiff");
        IMAGE.add("image/bmp");
        IMAGE.add("image/x-icon");
        IMAGE.add("image/vnd.microsoft.icon");
        IMAGE.add("image/heic");
        IMAGE.add("image/avif");
        IMAGE.add("image/apng");

        VIDEO.add("video/mp4");
        VIDEO.add("video/mpeg");
        VIDEO.add("video/quicktime");
        VIDEO.add("video/x-msvideo");
        VIDEO.add("video/webm");
        VIDEO.add("video/x-matroska");
        VIDEO.add("video/x-ms-wmv");
        VIDEO.add("video/x-ms-asf");
        VIDEO.add("video/ogg");
        VIDEO.add("video/3gpp");
        VIDEO.add("video/x-flv");
        VIDEO.add("video/mp2t");

        AUDIO.add("audio/mpeg");
        AUDIO.add("audio/wav");
        AUDIO.add("audio/x-wav");
        AUDIO.add("audio/ogg");
        AUDIO.add("audio/flac");
        AUDIO.add("audio/aac");
        AUDIO.add("audio/x-m4a");
        AUDIO.add("audio/midi");
        AUDIO.add("audio/webm");
        AUDIO.add("audio/x-aiff");
        AUDIO.add("audio/opus");
        AUDIO.add("audio/x-ms-wma");
        // Live: an.avi is BOTH — FL_IS_AUDIO('video/x-msvideo') is TRUE while
        // FL_GET_FILE_TYPE answers "video". Reproduced twice, from a hand-built descriptor and
        // from a real staged s.avi.
        AUDIO.add("video/x-msvideo");

        DOCUMENT.add("text/plain");
        DOCUMENT.add("text/html");
        DOCUMENT.add("text/csv");
        DOCUMENT.add("text/markdown");
        DOCUMENT.add("text/xml");
        DOCUMENT.add("text/calendar");
        DOCUMENT.add("application/json");
        DOCUMENT.add("application/pdf");
        DOCUMENT.add("application/xml");
        DOCUMENT.add("application/msword");
        DOCUMENT.add("application/rtf");
        DOCUMENT.add("application/epub+zip");
        DOCUMENT.add("application/vnd.ms-excel");
        DOCUMENT.add("application/vnd.ms-powerpoint");
        DOCUMENT.add("application/vnd.oasis.opendocument.text");
        DOCUMENT.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        DOCUMENT.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        DOCUMENT.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");

        COMPRESSED.add("application/zip");
        COMPRESSED.add("application/x-zip-compressed");
        COMPRESSED.add("application/gzip");
        COMPRESSED.add("application/x-tar");
        COMPRESSED.add("application/x-bzip");
        COMPRESSED.add("application/x-bzip2");
        COMPRESSED.add("application/vnd.rar");
    }

    private FileContentTypes() {
    }

    /**
     * The {@code CONTENT_TYPE} Snowflake reports for a staged file with this relative path — from the
     * extension only, {@code application/octet-stream} when the extension is unknown or absent.
     *
     * @param relativePath the file's path within its stage
     * @return the content type, never null
     */
    public static String forPath(final String relativePath) {
        if (relativePath == null) {
            return OCTET_STREAM;
        }
        final int slash = Math.max(relativePath.lastIndexOf('/'), relativePath.lastIndexOf('\\'));
        final String name = relativePath.substring(slash + 1);
        final int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return OCTET_STREAM;
        }
        // The extension is matched case-insensitively — the table itself is lower-case, and a staged
        // "IMAGE.PNG" is still image/png. (The CONTENT_TYPE -> category step below is NOT: it compares
        // the content type verbatim, so "IMAGE/PNG" as a descriptor field is `unknown`.)
        final String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        final String contentType = BY_EXTENSION.get(extension);
        return contentType == null ? OCTET_STREAM : contentType;
    }

    /**
     * {@code FL_GET_FILE_TYPE}: the single category name for a content type — {@code image},
     * {@code video}, {@code audio}, {@code document}, {@code compressed} or {@code unknown}. Checked in
     * that order, which is what makes an {@code .avi} (in both the video and audio sets) a
     * {@code video}.
     *
     * @param contentType the descriptor's {@code CONTENT_TYPE}, may be null
     * @return the category name, never null
     */
    public static String fileType(final String contentType) {
        if (contentType == null) {
            return UNKNOWN;
        }
        if (IMAGE.contains(contentType)) {
            return "image";
        }
        if (VIDEO.contains(contentType)) {
            return "video";
        }
        if (AUDIO.contains(contentType)) {
            return "audio";
        }
        if (DOCUMENT.contains(contentType)) {
            return "document";
        }
        if (COMPRESSED.contains(contentType)) {
            return "compressed";
        }
        return UNKNOWN;
    }

    /** {@code FL_IS_IMAGE}'s membership test — exact match against the measured image set. */
    public static boolean isImage(final String contentType) {
        return contentType != null && IMAGE.contains(contentType);
    }

    /** {@code FL_IS_VIDEO}'s membership test — exact match against the measured video set. */
    public static boolean isVideo(final String contentType) {
        return contentType != null && VIDEO.contains(contentType);
    }

    /** {@code FL_IS_AUDIO}'s membership test — includes {@code video/x-msvideo}, see the class note. */
    public static boolean isAudio(final String contentType) {
        return contentType != null && AUDIO.contains(contentType);
    }

    /** {@code FL_IS_DOCUMENT}'s membership test — exact match against the measured document set. */
    public static boolean isDocument(final String contentType) {
        return contentType != null && DOCUMENT.contains(contentType);
    }

    /** {@code FL_IS_COMPRESSED}'s membership test — note {@code application/x-gzip} is NOT in it. */
    public static boolean isCompressed(final String contentType) {
        return contentType != null && COMPRESSED.contains(contentType);
    }

    /** The measured extension table, for the test that pins it. */
    public static Map<String, String> extensionTable() {
        return Collections.unmodifiableMap(BY_EXTENSION);
    }
}
