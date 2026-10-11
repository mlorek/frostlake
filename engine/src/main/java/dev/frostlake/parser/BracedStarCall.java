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

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

/**
 * The braced star written where a value stands — {@code TO_JSON({*})}, {@code {t.* EXCLUDE (a)}:ID} — which is the
 * row as an OBJECT: OBJECT_CONSTRUCT over the star's columns. The parse reads it as the star call it stands for, and
 * this gives that call the name it was not written with, so everything that reads a star call's name finds one. The
 * name takes no place in the text: it stands where the opening brace does and is empty there, so the call's own
 * source text is still the braces and what they hold.
 */
final class BracedStarCall {

    private static final String NAME = "OBJECT_CONSTRUCT";

    private BracedStarCall() {
    }

    /**
     * Names the star call a braced star was read as.
     *
     * @param call the call's parse node, opening at the brace
     */
    static void name(final ParserRuleContext call) {
        final Token brace = call.getStart();
        final CommonToken word = new CommonToken(brace);
        word.setType(FrostlakeParser.IDENTIFIER);
        word.setText(NAME);
        word.setStopIndex(brace.getStartIndex() - 1);
        final FrostlakeParser.FunctionNameContext name = new FrostlakeParser.FunctionNameContext(call, call.invokingState);
        final FrostlakeParser.IdentifierContext identifier =
            new FrostlakeParser.IdentifierContext(name, name.invokingState);
        identifier.addChild(new TerminalNodeImpl(word));
        identifier.start = word;
        identifier.stop = word;
        name.addChild(identifier);
        name.start = word;
        name.stop = word;
        call.children.add(0, name);
    }
}
