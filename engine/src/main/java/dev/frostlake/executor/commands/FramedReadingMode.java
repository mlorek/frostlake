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

/** Where the account's reading of a refused query body stops skipping (see {@link FramedQueryReading}). */
enum FramedReadingMode {
    /** After the body's first word: at a parenthesis, a comma, a cast or anything that may follow an operand. */
    FIRST,
    /** After an outer-join marker that is none, or a postfix refused: at anything that may follow an operand. */
    MISMATCH,
    /** After the frame's list gave up at a word after one of its items: at a parenthesis too. */
    ITEM,
    /** After an operand that is none, past the word refused: at a parenthesis and at OVER too. */
    OPERAND
}
