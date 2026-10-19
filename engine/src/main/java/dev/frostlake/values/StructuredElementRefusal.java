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

package dev.frostlake.values;

/**
 * An element ARRAY_APPEND, ARRAY_PREPEND or ARRAY_INSERT could not cast to a structured ARRAY's element type, such as
 * a text that reads as no number for an {@code ARRAY(NUMBER)}. The account answers it in the cast's own sentence,
 * even where it stops a write: a CTAS, an {@code INSERT … SELECT} or an UPDATE of such a call is refused outside
 * the DML envelope that a plain cast's failure there is wrapped in (live-verified).
 */
public final class StructuredElementRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal, in the account's words
     */
    public StructuredElementRefusal(final String message) {
        super(message);
    }
}
