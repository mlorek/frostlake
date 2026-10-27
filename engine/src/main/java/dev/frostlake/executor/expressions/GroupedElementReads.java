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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.DateTimeType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where an expression of a grouped query converts an element (see StoredRead). A grouping key is computed over each
 * row before the rows are grouped, so a conversion inside a key reads the stored element; one outside every key
 * reads the grouped value. {@code TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') ... GROUP BY a[0]::DATE} converts its element
 * as the rows hold it, where {@code TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') ... GROUP BY a[0]} converts the key's value
 * and is refused (both live-verified).
 */
public final class GroupedElementReads {

    private GroupedElementReads() {
    }

    /**
     * Whether an expression holds a grouping key and converts an element only inside keys, so it may read the rows
     * as they are. A node of a kind the walk does not look into counts as a conversion outside the keys.
     *
     * @param expression the expression, parsed
     * @param keys       the canonical prints of the keys (see {@link AstPrinterVisitor#print})
     * @return whether it reads the rows as they are
     */
    public static boolean convertsOnlyInsideKeys(final Expression expression, final Set<String> keys) {
        final boolean[] holdsKey = new boolean[1];
        return !convertsOutsideKeys(expression, keys, holdsKey) && holdsKey[0];
    }

    /** Whether a conversion of an element lies outside every key; {@code holdsKey} learns of each key met. */
    private static boolean convertsOutsideKeys(final Expression expr, final Set<String> keys,
                                               final boolean[] holdsKey) {
        if (expr == null || expr instanceof LiteralExpression || expr instanceof ColumnReferenceExpression) {
            return false;
        }
        if (keys.contains(AstPrinterVisitor.print(expr))) {
            holdsKey[0] = true;
            return false;
        }
        if (expr instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expr;
            if (VariantRowElements.elementBase(cast.getExpression()) != null
                    && TypeInferencer.typeForName(cast.getTargetType()) instanceof DateTimeType) {
                return true;
            }
            return convertsOutsideKeys(cast.getExpression(), keys, holdsKey);
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            final List<Expression> arguments = call.getArguments();
            if (call.getFunctionName() != null && !arguments.isEmpty()
                    && VariantTemporalCasts.conversionTarget(call.getFunctionName().toUpperCase(Locale.ROOT)) != null
                    && VariantRowElements.elementBase(arguments.get(0)) != null) {
                return true;
            }
            for (final Expression argument : arguments) {
                if (convertsOutsideKeys(argument, keys, holdsKey)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof BinaryOperationExpression) {
            return convertsOutsideKeys(((BinaryOperationExpression) expr).getLeft(), keys, holdsKey)
                || convertsOutsideKeys(((BinaryOperationExpression) expr).getRight(), keys, holdsKey);
        }
        if (expr instanceof UnaryOperationExpression) {
            return convertsOutsideKeys(((UnaryOperationExpression) expr).getOperand(), keys, holdsKey);
        }
        if (expr instanceof ArrayAccessExpression) {
            return convertsOutsideKeys(((ArrayAccessExpression) expr).getArray(), keys, holdsKey)
                || convertsOutsideKeys(((ArrayAccessExpression) expr).getIndex(), keys, holdsKey);
        }
        if (expr instanceof ObjectAccessExpression) {
            return convertsOutsideKeys(((ObjectAccessExpression) expr).getBase(), keys, holdsKey);
        }
        return true;
    }
}
