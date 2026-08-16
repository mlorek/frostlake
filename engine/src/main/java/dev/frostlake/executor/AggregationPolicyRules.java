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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** What an AGGREGATION POLICY allows: which aggregates leak a row, and what a body's verdict says. */
public final class AggregationPolicyRules {

    /**
     * The aggregates an aggregation policy refuses, each measured on a live account: they can all
     * hand back one row's own value. SUM, AVG, COUNT, STDDEV and APPROX_COUNT_DISTINCT are allowed —
     * note that STDDEV is and VARIANCE is not, which is why this list is measured, not reasoned.
     */
    private static final Set<String> LEAKING = new HashSet<>(Arrays.asList(
        "MIN", "MAX", "MEDIAN", "MODE", "ANY_VALUE", "VARIANCE", "VAR_SAMP", "VAR_POP",
        "LISTAGG", "ARRAY_AGG", "PERCENTILE_CONT", "PERCENTILE_DISC"));

    private AggregationPolicyRules() {
    }

    /**
     * The FIRST select item that uses a forbidden aggregate, spelled as it was written — that text is
     * what the refusal echoes — or null when none does.
     */
    public static String leakingAggregate(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final String found = leakingIn(item);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String leakingIn(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call =
                (FrostlakeParser.FunctionCallExprContext) tree;
            if (LEAKING.contains(call.functionName().getText().toUpperCase(Locale.ROOT))) {
                return ParseTreeText.getOriginalText(call);
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            final String found = leakingIn(tree.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The minimum group size a body's verdict carries: AGGREGATION_CONSTRAINT's, or 0 for none. */
    public static int minGroupSize(final String verdict) {
        if (verdict == null) {
            return 0;
        }
        final int key = verdict.indexOf("\"min_group_size\":");
        if (key < 0) {
            return 0;
        }
        final StringBuilder digits = new StringBuilder();
        for (int i = key + "\"min_group_size\":".length(); i < verdict.length(); i++) {
            final char c = verdict.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }
}
