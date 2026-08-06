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

package dev.frostlake.ai;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/** TRY_COMPLETE — COMPLETE, but a failed call is NULL instead of an error. */
public class CortexTryComplete extends BuiltInFunction {

    private final CortexComplete complete = new CortexComplete("SNOWFLAKE.CORTEX.COMPLETE");

    public CortexTryComplete(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        try {
            return complete.evaluate(args);
        } catch (final RuntimeException failed) {
            return null;
        }
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
