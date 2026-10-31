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

package dev.frostlake.metastore.model;

/**
 * The refusal of a routine whose signature a routine of the other lifetime holds where it is created: a temporary
 * routine over a permanent one, or a permanent routine over its own session's temporary one. It reads as a name taken
 * does — {@code Object 'F(X NUMBER):NUMBER(38,0)' already exists.} — but neither OR REPLACE nor IF NOT EXISTS
 * answers it (live-verified), so a statement tells it apart by its kind.
 */
public class SignatureLifetimeConflict extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal's sentence
     */
    public SignatureLifetimeConflict(final String message) {
        super(message);
    }
}
