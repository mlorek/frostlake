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

import java.nio.file.Path;

/**
 * A stage reference resolved to a concrete file: where it lives on disk, plus the two descriptor
 * fields that identify it inside its stage.
 *
 * <p>{@link #getStage()} is the canonical display Snowflake puts in a FILE descriptor's {@code STAGE}
 * field, measured live: a named stage renders fully qualified and upper-cased
 * ({@code @PROBE_DB.S.SSE}) however it was written at the call site, the user stage renders
 * {@code @"~"}, and a table stage renders just the bare table name ({@code @TSTG}) with no database or
 * schema. {@link #getRelativePath()} is the path within the stage with no leading slash, and it keeps
 * its sub-directories ({@code sub/nested.txt}, {@code p135/hello.txt}).
 */
public final class StagedFile {

    private final Path path;
    private final String stage;
    private final String relativePath;

    public StagedFile(final Path path, final String stage, final String relativePath) {
        this.path = path;
        this.stage = stage;
        this.relativePath = relativePath;
    }

    /** Where the staged file actually lives on this machine. */
    public Path getPath() {
        return path;
    }

    /** The descriptor's {@code STAGE} field, e.g. {@code @DB.SCHEMA.NAME}. */
    public String getStage() {
        return stage;
    }

    /** The descriptor's {@code RELATIVE_PATH} field, e.g. {@code sub/nested.txt}. */
    public String getRelativePath() {
        return relativePath;
    }
}
