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

package dev.frostlake.executor.rowpattern;

import java.util.ArrayList;
import java.util.List;

/**
 * A row pattern compiled to the steps a backtracking matcher runs: read a row as a symbol, try one way and
 * keep the other for later, jump, test an anchor, open or close an exclusion, and report the match. A quantifier
 * is unrolled — its least repetitions in a row, then either a loop or the optional rest — and a loop refuses a
 * repetition that read no row, so a pattern that can match nothing over and over still ends.
 */
final class RowPatternProgram {

    static final int SYMBOL = 0;
    static final int SPLIT = 1;
    static final int JUMP = 2;
    static final int PARTITION_START = 3;
    static final int PARTITION_END = 4;
    static final int EXCLUDE = 5;
    static final int INCLUDE = 6;
    static final int MARK = 7;
    static final int ADVANCED = 8;
    static final int MATCH = 9;

    /** A pattern is refused past this many steps: an unrolled quantifier would not end in reasonable time. */
    private static final int LIMIT = 200_000;

    private final List<int[]> steps = new ArrayList<>();
    private final List<String> symbols = new ArrayList<>();
    private int marks;

    RowPatternProgram(final RowPatternNode pattern) {
        compile(pattern);
        add(MATCH, 0, 0);
    }

    int size() {
        return steps.size();
    }

    int operation(final int step) {
        return steps.get(step)[0];
    }

    /** A step's first operand: the symbol read, the way tried first, the jump's target or the mark's number. */
    int first(final int step) {
        return steps.get(step)[1];
    }

    /** A SPLIT's second operand: the way kept for later. */
    int second(final int step) {
        return steps.get(step)[2];
    }

    String symbol(final int index) {
        return symbols.get(index);
    }

    /** How many loops the program holds, each with a mark of its own. */
    int marks() {
        return marks;
    }

    private int add(final int operation, final int first, final int second) {
        if (steps.size() >= LIMIT) {
            throw new IllegalStateException("Row pattern is too large to match.");
        }
        steps.add(new int[] {operation, first, second});
        return steps.size() - 1;
    }

    private void compile(final RowPatternNode node) {
        switch (node.getKind()) {
            case SYMBOL:
                int index = symbols.indexOf(node.getSymbol());
                if (index < 0) {
                    symbols.add(node.getSymbol());
                    index = symbols.size() - 1;
                }
                add(SYMBOL, index, 0);
                return;
            case PARTITION_START:
                add(PARTITION_START, 0, 0);
                return;
            case PARTITION_END:
                add(PARTITION_END, 0, 0);
                return;
            case SEQUENCE:
                for (final RowPatternNode part : node.getParts()) {
                    compile(part);
                }
                return;
            case EXCLUSION:
                add(EXCLUDE, 0, 0);
                for (final RowPatternNode part : node.getParts()) {
                    compile(part);
                }
                add(INCLUDE, 0, 0);
                return;
            case ALTERNATION:
                compileAlternation(node.getParts());
                return;
            default:
                compileQuantified(node);
        }
    }

    private void compileAlternation(final List<RowPatternNode> parts) {
        final List<Integer> exits = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (i < parts.size() - 1) {
                final int split = add(SPLIT, 0, 0);
                steps.get(split)[1] = steps.size();
                compile(parts.get(i));
                exits.add(Integer.valueOf(add(JUMP, 0, 0)));
                steps.get(split)[2] = steps.size();
            } else {
                compile(parts.get(i));
            }
        }
        for (final Integer exit : exits) {
            steps.get(exit.intValue())[1] = steps.size();
        }
    }

    private void compileQuantified(final RowPatternNode node) {
        final RowPatternNode part = node.getParts().get(0);
        for (int i = 0; i < node.getLeast(); i++) {
            compile(part);
        }
        if (node.getGreatest() == RowPatternNode.UNBOUNDED) {
            // loop: SPLIT body, exit — or exit, body when reluctant; the body ends by jumping back.
            final int split = add(SPLIT, 0, 0);
            final int body = steps.size();
            final int mark = marks++;
            add(MARK, mark, 0);
            compile(part);
            add(ADVANCED, mark, 0);
            add(JUMP, split, 0);
            aim(split, body, steps.size(), node.isReluctant());
            return;
        }
        // The optional rest, each inside the one before: (part (part (part)?)?)?
        final List<Integer> splits = new ArrayList<>();
        final List<Integer> bodies = new ArrayList<>();
        for (int i = node.getLeast(); i < node.getGreatest(); i++) {
            splits.add(Integer.valueOf(add(SPLIT, 0, 0)));
            bodies.add(Integer.valueOf(steps.size()));
            compile(part);
        }
        for (int i = 0; i < splits.size(); i++) {
            aim(splits.get(i).intValue(), bodies.get(i).intValue(), steps.size(), node.isReluctant());
        }
    }

    private void aim(final int split, final int body, final int exit, final boolean reluctant) {
        steps.get(split)[1] = reluctant ? exit : body;
        steps.get(split)[2] = reluctant ? body : exit;
    }
}
