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

import java.util.ArrayList;
import java.util.List;

public class OperatorPipelineBuilder {
    // Package-private, not private: read by {@link OperatorPipeline} now that this class is a
    // top-level type in the same package rather than a nested one.
    final List<PipelineStage> stages = new ArrayList<>();
    OperatorContext context;

    /** Adds a stage that runs under the pipeline's context. */
    public OperatorPipelineBuilder addOperator(final Operator operator) {
        return addStage(new PipelineStage(operator, null));
    }

    /** Adds a stage that runs under its own context. */
    public OperatorPipelineBuilder addStage(final Operator operator, final OperatorContext stageContext) {
        return addStage(new PipelineStage(operator, stageContext));
    }

    public OperatorPipelineBuilder addStage(final PipelineStage stage) {
        this.stages.add(stage);
        return this;
    }

    public OperatorPipelineBuilder context(final OperatorContext context) {
        this.context = context;
        return this;
    }

    public OperatorPipeline build() {
        if (context == null) {
            throw new IllegalArgumentException("OperatorContext is required");
        }
        return new OperatorPipeline(this);
    }
}
