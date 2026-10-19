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

package dev.frostlake.executor.rowpattern;

/** Where a MATCH_RECOGNIZE clause looks for the next match once it has found one. */
public enum AfterMatchSkip {
    /** AFTER MATCH SKIP PAST LAST ROW, the default: after the match's last row. */
    PAST_LAST_ROW,
    /** AFTER MATCH SKIP TO NEXT ROW: at the row after the match's first. */
    TO_NEXT_ROW,
    /** AFTER MATCH SKIP TO FIRST symbol: at the first row read as the symbol. */
    TO_FIRST,
    /** AFTER MATCH SKIP TO [LAST] symbol: at the last row read as the symbol. */
    TO_LAST
}
