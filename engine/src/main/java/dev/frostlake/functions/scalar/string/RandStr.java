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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.types.StringType;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

/** RANDSTR(length, generator) — a deterministic random alphanumeric string seeded by the generator. */
public class RandStr extends TextArgumentFunction {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** The longest text RANDSTR makes. */
    private static final int MAX_LENGTH = 134_217_728;

    public RandStr() { super("RANDSTR", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // The length is judged before the seed is read: a NULL one and a negative one are refused, where a NULL
        // seed answers NULL (live-verified).
        if (args.get(0) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: length must not be NULL");
        }
        // A text is read as the number it spells and a fraction goes to the nearest whole number, through the 64-bit
        // slot (see WholeNumberArgument#wideWhole); past 134217728 characters the length is refused (live-verified).
        final BigInteger asked = WholeNumberArgument.wideWhole(args.get(0));
        if (asked.signum() < 0) {
            throw new RuntimeException("Invalid parameter value: " + asked + ". Reason: length must not be negative");
        }
        if (asked.compareTo(BigInteger.valueOf(MAX_LENGTH)) > 0) {
            throw new RuntimeException("Invalid parameter value: " + asked + ". Reason: length must be at most "
                + MAX_LENGTH);
        }
        final int length = asked.intValue();
        if (args.get(1) == null) return null;
        final long seed = (long) Double.parseDouble(args.get(1).toString());
        final Random random = new Random(seed);
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
