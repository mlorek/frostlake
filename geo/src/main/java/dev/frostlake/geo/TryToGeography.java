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

package dev.frostlake.geo;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.GeographyType;

import java.util.List;

/** TRY_TO_GEOGRAPHY — TO_GEOGRAPHY that returns NULL instead of erroring. */
public class TryToGeography extends BuiltInFunction {

    private static final ToGeography BASE = new ToGeography();

    public TryToGeography() { super("TRY_TO_GEOGRAPHY", GeographyType.GEOGRAPHY); }

    @Override
    public Object evaluate(final List<Object> args) {
        try {
            return BASE.evaluate(args);
        } catch (final RuntimeException invalid) {
            return null;
        }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
