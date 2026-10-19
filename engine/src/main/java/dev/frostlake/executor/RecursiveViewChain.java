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

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * How a RECURSIVE view's body recurses, as the account reads it (live-verified). The body's operands are grouped
 * as any set operation groups them - a run of INTERSECT first, each run one element - and the elements are combined
 * from the left, so the top of the body is its trailing run of one operator: UNION ALL, a distinct UNION, or EXCEPT
 * and MINUS alike. That run is the recursion when it unions, the element just after its first operator reads the
 * view and no element before that one does: everything before it is the anchor, combined by its own operators, and
 * that element is the recursive term, a run of INTERSECT one term whose operands are intersected. Every element
 * after the recursive term takes no part, whether it reads the view or not. Any other body does not recurse on the
 * view, and its reference to the view reads the view as any query would: over a view not yet created, the missing
 * object it is.
 *
 * <pre>
 *   SELECT 1 UNION ALL SELECT n + 1 FROM v WHERE n &lt; 3 UNION ALL SELECT 100      recurses; 100 takes no part
 *   SELECT 5 EXCEPT SELECT 6 UNION ALL SELECT n + 1 FROM v WHERE n &lt; 7           recurses from 5 EXCEPT 6
 *   SELECT 1 UNION ALL SELECT n + 1 FROM v WHERE n &lt; 3 EXCEPT SELECT 2          an EXCEPT on top: none
 *   SELECT 1 UNION ALL SELECT n + 1 FROM v WHERE n &lt; 3 UNION SELECT 100         the top run's second element
 *                                                                           reads nothing: none
 *   SELECT 100 UNION ALL SELECT 1 UNION ALL SELECT n + 1 FROM v WHERE n &lt; 3     likewise: none
 *   SELECT n FROM v UNION ALL SELECT n + 1 FROM v WHERE n &lt; 3                   the anchor reads the view: none
 * </pre>
 */
public final class RecursiveViewChain {

    /** The first operand of the recursive term: every operand before it is the anchor. */
    private final int recursionAt;

    /** The recursive term's operands, by their places in the body. */
    private final List<Integer> term;

    private RecursiveViewChain(final int recursionAt, final List<Integer> term) {
        this.recursionAt = recursionAt;
        this.term = term;
    }

    /**
     * The way a body recurses on a view, or null where it does not.
     *
     * @param body the view's body
     * @param view the view's name, canonical
     * @return the chain, or null
     */
    public static RecursiveViewChain of(final FrostlakeParser.SelectStatementContext body, final String view) {
        final List<FrostlakeParser.SelectOperandContext> operands = body.selectOperand();
        final List<FrostlakeParser.SetOperatorContext> operators = body.setOperator();
        final List<List<Integer>> elements = new ArrayList<>();
        final List<FrostlakeParser.SetOperatorContext> between = new ArrayList<>();
        List<Integer> element = new ArrayList<>();
        element.add(Integer.valueOf(0));
        for (int i = 1; i < operands.size(); i++) {
            final FrostlakeParser.SetOperatorContext operator = operators.get(i - 1);
            if (operator.INTERSECT() != null) {
                element.add(Integer.valueOf(i));
            } else {
                elements.add(element);
                between.add(operator);
                element = new ArrayList<>();
                element.add(Integer.valueOf(i));
            }
        }
        elements.add(element);
        if (between.isEmpty()) {
            return null;
        }
        final String top = kindOf(between.get(between.size() - 1));
        int run = between.size() - 1;
        while (run > 0 && kindOf(between.get(run - 1)).equals(top)) {
            run--;
        }
        if (between.get(run).UNION() == null || !reads(elements.get(run + 1), operands, view)) {
            return null;
        }
        for (int e = 0; e <= run; e++) {
            if (reads(elements.get(e), operands, view)) {
                return null;
            }
        }
        return new RecursiveViewChain(elements.get(run + 1).get(0).intValue(), elements.get(run + 1));
    }

    /** The first operand of the recursive term: every operand before it is the anchor. */
    public int recursionAt() {
        return recursionAt;
    }

    /** The recursive term's operands, by their places in the body, combined by INTERSECT. */
    public List<Integer> term() {
        return term;
    }

    /** A set operator's kind on the top of a body: UNION ALL, UNION, UNION BY NAME, or EXCEPT and MINUS alike. */
    private static String kindOf(final FrostlakeParser.SetOperatorContext operator) {
        if (operator.UNION() != null) {
            return "UNION" + (operator.ALL() != null ? " ALL" : "") + (operator.BY() != null ? " BY NAME" : "");
        }
        return "EXCEPT" + (operator.ALL() != null ? " ALL" : "");
    }

    /** Whether any operand of an element reads the view. */
    private static boolean reads(final List<Integer> element,
                                 final List<FrostlakeParser.SelectOperandContext> operands, final String view) {
        for (final Integer place : element) {
            if (reads(operands.get(place.intValue()), view)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a subtree holds a FROM item whose one-part name is {@code name} - the parse tree's own definition of a
     * self-reference; a text search would misfire on any body that merely spells the name.
     *
     * @param node the subtree
     * @param name the canonical name
     * @return whether it reads the name
     */
    public static boolean reads(final ParseTree node, final String name) {
        if (node instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.TableSourceContext source = (FrostlakeParser.TableSourceContext) node;
            if (source.tableQualifiedName() != null) {
                final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
                if (parts.length == 1 && parts[0].equals(name)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (reads(node.getChild(i), name)) {
                return true;
            }
        }
        return false;
    }
}
