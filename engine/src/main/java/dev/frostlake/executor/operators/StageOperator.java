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

import java.util.List;

/**
 * An operator whose work is a plain rows-in, rows-out function that needs no {@link OperatorContext}: the
 * shape of a query stage whose evaluators were built while planning. Subclasses implement
 * {@link #apply(List)}; the description names the stage in a plan.
 */
public abstract class StageOperator implements Operator {

    private final String description;

    /**
     * @param description how the stage reads in a plan
     */
    protected StageOperator(final String description) {
        this.description = description;
    }

    @Override
    public final List<Row> execute(final List<Row> input, final OperatorContext context) {
        return apply(input);
    }

    /**
     * The stage's work.
     *
     * @param input the rows in
     * @return the rows out
     */
    protected abstract List<Row> apply(final List<Row> input);

    @Override
    public String getDescription() {
        return description;
    }
}
