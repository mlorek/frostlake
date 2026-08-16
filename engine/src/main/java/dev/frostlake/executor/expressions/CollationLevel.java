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
 * How strongly an expression holds its collation when two meet in one comparison — the three levels
 * live ranks them by, lowest first. The higher level wins outright; two at the same level must agree.
 */
public enum CollationLevel {
    /** No collation: a literal, a computed value, a column declared without one, or COLLATE ''. */
    NONE,
    /** The collation a column was declared with. */
    COLUMN,
    /** A collation named by COLLATE in the statement itself, in either spelling. */
    EXPLICIT
}
