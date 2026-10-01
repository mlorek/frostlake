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

import java.util.List;

/**
 * Pipeline of operators for query execution.
 * Operators are executed in sequence, with each operator's output becoming the next operator's input.
 */
public class OperatorPipeline {
    private static final Logger logger = LoggerFactory.getLogger(OperatorPipeline.class);

    private final List<PipelineStage> stages;
    private final OperatorContext context;

    OperatorPipeline(final OperatorPipelineBuilder builder) {
        this.stages = builder.stages;
        this.context = builder.context;
    }

    /**
     * Execute the pipeline on the input rows.
     *
     * @param input The initial input rows
     * @return The final output rows after all operators have been applied
     */
    public List<Row> execute(final List<Row> input) {
        List<Row> current = input;

        logger.debug("Starting pipeline execution with {} rows", current.size());

        for (int i = 0; i < stages.size(); i++) {
            final PipelineStage stage = stages.get(i);
            final Operator operator = stage.getOperator();
            logger.debug("Executing operator {}/{}: {}", i + 1, stages.size(), operator.getDescription());

            final long startTime = System.currentTimeMillis();
            current = operator.execute(current, stage.getContext() != null ? stage.getContext() : context);
            final long elapsed = System.currentTimeMillis() - startTime;

            logger.debug("Operator {} completed in {}ms, output: {} rows",
                operator.getDescription(), elapsed, current.size());
        }

        logger.debug("Pipeline execution completed, final output: {} rows", current.size());
        return current;
    }

    /**
     * Get a description of the entire pipeline.
     *
     * @return A human-readable description of all operators in the pipeline
     */
    public String getDescription() {
        final StringBuilder sb = new StringBuilder("Pipeline[");
        for (int i = 0; i < stages.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(stages.get(i).getDescription());
        }
        sb.append("]");
        return sb.toString();
    }

    /** How many stages the pipeline runs. */
    public int stageCount() {
        return stages.size();
    }

    public static OperatorPipelineBuilder builder() {
        return new OperatorPipelineBuilder();
    }

}
