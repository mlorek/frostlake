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

package dev.frostlake.executor.commands;

/** What a refusal in the account's reading of a refused query body broke off (see {@link FramedQueryReading}). */
enum FramedRefusalKind {
    /** An operand that is none. */
    OPERAND,
    /** A postfix, a window or an IN list, with its operand read. */
    POSTFIX,
    /** A NOT that opens nothing: the expression ends there. */
    END,
    /** A query nested in the body went on past its parentheses, as the frame reads: the reading is over. */
    OVER
}
