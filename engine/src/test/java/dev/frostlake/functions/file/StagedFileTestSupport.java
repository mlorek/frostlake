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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adds a real {@code file://} stage named {@code ST} over a temp directory, for the tests that must
 * exercise {@code TO_FILE}'s stage resolution against files that genuinely exist. Frostlake reads the
 * descriptor's SIZE, LAST_MODIFIED, ETAG and CONTENT_TYPE from these files, so the fixtures are the
 * point: a test that stages 24 bytes must see {@code SIZE} 24.
 *
 * <p>{@code file://} stages are a Frostlake extension for local testing, so every suite built on this
 * class skips under {@code SF_LIVE=1} — see {@code stageOnlyReason()}.
 */
public abstract class StagedFileTestSupport extends FileFunctionTestSupport {

    /** A valid 1x1 PNG, for proving that content type follows the NAME and not these bytes. */
    protected static final byte[] PNG_BYTES = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
        0x00, 0x00, 0x00, 0x0C, 'I', 'D', 'A', 'T',
        0x78, (byte) 0x9C, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00, 0x00,
        0x03, 0x01, 0x01, 0x00, 0x18, (byte) 0xDD, (byte) 0x8A, (byte) 0xF1,
        0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D', (byte) 0xAE, 'B', 0x60, (byte) 0x82
    };

    private Path stageDir;

    @Override
    protected void setupTest() {
        if (isLiveSnowflake()) {
            return;
        }
        try {
            stageDir = Files.createTempDirectory("frostlake_file_fn_");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        engine.execute("CREATE STAGE st URL='file://" + stageDir + "'");
    }

    @Override
    protected void teardownTest() {
        if (stageDir != null) {
            deleteRecursively(stageDir.toFile());
            stageDir = null;
        }
    }

    /** Write a text file into the stage, creating any sub-directories it names. */
    protected void stage(final String relativePath, final String content) {
        stageBytes(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Write a binary file into the stage, creating any sub-directories it names. */
    protected void stageBytes(final String relativePath, final byte[] content) {
        final Path target = stageDir.resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The temp directory backing the stage. */
    protected Path stageDirectory() {
        return stageDir;
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
