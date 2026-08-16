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
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.BooleanType;
import java.util.List;
import tools.jackson.databind.JsonNode;

public class IsNullValue extends StructuredArgumentFunction {
    public IsNullValue() { super("IS_NULL_VALUE", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object v = args.get(0);
        // A SQL NULL input yields SQL NULL; only a JSON null (which now arrives as the VARIANT text "null",
        // not a Java null) yields TRUE (Snowflake). A missing JSON value is a SQL NULL, so it yields NULL too.
        if (v == null) return null;
        final JsonNode node = JsonTypeHelper.parse(v);
        return node != null && node.isNull();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
