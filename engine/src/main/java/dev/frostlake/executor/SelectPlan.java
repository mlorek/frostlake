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

import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.OperatorContext;
import dev.frostlake.executor.operators.OperatorPipeline;
import dev.frostlake.executor.operators.OperatorPipelineBuilder;
import dev.frostlake.executor.operators.PipelineStage;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * The plan of one SELECT — an operand over its FROM clause, a set operation over its arms' results, or a
 * FROM-less select over DUAL's one row — built by the query planner as it compiles the statement: a source
 * relation and the ordered stages that turn it into the answer, run end to end as one
 * {@link OperatorPipeline} once planning is complete.
 *
 * <p>Every stage's output shape is settled while planning, so the whole plan is built before a row flows.
 * One stage needs values for that: a PIVOT over {@code IN (ANY)} takes its column list from the distinct
 * values of its FOR column, which the account too resolves while compiling. For it the stages before are
 * {@link #materialize() materialized} while planning and the stage reads with a trailing {@code *}.
 */
final class SelectPlan {

    private final OperatorContext defaultContext;
    private final List<PipelineStage> pending = new ArrayList<>();
    private final List<String> described = new ArrayList<>();
    private List<Row> seed = new ArrayList<>();
    private String source = "";
    private Operator sourceOperator;
    private boolean sourceMarked;

    /**
     * @param executor the executor whose stages this plan runs
     * @param registry the function registry the pipeline's own context carries
     */
    SelectPlan(final QueryExecutor executor, final FunctionRegistry registry) {
        this.defaultContext = OperatorContext.builder().functionRegistry(registry).queryExecutor(executor).build();
    }

    /**
     * The relation the pipeline starts from.
     *
     * @param rows        its rows
     * @param description how it reads in the plan
     */
    void source(final List<Row> rows, final String description) {
        this.seed = rows;
        this.source = description;
    }

    /**
     * A source produced by a stage — a table's scan, a derived relation's plan, a table function — rather
     * than read while planning: the pipeline starts from nothing and the stage answers the relation's rows.
     * The stage describes itself when the plan is described, so a source read while planning after all can
     * say so.
     *
     * @param operator the stage
     */
    void sourceStage(final Operator operator) {
        sourceStage(operator, false);
    }

    /**
     * As {@link #sourceStage(Operator)}, for a stage whose shape had to be learned by running it while
     * planning, which the plan's description marks.
     *
     * @param operator              the stage
     * @param resolvedWhilePlanning whether the stage's shape had to be learned by running it while planning
     */
    void sourceStage(final Operator operator, final boolean resolvedWhilePlanning) {
        this.seed = new ArrayList<>();
        this.sourceOperator = operator;
        this.sourceMarked = resolvedWhilePlanning;
        pending.add(new PipelineStage(operator, null));
    }

    /** Plans a stage that runs under the pipeline's own context. */
    void add(final Operator operator) {
        add(new PipelineStage(operator, null));
    }

    /** Plans a stage that runs under its own context. */
    void add(final Operator operator, final OperatorContext context) {
        add(new PipelineStage(operator, context));
    }

    void add(final PipelineStage stage) {
        pending.add(stage);
        described.add(stage.getDescription());
    }

    /**
     * Runs every stage planned so far, now, because the next stage's columns can only be known from
     * these rows. The rows answered become the source of what is planned next.
     *
     * @return the rows the stages so far produce
     */
    List<Row> materialize() {
        seed = runPending();
        return seed;
    }

    /**
     * Plans a stage whose own shape is only known from the rows it produces — a FROM-less projection types
     * its items as it computes them — and runs the pipeline so far, now, to learn it. The stage reads marked
     * in the plan's description; the rows it answers become the source of what is planned next.
     *
     * @param operator the stage
     * @return the rows the stages so far produce
     */
    List<Row> materialize(final Operator operator) {
        addResolvedWhilePlanning(operator);
        return materialize();
    }

    /**
     * Plans a stage whose shape was resolved from the rows before it, already {@link #materialize()
     * materialized}: it runs in the pipeline like any other, and reads marked in the plan's description.
     */
    void addResolvedWhilePlanning(final Operator operator) {
        pending.add(new PipelineStage(operator, null));
        described.add(operator.getDescription() + "*");
    }

    /**
     * Runs the pipeline end to end: the stages not run while planning, over the rows those produced.
     *
     * @return the answer's rows
     */
    List<Row> execute() {
        return run();
    }

    private List<Row> runPending() {
        final List<Row> rows = run();
        pending.clear();
        return rows;
    }

    private List<Row> run() {
        if (pending.isEmpty()) {
            return seed;
        }
        final OperatorPipelineBuilder builder = OperatorPipeline.builder().context(defaultContext);
        for (final PipelineStage stage : pending) {
            builder.addStage(stage);
        }
        return builder.build().execute(seed);
    }

    /** The plan as text: the source, then every stage in order. */
    String describe() {
        return "Plan[" + describeStages() + "]";
    }

    /** The source and the stages in order, as a nested plan reads inside the stage that runs it. */
    String describeStages() {
        final StringBuilder text = new StringBuilder(sourceOperator != null
            ? sourceOperator.getDescription() + (sourceMarked ? "*" : "") : source);
        for (final String stage : described) {
            text.append(" -> ").append(stage);
        }
        return text.toString();
    }
}
