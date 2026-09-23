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

import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One operand of a set operation as the plan combines it: a select of its own, or a combination of operands
 * under one operator. The plan reads the written chain with INTERSECT binding tighter than UNION, UNION ALL and
 * MINUS, which associate to the left; a run of one operator is one combination of all its operands —
 * {@code 1 UNION ALL 2 UNION ALL 3} combines three arms, {@code 1 UNION 2 UNION ALL 3} is the UNION of two
 * combined with the third, and {@code 1 UNION ALL 2 INTERSECT 3} is the INTERSECT of the last two combined with
 * the first (live-verified through the re-print a refusal gives a set-operation subquery).
 */
final class SetOperationNode {

    /** The operator whose run binds before the others. */
    static final String INTERSECT = "INTERSECT";

    private final FrostlakeParser.SelectOperandContext operand;
    private final String operator;
    private final List<SetOperationNode> arms;

    private SetOperationNode(final FrostlakeParser.SelectOperandContext operand, final String operator,
                             final List<SetOperationNode> arms) {
        this.operand = operand;
        this.operator = operator;
        this.arms = arms;
    }

    /** One written operand. */
    static SetOperationNode leaf(final FrostlakeParser.SelectOperandContext operand) {
        return new SetOperationNode(operand, null, Collections.<SetOperationNode>emptyList());
    }

    /** Operands combined under one operator. */
    static SetOperationNode combination(final String operator, final List<SetOperationNode> arms) {
        return new SetOperationNode(null, operator, new ArrayList<SetOperationNode>(arms));
    }

    /**
     * The tree a written chain plans as.
     *
     * @param operands  the chain's operands, each already a leaf or a combination of its own
     * @param operators the operators between them, as the plan spells them, one fewer than the operands
     * @return the root combination, or the one operand
     */
    static SetOperationNode of(final List<SetOperationNode> operands, final List<String> operators) {
        final List<SetOperationNode> terms = new ArrayList<>();
        final List<String> termOperators = new ArrayList<>();
        List<SetOperationNode> run = new ArrayList<>();
        run.add(operands.get(0));
        for (int i = 0; i < operators.size(); i++) {
            if (INTERSECT.equals(operators.get(i))) {
                run.add(operands.get(i + 1));
                continue;
            }
            terms.add(run.size() == 1 ? run.get(0) : combination(INTERSECT, run));
            termOperators.add(operators.get(i));
            run = new ArrayList<>();
            run.add(operands.get(i + 1));
        }
        terms.add(run.size() == 1 ? run.get(0) : combination(INTERSECT, run));
        SetOperationNode combined = terms.get(0);
        String chainOperator = null;
        List<SetOperationNode> chain = new ArrayList<>();
        chain.add(combined);
        for (int i = 0; i < termOperators.size(); i++) {
            final String next = termOperators.get(i);
            if (chainOperator != null && !chainOperator.equals(next)) {
                combined = combination(chainOperator, chain);
                chain = new ArrayList<>();
                chain.add(combined);
            }
            chainOperator = next;
            chain.add(terms.get(i + 1));
        }
        return chainOperator == null ? combined : combination(chainOperator, chain);
    }

    /** The written operand of a leaf, or null for a combination. */
    FrostlakeParser.SelectOperandContext operand() {
        return operand;
    }

    /** A combination's operator as the plan spells it, or null for a leaf. */
    String operator() {
        return operator;
    }

    /** A combination's arms in written order; empty for a leaf. */
    List<SetOperationNode> arms() {
        return arms;
    }

    boolean isLeaf() {
        return operator == null;
    }
}
