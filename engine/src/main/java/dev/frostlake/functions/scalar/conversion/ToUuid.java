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
import dev.frostlake.types.StringType;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TO_UUID(s) — the canonical lowercase form of a UUID string. Snowflake accepts ONLY the hyphenated
 * 8-4-4-4-12 form (live-verified: hyphenless and brace-wrapped inputs are errors), so this does too.
 */
public class ToUuid extends BuiltInFunction {

    private static final Pattern HYPHENATED_UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public ToUuid() { super("TO_UUID", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        return canonical(args.get(0).toString());
    }

    /** The lowercase canonical form, or an error mirroring Snowflake's message for invalid input. */
    static String canonical(final String input) {
        if (!HYPHENATED_UUID.matcher(input).matches()) {
            throw new RuntimeException("UUID '" + input + "' is invalid, expected format is "
                + "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (8-4-4-4-12 hexadecimal digits with hyphens)");
        }
        return input.toLowerCase();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
