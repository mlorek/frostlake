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

package dev.frostlake.functions.scalar.conversion;

/** One element of a numeric input format model, as {@link NumericFormatModel} scans it. */
public enum NumericFormatElement {
    /** {@code 9}: a digit position that may be left out. */
    DIGIT,
    /** {@code 0}: a digit position that must be written, in a hexadecimal model as much as a decimal one. */
    ZERO,
    /** {@code X}: a hexadecimal digit position. */
    HEX,
    /** {@code ,} or {@code G}: a digit group separator. */
    GROUP,
    /** {@code .} or {@code D}: the decimal point. */
    DECIMAL,
    /** {@code $}: the currency sign. */
    DOLLAR,
    /** {@code %}: the value written as a percentage. */
    PERCENT,
    /** {@code B}: a number that may start at its decimal point. */
    BLANK,
    /** {@code EE} to {@code EEEEEEE}: an exponent. */
    EXPONENT,
    /** {@code S}: a sign that must be written. */
    SIGN,
    /** {@code MI}: a sign that may be written. */
    MINUS,
    /** {@code FM}: the fill-mode toggle. */
    FILL_MODE,
    /** {@code FX}: the exact-match toggle. */
    EXACT_MODE,
    /** {@code _}: spaces that may be written. */
    OPTIONAL_SPACE,
    /** {@code TM}: a number in positional or scientific notation. */
    TEXT_MINIMAL,
    /** {@code TM9}: a number in positional notation. */
    TEXT_MINIMAL_POSITIONAL,
    /** {@code TME}: a number in scientific notation. */
    TEXT_MINIMAL_SCIENTIFIC,
    /** Literal text: a quoted string, or characters a model may carry as themselves. */
    LITERAL
}
