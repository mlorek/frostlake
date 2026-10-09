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

/** What a node of a MATCH_RECOGNIZE row pattern is. */
public enum RowPatternKind {
    /** A symbol: one row the symbol's definition holds for. */
    SYMBOL,
    /** {@code ^}: the start of the partition; no row. */
    PARTITION_START,
    /** {@code $}: the end of the partition; no row. */
    PARTITION_END,
    /** Its parts one after another. */
    SEQUENCE,
    /** One of its parts, the first that leads to a match. */
    ALTERNATION,
    /** Its one part repeated between a least and a greatest number of times. */
    QUANTIFIED,
    /** {@code {- … -}}: its one part, matched but left out of the rows the match answers. */
    EXCLUSION
}
