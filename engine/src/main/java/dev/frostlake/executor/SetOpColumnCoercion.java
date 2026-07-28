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

/**
 * How a set-operation column's values are normalized before row-key comparison, mirroring Snowflake's
 * implicit branch-type unification (a VARCHAR branch compared against a TIMESTAMP branch is coerced to
 * TIMESTAMP, etc.). {@code NONE} means the column's values already compare correctly as-is — notably,
 * a column that is VARCHAR on <em>both</em> sides is never coerced ({@code '01'} stays distinct from
 * {@code '1'}), exactly like the expression layer's comparison coercion.
 */
enum SetOpColumnCoercion {
    NONE,
    NUMERIC,
    TIMESTAMP,
    DATE,
    TIME,
    BOOLEAN
}
