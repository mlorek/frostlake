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

package dev.frostlake.parser;

/** What becomes of a syntax-error line that follows the last line a report kept — see {@link LaterStatementLines}. */
enum LaterLineVerdict {

    /** No later-statement rule speaks: the line is judged as any other. */
    AS_BEFORE,

    /** The line's statement is read on its own: the line is reported, even where the input ended. */
    READ,

    /** The line falls in the statement the recovery passes over: it is not reported. */
    SKIPPED,

    /** The report ends before the line. */
    END,

    /** The report names {@link LaterStatementLines#replacement()} in the line's place, and ends there. */
    REPLACED
}
