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

package dev.frostlake.executor.copy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Byte access to staged files with transparent gzip. The NAME is not the whole story: a name ending
 * {@code .gz} reads through a {@link GZIPInputStream}, and so does any file whose first two bytes are
 * gzip's own magic number — which is what an unload written with {@code SINGLE = TRUE} leaves behind,
 * since the account names that file {@code data} however it was compressed. PUT's default
 * AUTO_COMPRESS lands {@code .gz} files on the stage, and the loaders read them exactly as a real
 * account does.
 */
public final class StagedFileIo {

    /**
     * The mark of the first byte of a sequence the file's encoding cannot read, the byte's value added to it, for a
     * reader to find it and settle it by REPLACE_INVALID_CHARACTERS (see {@link UnreadableMarkingReader}).
     */
    public static final char UNREADABLE_START = '\uF600';

    /** The mark of a later byte of the same unreadable sequence, the byte's value added to it. */
    public static final char UNREADABLE_MORE = '\uF700';

    /** Past the last mark. */
    private static final char UNREADABLE_END = '\uF800';

    /** The character sets a load's ENCODING names, by the account's names, as the JDK names them (documented). */
    private static final Map<String, String> ENCODINGS = new HashMap<String, String>();

    static {
        final String[][] pairs = {
            {"BIG5", "Big5"}, {"EUCJP", "EUC-JP"}, {"EUCKR", "EUC-KR"}, {"GB18030", "GB18030"},
            {"IBM420", "IBM420"}, {"IBM424", "IBM424"}, {"IBM949", "x-IBM949"},
            {"ISO2022CN", "ISO-2022-CN"}, {"ISO2022JP", "ISO-2022-JP"}, {"ISO2022KR", "ISO-2022-KR"},
            {"ISO88591", "ISO-8859-1"}, {"ISO88592", "ISO-8859-2"}, {"ISO88595", "ISO-8859-5"},
            {"ISO88596", "ISO-8859-6"}, {"ISO88597", "ISO-8859-7"}, {"ISO88598", "ISO-8859-8"},
            {"ISO88599", "ISO-8859-9"}, {"ISO885915", "ISO-8859-15"}, {"KOI8R", "KOI8-R"},
            {"SHIFTJIS", "Shift_JIS"}, {"UTF8", "UTF-8"}, {"UTF16", "UTF-16"}, {"UTF16BE", "UTF-16BE"},
            {"UTF16LE", "UTF-16LE"}, {"UTF32", "UTF-32"}, {"UTF32BE", "UTF-32BE"}, {"UTF32LE", "UTF-32LE"},
            {"WINDOWS874", "x-windows-874"}, {"WINDOWS949", "x-windows-949"}, {"WINDOWS1250", "windows-1250"},
            {"WINDOWS1251", "windows-1251"}, {"WINDOWS1252", "windows-1252"}, {"WINDOWS1253", "windows-1253"},
            {"WINDOWS1254", "windows-1254"}, {"WINDOWS1255", "windows-1255"}, {"WINDOWS1256", "windows-1256"},
        };
        for (final String[] pair : pairs) {
            ENCODINGS.put(pair[0], pair[1]);
        }
    }

    private StagedFileIo() {
    }

    /**
     * The character set an ENCODING names, by the account's name for it (ISO88591) or the set's own (ISO-8859-1),
     * in any case.
     *
     * @param encoding the name as written
     * @return the set, or null for a name the account does not list
     */
    public static Charset charsetFor(final String encoding) {
        final String name = ENCODINGS.get(encodingKey(encoding));
        if (name == null) {
            return null;
        }
        try {
            return Charset.forName(name);
        } catch (final UnsupportedCharsetException absent) {
            return null;
        }
    }

