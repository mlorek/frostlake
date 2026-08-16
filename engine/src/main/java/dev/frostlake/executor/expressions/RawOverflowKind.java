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
 * Which step of an exact arithmetic operation ran out of the 128-bit carrier — the two refusal shapes
 * that need to know an operand's NULLABILITY before their sentence is complete (see
 * {@link RawRangeOverflow}). The third shape — the RAW RESULT of {@code + - *} — reports a fixed
 * {@code (38,0)&#123;not null&#125;} and is thrown fully formed, so it has no member here.
 */
public enum RawOverflowKind {
    /** The LEFT operand cannot be rescaled to the operation's common scale. */
    RESCALE_LEFT,
    /** The RIGHT operand cannot be rescaled to the operation's common scale. */
    RESCALE_RIGHT,
    /** The QUOTIENT's raw scaled integer does not fit at the division's derived scale. */
    QUOTIENT
}
