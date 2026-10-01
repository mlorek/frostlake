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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * One alternative of a numeric input format model: its elements in order, already checked by
 * {@link NumericFormatModel}, and the reading of a text against them ({@link NumericFormatReader}).
 */
final class NumericFormatAlternative {

    private final List<NumericFormatElement> kinds;
    private final List<String> spellings;
    private final int groupSize;
    private final boolean hexadecimal;
    private final boolean textMinimal;
    private final boolean explicitSign;
    private final boolean blank;

    /**
     * @param kinds the elements, in order
     * @param spellings each element as written
     * @param groupSize the TM9 group size, 0 when the model sets none
     */
    NumericFormatAlternative(final List<NumericFormatElement> kinds, final List<String> spellings,
                             final int groupSize) {
        this.kinds = kinds;
        this.spellings = spellings;
        this.groupSize = groupSize;
        this.hexadecimal = kinds.contains(NumericFormatElement.HEX);
        this.textMinimal = NumericFormatModel.containsTextMinimal(kinds);
        this.explicitSign = kinds.contains(NumericFormatElement.SIGN) || kinds.contains(NumericFormatElement.MINUS);
        this.blank = kinds.contains(NumericFormatElement.BLANK);
    }

    /**
     * AUTO: any number in positional or scientific notation, read as TM reads one — '1e5' is 100000 and
     * '1,234' and '$1' are refused (live-verified).
     *
     * @return the alternative
     */
    static NumericFormatAlternative automatic() {
        return new NumericFormatAlternative(Collections.singletonList(NumericFormatElement.TEXT_MINIMAL),
            Collections.singletonList("AUTO"), 0);
    }

    /**
     * The value the text spells under this alternative. Without an S or an MI a sign may lead the text,
     * and where the alternative starts with a literal sign the text is read again without one, so '-1'
     * under '-9' is 1 (live-verified). An alternative that starts exact places its sign itself, so its
     * text is read once.
     *
     * @param text the text
     * @return the value, or null where the text does not fit
     */
    BigDecimal read(final String text) {
        final boolean implicitSign = !explicitSign && !hexadecimal;
        final BigDecimal value = new NumericFormatReader(text, this).read(implicitSign);
        if (value == null && implicitSign && !NumericFormatReader.startsExact(this)) {
            return new NumericFormatReader(text, this).read(false);
        }
        return value;
    }

    List<NumericFormatElement> kinds() {
        return kinds;
    }

    List<String> spellings() {
        return spellings;
    }

    int groupSize() {
        return groupSize;
    }

    boolean hexadecimal() {
        return hexadecimal;
    }

    boolean textMinimal() {
        return textMinimal;
    }

    boolean blank() {
        return blank;
    }
}
