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

package dev.frostlake.types;

/**
 * The element type of a {@link VectorType} — the two spellings Snowflake allows in
 * {@code VECTOR(FLOAT, n)} / {@code VECTOR(INT, n)}.
 *
 * <p>Both are NARROWER than the engine's ordinary numeric values, live-verified:
 * {@code FLOAT} elements are 32-bit ({@code VECTOR_NORMALIZE([1,2,3]::VECTOR(FLOAT,3))} is
 * {@code [0.26726124,0.5345225,0.80178374]}, not the float64 {@code 0.2672612419124244}), and
 * {@code INT} elements are 32-bit with silent wraparound ({@code [2147483648,0,0]::VECTOR(INT,3)}
 * is {@code [-2147483648,0,0]} and {@code [9007199254740993,0,0]} is {@code [1,0,0]}).
 */
public enum VectorElementType {
    FLOAT, INT
}
