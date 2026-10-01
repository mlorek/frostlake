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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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

    private StagedFileIo() {
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

    public static BufferedReader reader(final Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(inputStream(file), StandardCharsets.UTF_8));
    }

    public static String readString(final Path file) throws IOException {
        try (final InputStream in = inputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
