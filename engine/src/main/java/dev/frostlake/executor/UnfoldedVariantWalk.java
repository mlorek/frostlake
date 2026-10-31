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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.types.DataType;
import dev.frostlake.types.VariantType;

import java.util.Collections;
import java.util.Locale;

/**
 * Whether a session variable's source holds a VARIANT the account's folder does not take (see
 * SessionAssignmentSource): a conversion to VARIANT folds only over a literal, so {@code NULL::VARIANT::VARIANT},
 * {@code TO_VARIANT(NULL::VARCHAR)} and {@code TO_VARIANT(TO_VARIANT(NULL))} do not, while {@code NULL::VARIANT},
 * {@code TO_VARIANT(NULL)} and {@code TO_VARIANT(1)::NUMBER} do; and an IS NULL over a VARIANT does not fold,
 * {@code NULL::VARIANT IS NULL} or {@code (SELECT TO_VARIANT(1)) IS NULL} (all live-verified). The queries the source
 * holds are not folded, so the walk does not enter them.
 *
 * <p>It subclasses the printer for its traversal only; the printed text is discarded.
 */
final class UnfoldedVariantWalk extends AstPrinterVisitor {

    private final QueryExecutor executor;
    private boolean unfolded;

    private UnfoldedVariantWalk(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Whether the source holds such a VARIANT.
     *
     * @param source   the source, parsed
     * @param executor the executor that types the operands of an IS NULL
     * @return whether it does
     */
    static boolean holdsUnfolded(final Expression source, final QueryExecutor executor) {
        if (source == null) {
            return false;
        }
        final UnfoldedVariantWalk walk = new UnfoldedVariantWalk(executor);
        source.accept(walk);
        return walk.unfolded;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";
    }

    @Override
    public String visitCast(final CastExpression expr) {
        if (expr.getTargetDataType() instanceof VariantType && !(expr.getExpression() instanceof LiteralExpression)) {
            unfolded = true;
        }
        return super.visitCast(expr);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        if (expr.getFunctionName() != null && "TO_VARIANT".equals(expr.getFunctionName().toUpperCase(Locale.ROOT))
                && expr.getArguments().size() == 1 && !(expr.getArguments().get(0) instanceof LiteralExpression)) {
            unfolded = true;
        }
        return super.visitFunctionCall(expr);
    }

    @Override
    public String visitIsNull(final IsNullExpression expr) {
        if (staticTypeOf(expr.getOperand()) instanceof VariantType) {
            unfolded = true;
        }
        return super.visitIsNull(expr);
    }

    private DataType staticTypeOf(final Expression operand) {
        try {
            return DeclarationTypes.staticType(operand, Collections.<String, DataType>emptyMap(), executor);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }
}
