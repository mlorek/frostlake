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

import dev.frostlake.parser.FrostlakeParser;

/**
 * A value an ALTER writes for a parameter, by the kind it is written in - a text, a whole number, a number with a
 * fraction or a switch value - which the parameter's type is judged against before anything else (see
 * ParameterLevels).
 */
final class WrittenParameterValue {

    private final String written;
    private final boolean text;
    private final boolean whole;
    private final boolean fraction;
    private final boolean flag;

    private WrittenParameterValue(final String written, final boolean text, final boolean whole,
                                  final boolean fraction, final boolean flag) {
        this.written = written;
        this.text = text;
        this.whole = whole;
        this.fraction = fraction;
        this.flag = flag;
    }

    /**
     * An ALTER TABLE, DATABASE or SCHEMA … SET's value.
     *
     * @param property the property set
     * @return the value, or null for a parenthesized list
     */
    static WrittenParameterValue of(final FrostlakeParser.TableSetPropertyContext property) {
        if (property.FLOAT_LITERAL() != null) {
            return new WrittenParameterValue((property.MINUS() != null ? "-" : "") + property.FLOAT_LITERAL().getText(),
                false, false, true, false);
        }
        final FrostlakeParser.CopyOptionValueContext value = property.copyOptionValue();
        if (value == null) {
            return null;
        }
        final boolean whole = value.INTEGER_LITERAL() != null;
        final boolean flag = value.booleanValue() != null;
        return new WrittenParameterValue(value.getText(), !whole && !flag, whole, false, flag);
    }

    /**
     * An ALTER ALERT … SET's value.
     *
     * @param value the value
     * @return the value, or null for a parenthesized list
     */
    static WrittenParameterValue of(final FrostlakeParser.TaskPropertyValueContext value) {
        if (value == null || value.LPAREN() != null) {
            return null;
        }
        final boolean whole = value.INTEGER_LITERAL() != null;
        final boolean fraction = value.FLOAT_LITERAL() != null;
        final boolean flag = value.booleanValue() != null;
        return new WrittenParameterValue(value.getText(), !whole && !fraction && !flag, whole, fraction, flag);
    }

    /**
     * Whether a parameter of a type takes the value: a BOOLEAN TRUE or FALSE, a NUMBER a whole number, a FLOAT a
     * number written with a fraction (7 is refused, 10.0 taken) and a STRING a text.
     *
     * @param type the parameter's type, or null for a name of no listed type
     * @return whether it takes it; true for any other type
     */
    boolean fits(final String type) {
        if ("BOOLEAN".equals(type)) {
            return flag;
        }
        if ("NUMBER".equals(type)) {
            return whole;
        }
        if ("FLOAT".equals(type)) {
            return fraction;
        }
        if ("STRING".equals(type)) {
            return text;
        }
        return true;
    }

    /** The value as written, as the refusal echoes it. */
    String written() {
        return written;
    }
}
