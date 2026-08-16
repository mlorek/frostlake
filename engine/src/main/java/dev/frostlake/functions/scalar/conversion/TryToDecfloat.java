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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * TRY_TO_DECFLOAT(expr [, format]) — {@link ToDecfloat} answering NULL where TO_DECFLOAT fails on the
 * value: a text that spells no number, a format model the text does not fit, and a model that is not one.
 * It reads text as TO_DECFLOAT does, not as TRY_TO_DOUBLE: a non-finite word, a {@code d} suffix and hex
 * digits are NULL here where TRY_TO_DOUBLE answers inf, 1 and 16 (all live-verified):
 *
 * <pre>
 *   TRY_TO_DECFLOAT('1.5')            1.5          TRY_TO_DECFLOAT(' 1.5 ')     1.5
 *   TRY_TO_DECFLOAT('inf')            NULL         TRY_TO_DECFLOAT('nan')       NULL
 *   TRY_TO_DECFLOAT('1d')             NULL         TRY_TO_DECFLOAT('0x10')      NULL
 *   TRY_TO_DECFLOAT('1e5', '9EEEE')   100000       TRY_TO_DECFLOAT('123', '99') NULL
 *   TRY_TO_DECFLOAT('1e5', '9e9')     NULL         a model that is not one
 * </pre>
 *
 * <p>A BOOLEAN is NULL, as it was when this function read text as TRY_TO_DOUBLE.
 */
public class TryToDecfloat extends BuiltInFunction {

    private static final ToDecfloat BASE = new ToDecfloat();

    public TryToDecfloat() {
        super("TRY_TO_DECFLOAT", NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(0) instanceof Boolean) {
            return null;
        }
        try {
            return BASE.evaluate(args);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
