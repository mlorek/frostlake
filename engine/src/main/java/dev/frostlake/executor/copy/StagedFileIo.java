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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
     * What a byte sequence the file's encoding cannot read is read as, for the load to find it and settle it by
     * REPLACE_INVALID_CHARACTERS: a noncharacter, which text does not hold.
     */
    public static final char MALFORMED = '\uFFFF';

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
     * A staged file's text in a character set, every byte sequence the set cannot read read as {@link #MALFORMED}.
     *
     * @param file    the staged file
     * @param charset its character set
     * @return the reader
     * @throws IOException when the file cannot be opened
     */
    public static BufferedReader reader(final Path file, final Charset charset) throws IOException {
        final CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            decoder.replaceWith(String.valueOf(MALFORMED));
        } catch (final IllegalArgumentException wide) {
            // A set that reads fewer than one character a byte keeps its own replacement, U+FFFD.
        }
        return new BufferedReader(new InputStreamReader(inputStream(file), decoder));
    }

    public static String readString(final Path file) throws IOException {
        try (final InputStream in = inputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
