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

/**
 * Callback {@code TO_FILE} uses to turn a stage reference such as {@code @stage/dir/file.txt} into the
 * file it names — the seam that lets the FILE functions read real stage metadata without depending on
 * the executor class itself. Mirrors {@code dev.frostlake.functions.table.QueryRunner}, and exists
 * instead of a {@code Function<String, StagedFile>} per the project's no-functional-interfaces rule.
 */
public interface StageFileLocator {

    /**
     * Resolve a stage reference to the file it names.
     *
     * @param location a stage reference: {@code @stage/path}, {@code @db.schema.stage/path},
     *                 {@code @~/path} or {@code @%table/path}
     * @return the resolved file, or null when the reference names no stage this engine can reach
     *         (the caller turns that into Snowflake's "was not found" error)
     */
    StagedFile locate(final String location);
}