    public static InputStream inputStream(final Path file) throws IOException {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")
                || isGzipped(file)) {
            return new GZIPInputStream(Files.newInputStream(file));
        }
        return Files.newInputStream(file);
    }

    /**
     * Whether a file begins with gzip's magic number, whatever it is called.
     *
     * @param file the staged file
     * @return true when its first two bytes are 0x1f 0x8b
     * @throws IOException when the file cannot be read
     */
    private static boolean isGzipped(final Path file) throws IOException {
        try (final InputStream head = Files.newInputStream(file)) {
            final byte[] magic = head.readNBytes(2);
            return magic.length == 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b;
        }
    }

    /**
     * Whether an ENCODING names one of the account's character sets, by the account's name or the set's own.
     *
     * @param encoding the name as written
     * @return whether the account lists it
     */
    public static boolean isEncodingName(final String encoding) {
        return encoding != null && ENCODINGS.containsKey(encodingKey(encoding));
    }

    /** A character set's name as the table keys it: upper case, without hyphens and underscores. */
    private static String encodingKey(final String encoding) {
        return encoding.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    public static BufferedReader reader(final Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(inputStream(file), StandardCharsets.UTF_8));
    }

    /**
     * A staged file's text in a character set, every byte the set cannot read kept as a mark of it (see
     * {@link #UNREADABLE_START}).
     *
     * @param file    the staged file
     * @param charset its character set
     * @return the reader
     * @throws IOException when the file cannot be opened
     */
    public static BufferedReader reader(final Path file, final Charset charset) throws IOException {
        return new BufferedReader(new UnreadableMarkingReader(inputStream(file), charset));
    }

    /**
     * A staged file's whole text in a character set, every byte the set cannot read kept as a mark of it.
     *
     * @param file    the staged file
     * @param charset its character set
     * @return the text
     * @throws IOException when the file cannot be read
     */
    public static String readMarked(final Path file, final Charset charset) throws IOException {
        final StringBuilder text = new StringBuilder();
        try (BufferedReader reader = reader(file, charset)) {
            final char[] buffer = new char[8192];
            int read = reader.read(buffer);
            while (read >= 0) {
                text.append(buffer, 0, read);
                read = reader.read(buffer);
            }
        }
        return text.toString();
    }

    /**
     * Bytes read as UTF-8, every byte that is no part of it kept as a mark of it, as {@link #readMarked} keeps them.
     *
     * @param bytes the bytes
     * @return the text
     */
    public static String markedUtf8(final byte[] bytes) {
        final StringBuilder text = new StringBuilder(bytes.length);
        try (Reader reader = new UnreadableMarkingReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            final char[] buffer = new char[1024];
            int read = reader.read(buffer);
            while (read >= 0) {
                text.append(buffer, 0, read);
                read = reader.read(buffer);
            }
        } catch (final IOException unread) {
            throw new UncheckedIOException(unread);
        }
        return text.toString();
    }

    /** Whether a character is the mark of a byte its file's encoding could not read. */
    public static boolean isUnreadable(final char c) {
        return c >= UNREADABLE_START && c < UNREADABLE_END;
    }

    /**
     * Where a text holds the first mark of an unreadable byte.
     *
     * @param text the text
     * @return its index, or -1 when it holds none
     */
    public static int firstUnreadable(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isUnreadable(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The byte a mark keeps, as the account's refusals spell it: {@code 0xE9}.
     *
     * @param mark the mark
     * @return its byte in upper-case hex
     */
    public static String unreadableByte(final char mark) {
        return String.format(Locale.ROOT, "0x%02X", Integer.valueOf(mark & 0xFF));
    }

    /**
     * A text as a refusal quotes it, each unreadable byte spelled as its value: {@code caf0xE9}.
     *
     * @param text the text
     * @return the text quoted
     */
    public static String unreadableShown(final String text) {
        final StringBuilder shown = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (isUnreadable(c)) {
                shown.append(unreadableByte(c));
            } else {
                shown.append(c);
            }
        }
        return shown.toString();
    }

    /**
     * A record format's refusal of a file holding a byte that is no part of UTF-8, in the account's words: {@code Error
     * parsing JSON: invalid UTF-8 sequence: 0xE9}, then the file, the line and the byte's character within it, and the
     * row and column being read (live-verified, for JSON and XML).
     *
     * @param format     the format, as the refusal names it: JSON, XML
     * @param marked     the file's text, each unreadable byte kept as its mark
     * @param unreadable where the text holds the first mark
     * @param shownName  the file, as the refusal names it
     * @return the refusal
     */
    public static RuntimeException unreadableRefusal(final String format, final String marked, final int unreadable,
                                                     final String shownName) {
        final int lineStart = marked.lastIndexOf('\n', unreadable - 1) + 1;
        int line = 1;
        for (int i = 0; i < lineStart; i++) {
            line += marked.charAt(i) == '\n' ? 1 : 0;
        }
        return new RuntimeException("Error parsing " + format + ": invalid UTF-8 sequence: "
            + unreadableByte(marked.charAt(unreadable)) + "\n  File '" + shownName + "', line " + line
            + ", character " + (unreadable - lineStart + 1) + "\n  Row 0, column $1");
    }

    /**
     * A text with each unreadable byte sequence replaced by U+FFFD, as REPLACE_INVALID_CHARACTERS = TRUE reads it.
     *
     * @param text the text
     * @return the text replaced
     */
    public static String unreadableReplaced(final String text) {
        if (firstUnreadable(text) < 0) {
            return text;
        }
        final StringBuilder replaced = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= UNREADABLE_START && c < UNREADABLE_MORE) {
                replaced.append('\uFFFD');
            } else if (!isUnreadable(c)) {
                replaced.append(c);
            }
        }
        return replaced.toString();
    }

    public static String readString(final Path file) throws IOException {
        try (final InputStream in = inputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
