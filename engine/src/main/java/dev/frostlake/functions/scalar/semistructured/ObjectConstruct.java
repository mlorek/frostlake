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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ObjectType;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * OBJECT_CONSTRUCT(k1, v1, k2, v2, ...) — builds an object from key-value pairs.
 * Also supports OBJECT_CONSTRUCT(*) which is handled as no-args → empty object.
 */
public class ObjectConstruct extends BuiltInFunction {
    public ObjectConstruct() { super("OBJECT_CONSTRUCT", ObjectType.OBJECT); }

    protected ObjectConstruct(final String name) { super(name, ObjectType.OBJECT); }

    /**
     * A STRUCTURED value is refused in either half of the alternating argument list, but with a
     * different sentence in each. Live, {@code OBJECT_CONSTRUCT('a', so)} and
     * {@code OBJECT_CONSTRUCT('a', 1, 'b', so)} are "Function OBJECT_CONSTRUCT does not support
     * OBJECT(x VARCHAR(16777216)) argument type" (vendor code 2016) while {@code OBJECT_CONSTRUCT(so,
     * 1)} — the same value used as a KEY — appends " for keys" and carries vendor code 2270 instead.
     * The plain types are accepted in the value half ({@code OBJECT_CONSTRUCT('a', o)} nests the
     * object), so this is a divergence and not a general semi-structured rule.
     *
     * <p>Inherited by {@code OBJECT_CONSTRUCT_KEEP_NULL}, which subclasses this and was measured to
     * behave identically.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return position % 2 == 0 ? SemiStructuredRejection.UNSUPPORTED_KEY_ARGUMENT_TYPE
            : SemiStructuredRejection.UNSUPPORTED_ARGUMENT_TYPE;
    }

    /**
     * A FILE splits the two halves differently again: it NESTS as a value and is refused as a KEY.
     * Live, {@code OBJECT_CONSTRUCT('a', f)} returns {@code {"a":{"CONTENT_TYPE":…}}} while
     * {@code OBJECT_CONSTRUCT(f, 1)} is "Function OBJECT_CONSTRUCT does not support FILE argument type
     * for keys" — the key sentence a structured value gets, with the value half left alone.
     */
    @Override
    public SemiStructuredRejection fileRejection(final int position) {
        return position % 2 == 0 ? SemiStructuredRejection.UNSUPPORTED_KEY_ARGUMENT_TYPE
            : SemiStructuredRejection.NONE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return build(args, false);
    }

    protected static Object build(final List<Object> args, final boolean keepNull) {
        ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (int i = 0; i + 1 < args.size(); i += 2) {
            if (args.get(i) == null) continue;
            Object value = args.get(i + 1);
            if (!keepNull && value == null) continue;
            String key = args.get(i).toString();
            obj.set(key, ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
        }
        return ArrayFunctionHelper.toCanonicalVariant(obj);
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
