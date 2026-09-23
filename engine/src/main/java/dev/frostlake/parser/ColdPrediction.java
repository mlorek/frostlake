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
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;

/**
 * A parse whose prediction cache is its own, so where it reports a fault does not depend on what the
 * JVM parsed before it.
 *
 * <p>ANTLR shares one DFA array and one {@link PredictionContextCache} across every parser instance the
 * generated class makes. That sharing is what makes parsing fast, and for a statement that PARSES it
 * changes nothing — the tree is the same either way. For a statement that does NOT parse it decides
 * which of several viable failure points the parse settles on, so the same refused statement reported
 * {@code unexpected 'FROM'} at position 14 in a fresh JVM and {@code unexpected ','} at position 8 once
 * other statements had warmed the cache. The account answers the first of those, and a refusal must be
 * a function of the statement alone in any case.
 *
 * <p>So a refused statement is parsed a SECOND time, through here, and its faults are read from that
 * parse. The cost falls only on statements that were going to be refused anyway; everything that
 * compiles keeps the shared cache.
 */
public final class ColdPrediction {

    private ColdPrediction() {
    }

    /**
     * Gives a parser a prediction cache of its own, discarding whatever the shared one holds.
     *
     * @param parser the parser to re-arm, before it is asked for a tree
     */
    public static void arm(final Parser parser) {
        final ATN atn = parser.getATN();
        final DFA[] decisions = new DFA[atn.getNumberOfDecisions()];
        for (int i = 0; i < decisions.length; i++) {
            decisions[i] = new DFA(atn.getDecisionState(i), i);
        }
        parser.setInterpreter(new ParserATNSimulator(parser, atn, decisions, new PredictionContextCache()));
    }
}
