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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.StructuredArgumentFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.BooleanType;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** IS_DOUBLE(v) — TRUE when the variant holds any number (Snowflake: DOUBLE, INTEGER or DECIMAL). */
public class IsDoubleFn extends StructuredArgumentFunction {
    public IsDoubleFn() { super("IS_DOUBLE", BooleanType.BOOLEAN); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final JsonNode node = ArrayFunctionHelper.parseNode(args.get(0));
        return node != null && node.isNumber();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
