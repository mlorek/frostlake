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

package dev.frostlake.metastore;

import dev.frostlake.storage.ResultSet;

/**
 * The errors the files a past COPY INTO a table loaded give when they are validated again, in the shape
 * {@code VALIDATION_MODE = RETURN_ALL_ERRORS} answers: VALIDATE "validates the files loaded in a past execution of the
 * COPY INTO command and returns all the errors encountered during the load" (documented).
 */
public interface CopyJobErrors {

    /**
     * Validates the load's files again.
     *
     * @return every error they give, a file no longer staged as a missing one
     */
    ResultSet errors();
}
