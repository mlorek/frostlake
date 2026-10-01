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
 * One planned step of an {@link OperatorPipeline}: an operator and the context it runs under. A stage
 * planned without a context runs under the pipeline's own.
 */
public final class PipelineStage {

    private final Operator operator;
    private final OperatorContext context;

    /**
     * @param operator the operator
     * @param context  the context the operator runs under, or null for the pipeline's own
     */
    public PipelineStage(final Operator operator, final OperatorContext context) {
        this.operator = operator;
        this.context = context;
    }

    public Operator getOperator() {
        return operator;
    }

    /** The stage's own context, or null when it takes the pipeline's. */
    public OperatorContext getContext() {
        return context;
    }

    public String getDescription() {
        return operator.getDescription();
    }

    /**
     * Runs the stage on its own, outside a pipeline.
     *
     * @param input the rows in
     * @return the rows out
     */
    public List<Row> run(final List<Row> input) {
        return operator.execute(input, context);
    }
}
