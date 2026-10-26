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

import dev.frostlake.functions.ArrayArgumentKind;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ARRAY_INSERT(array, pos, element) — inserts element at 0-based index pos, shifting later elements right.
 * pos == size appends. A negative pos is an index from the back (-1 inserts before the last element). If the
 * absolute position exceeds the array size, empty (null) elements pad the gap between the source and element.
 */
public class ArrayInsert extends BuiltInFunction {

    /** The longest run of holes padded node by node; a longer one is held as a run (see PaddedArrayElements). */
    private static final long LONGEST_PADDING = 1_000_000L;
    public ArrayInsert() { super("ARRAY_INSERT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArrayOrSingle(args.get(0));
        if (src == null) return null;
        if (args.get(1) == null) return null;
        final int n = src.size();
        // A position is read as the 32-bit whole number it gives, a fraction rounded: 0.6 is 1 (see
        // WholeNumberArgument#part). The array it makes holds at most 2,147,483,647 elements, its padding counted
        // (live-verified).
        final int pos = WholeNumberArgument.part(args.get(1));
        if ((pos >= 0 ? Math.max(n, (long) pos) : Math.max(n, -(long) pos)) + 1 > Integer.MAX_VALUE) {
            throw new RuntimeException("Result array of (" + getName() + ") is too large");
        }
        // Live: ARRAY_INSERT([1], 4, 9) is [1,undefined,undefined,undefined,9] — both the
        // inserted SQL NULL and the padding are VARIANT `undefined`, whose TYPEOF is SQL NULL.
        final JsonNode element = ArrayFunctionHelper.toElementNode(ArrayFunctionHelper.MAPPER, args.get(2));
        final ArrayNode padded = paddedPast(src, pos, element);
        if (padded != null) {
            return VariantValue.ofNodeLazily(padded);
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();

        if (pos >= 0) {
            if (pos <= n) {
                for (int i = 0; i < pos; i++) result.add(src.get(i));
                result.add(element);
                for (int i = pos; i < n; i++) result.add(src.get(i));
            } else {
                for (int i = 0; i < n; i++) result.add(src.get(i));
                for (int i = n; i < pos; i++) result.add(VariantUndefined.node());
                result.add(element);
            }
        } else {
            final int target = n + pos;
            if (target >= 0) {
                for (int i = 0; i < target; i++) result.add(src.get(i));
                result.add(element);
                for (int i = target; i < n; i++) result.add(src.get(i));
            } else {
                // A position before the first element pads the gap after the element: ARRAY_INSERT([1,2], -3, 9) is
                // [9,undefined,1,2] (live-verified).
                result.add(element);
                for (int i = target; i < 0; i++) result.add(VariantUndefined.node());
                for (int i = 0; i < n; i++) result.add(src.get(i));
            }
        }
        return VariantValue.ofNode(result);
    }

    /**
     * The array a position far past either end makes, its run of holes held without a node per hole (see
     * PaddedArrayElements): ARRAY_INSERT([1,2], 1000000000, 9) is an array of 1,000,000,001 elements
     * (live-verified). A shorter run is padded node by node, as any array is held; null then.
     */
    private static ArrayNode paddedPast(final ArrayNode src, final int pos, final JsonNode element) {
        final int n = src.size();
        final long holes = pos >= 0 ? (long) pos - n : -((long) n + pos);
        if (holes <= LONGEST_PADDING) {
            return null;
        }
        final List<JsonNode> elements = new ArrayList<JsonNode>(n);
        for (int i = 0; i < n; i++) {
            elements.add(src.get(i));
        }
        final List<JsonNode> inserted = Collections.singletonList(element);
        // Past the end the element follows the holes; before the first element it leads them.
        return new ArrayNode(ArrayFunctionHelper.MAPPER.getNodeFactory(), pos >= 0
            ? new PaddedArrayElements(elements, (int) holes, inserted)
            : new PaddedArrayElements(inserted, (int) holes, elements));
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public ArrayArgumentKind arrayArgumentKind(final int position) {
        if (position == 0) {
            return ArrayArgumentKind.ARRAY;
        }
        if (position == 1) {
            return ArrayArgumentKind.WHOLE_NUMBER;
        }
        return null;
    }
}
