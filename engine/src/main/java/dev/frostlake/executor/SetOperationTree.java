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

import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A set operation's operands grouped the way live evaluates them: INTERSECT before UNION, EXCEPT and MINUS,
 * which apply left to right. A leaf is one operand as written — a select, or a parenthesized statement.
 */
final class SetOperationTree {

    private final FrostlakeParser.SelectOperandContext operand;
    private final FrostlakeParser.SetOperatorContext operator;
    private final SetOperationTree left;
    private final SetOperationTree right;

    private SetOperationTree(final FrostlakeParser.SelectOperandContext operand,
                             final FrostlakeParser.SetOperatorContext operator,
                             final SetOperationTree left, final SetOperationTree right) {
        this.operand = operand;
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    /** The tree of a statement's operands; a statement of one operand is a leaf. */
    static SetOperationTree of(final FrostlakeParser.SelectStatementContext statement) {
        final List<SetOperationTree> terms = new ArrayList<>();
        final List<FrostlakeParser.SetOperatorContext> operators = new ArrayList<>();
        SetOperationTree term = leaf(statement.selectOperand(0));
        for (int i = 1; i < statement.selectOperand().size(); i++) {
            final FrostlakeParser.SetOperatorContext written = statement.setOperator(i - 1);
            final SetOperationTree next = leaf(statement.selectOperand(i));
            if (written.INTERSECT() != null) {
                term = new SetOperationTree(null, written, term, next);
            } else {
                terms.add(term);
                operators.add(written);
                term = next;
            }
        }
        terms.add(term);
        SetOperationTree tree = terms.get(0);
        for (int i = 0; i < operators.size(); i++) {
            tree = new SetOperationTree(null, operators.get(i), tree, terms.get(i + 1));
        }
        return tree;
    }

    private static SetOperationTree leaf(final FrostlakeParser.SelectOperandContext operand) {
        return new SetOperationTree(operand, null, null, null);
    }

    boolean isLeaf() {
        return operand != null;
    }

    /** Whether this node is a UNION, with or without ALL or BY NAME. */
    boolean isUnion() {
        return operator != null && operator.UNION() != null;
    }

    /** The operand of a leaf, or null. */
    FrostlakeParser.SelectOperandContext operand() {
        return operand;
    }

    SetOperationTree left() {
        return left;
    }

    SetOperationTree right() {
        return right;
    }

    /** Every operand beneath this node, in the order written. */
    List<FrostlakeParser.SelectOperandContext> operands() {
        final List<FrostlakeParser.SelectOperandContext> all = new ArrayList<>();
        collectOperands(this, all);
        return all;
    }

    private static void collectOperands(final SetOperationTree node,
                                        final List<FrostlakeParser.SelectOperandContext> into) {
        if (node.isLeaf()) {
            into.add(node.operand);
            return;
        }
        collectOperands(node.left, into);
        collectOperands(node.right, into);
    }
}
