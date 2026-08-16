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

import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * LIMIT operator - restricts the number of rows returned.
 * Supports both LIMIT N and LIMIT N OFFSET M syntax.
 */
public class LimitOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(LimitOperator.class);

    private final int limit;
    private final int offset;

    public LimitOperator(final int limit, final int offset) {
        this.limit = limit;
        this.offset = offset;
    }

    public LimitOperator(final int limit) {
        this(limit, 0);
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (input.isEmpty()) {
            return input;
        }

        final int startIndex = offset;
        final int endIndex = Math.min(offset + limit, input.size());

        if (startIndex >= input.size()) {
            logger.debug("LIMIT offset {} exceeds input size {}, returning empty result",
                offset, input.size());
            return new ArrayList<>();
        }

        final List<Row> result = new ArrayList<>(input.subList(startIndex, endIndex));

        logger.debug("LIMIT {} OFFSET {}: {} -> {} rows",
            limit, offset, input.size(), result.size());

        return result;
    }

    @Override
    public String getDescription() {
        if (offset > 0) {
            return String.format("LIMIT[%d OFFSET %d]", limit, offset);
        }
        return String.format("LIMIT[%d]", limit);
    }
}
