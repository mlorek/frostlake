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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QUALIFY clause operator - filters rows based on window function results.
 *
 * QUALIFY is similar to HAVING but works on window functions instead of aggregates.
 * It filters rows after window functions have been computed.
 */
public class QualifyOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(QualifyOperator.class);

    private final String qualifyExpression;
    private final QualifyEvaluator qualifyEvaluator;

    public QualifyOperator(final String qualifyExpression, final QualifyEvaluator qualifyEvaluator) {
        this.qualifyExpression = qualifyExpression;
        this.qualifyEvaluator = qualifyEvaluator;
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (qualifyExpression == null || qualifyExpression.trim().isEmpty()) {
            return input;
        }

        logger.debug("Applying QUALIFY filter: {}", qualifyExpression);

        final Expression condition = ExpressionEvaluator.parse(qualifyExpression);
        List<Row> filtered = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < input.size(); rowIdx++) {
            Row row = input.get(rowIdx);
            try {
                boolean passes = qualifyEvaluator.evaluate(condition, row, rowIdx);
                if (passes) {
                    filtered.add(row);
                }
            } catch (final Exception e) {
                logger.warn("Failed to evaluate QUALIFY condition for row {}: {}", rowIdx, e.getMessage());
            }
        }

        logger.debug("QUALIFY filter: {} -> {} rows", input.size(), filtered.size());
        return filtered;
    }

    @Override
    public String getDescription() {
        return String.format("QUALIFY[%s]",
            qualifyExpression.length() > 50 ? qualifyExpression.substring(0, 47) + "..." : qualifyExpression);
    }
}
