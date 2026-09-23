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

/**
 * HAVING clause operator - filters rows after GROUP BY based on aggregate conditions.
 *
 * HAVING is applied after GROUP BY and filters rows based on aggregate values.
 * Unlike WHERE (which filters before grouping), HAVING filters after aggregation.
 */
public class HavingOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(HavingOperator.class);

    private final String havingExpression;
    private final HavingEvaluator havingEvaluator;

    public HavingOperator(final String havingExpression, final HavingEvaluator havingEvaluator) {
        this.havingExpression = havingExpression;
        this.havingEvaluator = havingEvaluator;
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (havingExpression == null || havingExpression.trim().isEmpty()) {
            return input;
        }

        logger.debug("Applying HAVING filter: {}", havingExpression);

        final Expression condition = ExpressionEvaluator.parse(havingExpression);
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : input) {
            // A value the condition cannot compute refuses the statement rather than dropping the group:
            // HAVING CAST(s AS VARCHAR(5)) = 'x' over a longer s is live's truncation refusal.
            if (havingEvaluator.evaluate(condition, row)) {
                filtered.add(row);
            }
        }

        logger.debug("HAVING filter: {} -> {} rows", input.size(), filtered.size());
        return filtered;
    }

    @Override
    public String getDescription() {
        return String.format("HAVING[%s]",
            havingExpression.length() > 50 ? havingExpression.substring(0, 47) + "..." : havingExpression);
    }
}
