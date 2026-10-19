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

/** A scripting construct whose statements an END closes: a block, or the body of a control statement. */
enum BlockConstruct {
    /** BEGIN … END, with or without a DECLARE section and exception handlers. */
    BLOCK,
    /** IF … END IF. */
    IF,
    /** CASE … END CASE. */
    CASE,
    /** FOR … END FOR, or END LOOP when its body opens with LOOP. */
    FOR,
    /** WHILE … END WHILE, or END LOOP when its body opens with LOOP. */
    WHILE,
    /** LOOP … END LOOP. */
    LOOP,
    /** REPEAT … UNTIL … END REPEAT. */
    REPEAT
}
