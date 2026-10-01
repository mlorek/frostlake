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

import java.nio.file.Path;

/**
 * One staged file INFER_SCHEMA reads, and the names it goes by: the one its FILENAMES column lists, the one a
 * refusal's {@code File '…'} line gives, and the storage path the first line of a JSON parse refusal gives.
 */
public final class InferenceFile {

    private final Path path;
    private final String listedName;
    private final String relativeName;
    private final String storedName;

    /**
     * A file to read.
     *
     * @param path where the file is
     * @param listedName how FILENAMES lists it — the stage-relative path for a named stage, prefixed with the
     *                   table for a table's stage and with {@code @~/} for the user's
     * @param relativeName its stage-relative path
     * @param storedName its path under the stage's storage folder
     */
    public InferenceFile(final Path path, final String listedName, final String relativeName,
                         final String storedName) {
        this.path = path;
        this.listedName = listedName;
        this.relativeName = relativeName;
        this.storedName = storedName;
    }

    /** Where the file is. */
    public Path path() {
        return path;
    }

    /** How FILENAMES lists the file. */
    public String listedName() {
        return listedName;
    }

    /** The file's stage-relative path, as a refusal names it. */
    public String relativeName() {
        return relativeName;
    }

    /** The file's path under the stage's storage folder. */
    public String storedName() {
        return storedName;
    }
}
