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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the pattern of a MATCH_RECOGNIZE clause off its parse tree. A symbol is named without regard to letter
 * case, quoted or not: {@code "lo"} and {@code LO} are one symbol, and CLASSIFIER answers {@code LO} for both
 * (live-verified).
 */
public final class RowPatternReader {

    private RowPatternReader() {
    }

    /**
     * @param pattern the pattern as parsed
     * @return its tree
     */
    public static RowPatternNode read(final FrostlakeParser.RowPatternContext pattern) {
        final List<RowPatternNode> alternatives = new ArrayList<>();
        for (final FrostlakeParser.RowPatternSequenceContext sequence : pattern.rowPatternSequence()) {
            final List<RowPatternNode> terms = new ArrayList<>();
            for (final FrostlakeParser.RowPatternTermContext term : sequence.rowPatternTerm()) {
                terms.add(term(term));
            }
            alternatives.add(RowPatternNode.of(RowPatternKind.SEQUENCE, terms));
        }
        return RowPatternNode.of(RowPatternKind.ALTERNATION, alternatives);
    }

    /**
     * A symbol's name as the pattern, the definitions and the measures agree on it.
     *
     * @param written the name as parsed
     * @return the name, upper-cased
     */
    public static String symbolName(final FrostlakeParser.IdentifierContext written) {
        return ParseTreeText.getIdentifier(written).toUpperCase(Locale.ROOT);
    }

    private static RowPatternNode term(final FrostlakeParser.RowPatternTermContext term) {
        final RowPatternNode primary = primary(term.rowPatternPrimary());
        final FrostlakeParser.RowPatternQuantifierContext quantifier = term.rowPatternQuantifier();
        if (quantifier == null) {
            return primary;
        }
        int least;
        int greatest;
        boolean reluctant = quantifier.QUESTION().size() > 0;
        if (quantifier.STAR() != null) {
            least = 0;
            greatest = RowPatternNode.UNBOUNDED;
        } else if (quantifier.PLUS() != null) {
            least = 1;
            greatest = RowPatternNode.UNBOUNDED;
        } else if (quantifier.LBRACE() == null) {
            least = 0;
            greatest = 1;
            reluctant = quantifier.QUESTION().size() > 1;
        } else {
            // {n}, {n,}, {,m} or {n,m}: a number stands before the comma, after it, or alone.
            least = 0;
            greatest = RowPatternNode.UNBOUNDED;
            boolean pastComma = false;
            for (int i = 0; i < quantifier.getChildCount(); i++) {
                final ParseTree child = quantifier.getChild(i);
                if (!(child instanceof TerminalNode)) {
                    continue;
                }
                final int type = ((TerminalNode) child).getSymbol().getType();
                if (type == FrostlakeParser.COMMA) {
                    pastComma = true;
                } else if (type == FrostlakeParser.INTEGER_LITERAL) {
                    final int count = count(child.getText());
                    if (pastComma) {
                        greatest = count;
                    } else {
                        least = count;
                    }
                }
            }
            if (quantifier.COMMA() == null) {
                // {n} is exactly n; {0} repeats without a bound, as the account reads it (live-verified).
                greatest = least == 0 ? RowPatternNode.UNBOUNDED : least;
            } else if (greatest != RowPatternNode.UNBOUNDED && greatest < least) {
                // A greatest below the least is taken as both (live-verified: a{2,1} matches one row at a time).
                least = greatest;
            }
        }
        return RowPatternNode.quantified(primary, least, greatest, reluctant);
    }

    private static int count(final String written) {
        try {
            return Integer.parseInt(written);
        } catch (final NumberFormatException tooLong) {
            return Integer.MAX_VALUE;
        }
    }

    private static RowPatternNode primary(final FrostlakeParser.RowPatternPrimaryContext primary) {
        if (primary.CARET() != null) {
            return RowPatternNode.anchor(RowPatternKind.PARTITION_START);
        }
        if (primary.DOLLAR() != null) {
            return RowPatternNode.anchor(RowPatternKind.PARTITION_END);
        }
        if (primary.LBRACE() != null) {
            final List<RowPatternNode> excluded = new ArrayList<>();
            excluded.add(read(primary.rowPattern(0)));
            return RowPatternNode.of(RowPatternKind.EXCLUSION, excluded);
        }
        if (primary.LPAREN() != null && primary.identifier() != null) {
            final List<RowPatternNode> parts = new ArrayList<>();
            for (final FrostlakeParser.RowPatternContext part : primary.rowPattern()) {
                parts.add(read(part));
            }
            return RowPatternNode.permutation(parts);
        }
        if (primary.LPAREN() != null) {
            return read(primary.rowPattern(0));
        }
        return RowPatternNode.symbol(symbolName(primary.identifier()));
    }
}
