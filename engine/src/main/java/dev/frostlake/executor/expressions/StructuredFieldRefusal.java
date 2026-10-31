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

package dev.frostlake.executor.expressions;

/**
 * A refusal of a structured OBJECT's field, as the account words it with no "SQL compilation error" in front of it:
 * a name the object does not declare ("Function GET: expected structured object to contain field nosuch but it did
 * not."), an OBJECT_INSERT of a field that is there or is not, a NULL set into a field. Each is the compiled
 * statement's refusal, so a CREATE VIEW over it is refused as the query is (live-verified).
 */
public final class StructuredFieldRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal as the account words it
     */
    public StructuredFieldRefusal(final String message) {
        super(message);
    }
}
