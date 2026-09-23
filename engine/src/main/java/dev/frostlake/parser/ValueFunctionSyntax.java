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

package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The syntax the value and navigation functions have of their own, written with their names unquoted: a null
 * treatment after a call's closing parenthesis belongs to FIRST_VALUE, LAST_VALUE, LAG, LEAD and NTH_VALUE alone,
 * with their arguments positional or named, and LAG, LEAD and NTH_VALUE take no quantifier before their arguments
 * (all live-verified):
 *
 * <pre>
 *   MEDIAN(n) IGNORE NULLS OVER ()           syntax error … unexpected 'IGNORE'.
 *   ROW_NUMBER() RESPECT NULLS OVER (…)      syntax error … unexpected 'RESPECT'.
 *   SUM(x =&gt; n) IGNORE NULLS OVER ()        syntax error … unexpected 'IGNORE'.
 *   "LAG"(n) IGNORE NULLS OVER (…)           syntax error … unexpected 'IGNORE'.
 *   LAG(n) IGNORE NULLS OVER (ORDER BY n)    answered
 *   LAG(ALL n) OVER (ORDER BY n)             syntax error … unexpected 'ALL'., then … unexpected '('.
 *   NTH_VALUE(DISTINCT n, 1) OVER (…)        syntax error … unexpected 'DISTINCT'., then … unexpected '('.
 * </pre>
 *
 * <p>The quantifier's report names the quantifier and then the call's own opening parenthesis, and nothing more of
 * the statement. A quoted name is an ordinary call: {@code "LAG"(ALL n)} is judged for its quantifier as any
 * window function is, and a null treatment after it is the syntax error it is after any other call.
 *
 * <p>The grammar reads the treatment after any call and the quantifier before any call's arguments, so that each
 * refusal lands on its own word; the earliest one written is reported. A FIRST_VALUE or LAST_VALUE carrying a
 * treatment both inside and after its parentheses is refused as the duplicate it is.
 */
public final class ValueFunctionSyntax {

    private static final Set<String> VALUE_FUNCTIONS =
        Set.of("FIRST_VALUE", "LAST_VALUE", "LAG", "LEAD", "NTH_VALUE");

    private static final Set<String> NAVIGATION_FUNCTIONS = Set.of("LAG", "LEAD", "NTH_VALUE");

    private ValueFunctionSyntax() {
    }

    /**
     * Refuse the first fault written, in the order written — a null treatment following a call which takes none,
     * or a quantifier before a navigation function's arguments — and then a treatment written twice.
     *
     * @param script the parsed script
     * @param sql    the script's source text
     */
    public static void requireValueFunctionForms(final ParseTree script, final String sql) {
        if (script == null) {
            return;
        }
        Token first = null;
        List<String> lines = null;
        boolean duplicated = false;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            final WrittenCallParts call = WrittenCallParts.of(node);
            // A name spelled by IDENTIFIER() is resolved later, and left to what resolves it.
            if (call != null && call.name != null && call.name.identifierArgument() == null) {
                final String name = valueFunctionName(call);
                final List<FrostlakeParser.NullHandlingContext> after = new ArrayList<>();
                final List<FrostlakeParser.NullHandlingContext> inside = new ArrayList<>();
                sortTreatments(call, after, inside);
                if (!after.isEmpty() && name == null) {
                    final Token refused = after.get(0).getStart();
                    if (first == null || refused.getTokenIndex() < first.getTokenIndex()) {
                        first = refused;
                        lines = new ArrayList<>();
                        lines.add(SyntaxErrorListener.sentence(refused));
                    }
                } else if (!after.isEmpty() && !inside.isEmpty()) {
                    duplicated = true;
                }
                if (call.quantifier != null && name != null && NAVIGATION_FUNCTIONS.contains(name)) {
                    final Token refused = call.quantifier.getSymbol();
                    if (first == null || refused.getTokenIndex() < first.getTokenIndex()) {
                        first = refused;
                        lines = new ArrayList<>();
                        lines.add(SyntaxErrorListener.sentence(refused));
                        lines.add(SyntaxErrorListener.sentence(call.open.getSymbol()));
                    }
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        if (first != null) {
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
        if (duplicated) {
            throw new RuntimeException("SQL compilation error: duplicated use of null handling option");
        }
    }

    /** The call's treatments, sorted into those after its closing parenthesis and those inside it. */
    private static void sortTreatments(final WrittenCallParts call, final List<FrostlakeParser.NullHandlingContext> after,
                                       final List<FrostlakeParser.NullHandlingContext> inside) {
        if (call.treatments == null || call.close == null) {
            return;
        }
        for (final FrostlakeParser.NullHandlingContext treatment : call.treatments) {
            if (treatment.getStart() == null) {
                continue;
            }
            if (treatment.getStart().getTokenIndex() > call.close.getSymbol().getTokenIndex()) {
                after.add(treatment);
            } else {
                inside.add(treatment);
            }
        }
    }

    /**
     * The value function the call names with its own syntax — one unquoted, unqualified name among FIRST_VALUE,
     * LAST_VALUE, LAG, LEAD and NTH_VALUE — or null for any other call.
     */
    private static String valueFunctionName(final WrittenCallParts call) {
        final List<FrostlakeParser.IdentifierContext> parts = call.name.identifier();
        if (parts == null || parts.size() != 1
                || parts.get(0).getStart().getType() == FrostlakeParser.QUOTED_IDENTIFIER) {
            return null;
        }
        final String canonical = SqlIdentifiers.canonicalText(parts.get(0).getText());
        return VALUE_FUNCTIONS.contains(canonical) ? canonical : null;
    }
}
