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

package dev.frostlake.functions.scalar.hash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The digest size the SHA2 family's second argument asks for. It is a number, or a text that spells one,
 * rounded half away from zero, and it must come out as 224, 256, 384 or 512: anything else is "Invalid
 * parameter value: 100. Reason: invalid SHA2 digest size", the rounded value echoed — a NULL size is the
 * value 0 — and a text that spells no number "Numeric value 'abc' is not recognized". The size is judged
 * before the message is read, so a NULL message beside a size that is none is refused all the same
 * (live-verified).
 */
final class Sha2DigestSize {

    private static final int DEFAULT_BITS = 256;

    private Sha2DigestSize() {
    }

    /**
     * The digest size a call asks for.
     *
     * @param args the call's arguments, the size second when it is written
     * @return 224, 256, 384 or 512
     */
    static int of(final List<Object> args) {
        if (args.size() < 2) {
            return DEFAULT_BITS;
        }
        final BigDecimal bits = rounded(args.get(1));
        if (bits.compareTo(BigDecimal.valueOf(224)) != 0 && bits.compareTo(BigDecimal.valueOf(DEFAULT_BITS)) != 0
                && bits.compareTo(BigDecimal.valueOf(384)) != 0 && bits.compareTo(BigDecimal.valueOf(512)) != 0) {
            throw new RuntimeException("Invalid parameter value: " + bits.toPlainString()
                + ". Reason: invalid SHA2 digest size");
        }
        return bits.intValue();
    }

    private static BigDecimal rounded(final Object size) {
        if (size == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal written;
        if (size instanceof Number) {
            written = new BigDecimal(size.toString());
        } else {
            try {
                written = new BigDecimal(size.toString().trim());
            } catch (final NumberFormatException noNumber) {
                throw new RuntimeException("Numeric value '" + size + "' is not recognized");
            }
        }
        return written.setScale(0, RoundingMode.HALF_UP);
    }
}
