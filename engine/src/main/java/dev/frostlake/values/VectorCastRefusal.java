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
 * A cast to a VECTOR refused for the value it was given: an element of the wrong kind, too wide for the element
 * type, or the wrong count of them. The account answers it as it is, even where it stops a write: an
 * {@code INSERT … SELECT} of such a cast is refused in the cast's own sentence, outside the DML envelope that other
 * row-time failures are wrapped in (live-verified).
 */
public final class VectorCastRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal, in the account's words
     */
    public VectorCastRefusal(final String message) {
        super(message);
    }
}
