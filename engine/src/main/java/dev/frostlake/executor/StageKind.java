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

package dev.frostlake.executor;

/** The three kinds of stage a {@code @…} reference can name. */
public enum StageKind {
    /** A stage created by name: {@code @st}, {@code @db.schema.st}. */
    NAMED,
    /** A table's own stage: {@code @%t}. */
    TABLE,
    /** The current user's stage: {@code @~}. */
    USER
}
