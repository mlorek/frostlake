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

/** Which rows a MATCH_RECOGNIZE clause answers for each match. */
public enum RowsPerMatch {
    /** ONE ROW PER MATCH, the default: one summary row, an empty match's too. */
    ONE_ROW,
    /** ALL ROWS PER MATCH [SHOW EMPTY MATCHES]: every row of the match, and one row for an empty match. */
    ALL_ROWS,
    /** ALL ROWS PER MATCH OMIT EMPTY MATCHES: every row of the match, an empty match answering none. */
    ALL_ROWS_OMIT_EMPTY,
    /** ALL ROWS PER MATCH WITH UNMATCHED ROWS: as ALL_ROWS, and every row no match holds. */
    ALL_ROWS_WITH_UNMATCHED;

    /** Whether one summary row answers a match. */
    public boolean isOneRow() {
        return this == ONE_ROW;
    }
}
