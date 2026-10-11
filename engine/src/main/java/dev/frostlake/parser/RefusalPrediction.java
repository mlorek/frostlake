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

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Pair;

/**
 * The prediction of a parse that is read for its faults. Where no alternative reads the text, ANTLR falls back on
 * an alternative that could have ended its rule had its predicate held, enters it, and reports the predicate's
 * failure at the token the alternative starts with — so a fault inside {@code CASE WHEN 1 = 1 THEN x y END} was
 * reported at the CASE, the word a name's alternative may not start with. The account names the token where the
 * reading stopped (live-verified), and that is what a prediction with no such fallback reports: no alternative is
 * viable at {@code y}.
 *
 * <p>The same holds at the comma after a select item, where ANTLR would otherwise end the list and refuse the
 * comma: an item is due after it, and the fault is where that item stops being one — the FROM of {@code SELECT a,
 * UPPER(b FROM t}.
 */
final class RefusalPrediction extends ParserATNSimulator {

    RefusalPrediction(final Parser parser, final ATN atn, final DFA[] decisions, final PredictionContextCache cache) {
        super(parser, atn, decisions, cache);
    }

    /** Whether the comma the decision starts at follows an item: a comma straight after the list's opening word does not. */
    private boolean followsAnItem() {
        for (int i = _startIndex - 1; i >= 0; i--) {
            final Token before = _input.get(i);
            if (before.getChannel() == Token.DEFAULT_CHANNEL) {
                final int type = before.getType();
                return type != FrostlakeParser.SELECT && type != FrostlakeParser.COMMA
                    && type != FrostlakeParser.DISTINCT && type != FrostlakeParser.ALL;
            }
        }
        return false;
    }

    @Override
    protected int getSynValidOrSemInvalidAltThatFinishedDecisionEntryRule(final ATNConfigSet configs,
                                                                         final ParserRuleContext outerContext) {
        if (_dfa != null && _dfa.atnStartState.ruleIndex == FrostlakeParser.RULE_selectList
                && _input.get(_startIndex).getType() == FrostlakeParser.COMMA && followsAnItem()) {
            // After a select item's comma another item is due: the list does not end there, whatever the item
            // after it fails on.
            return ATN.INVALID_ALT_NUMBER;
        }
        final Pair<ATNConfigSet, ATNConfigSet> split = splitAccordingToSemanticValidity(configs, outerContext);
        return getAltThatFinishedDecisionEntryRule(split.a);
    }
}
