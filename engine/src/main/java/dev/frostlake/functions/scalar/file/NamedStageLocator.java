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
 * Callback the stage functions use to find the named stage their first argument names, the seam that keeps them off
 * the executor class itself (see {@link StageFileLocator}).
 */
public interface NamedStageLocator {

    /**
     * The named stage the canonical name parts reach, matched exactly: one part is a stage in the current schema, two
     * a schema and a stage in the current database, three a database, a schema and a stage.
     *
     * @param parts the name's canonical parts
     * @return the stage, or null when no named stage has that name
     */
    NamedStage namedStage(final String[] parts);
}
