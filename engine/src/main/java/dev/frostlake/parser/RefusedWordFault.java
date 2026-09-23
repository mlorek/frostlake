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

/**
 * A syntax error the parser raises itself at a word live refuses where it stands, once the parse has read it: a
 * keyword written as a class name, TRUE or FALSE in a signature, the type of a named argument in an overload's type
 * list, and the tokens live names after those. The listener
 * reports it as it reports a reference fault: where the parser placed it, with the last line ending the statement's
 * report.
 */
public final class RefusedWordFault extends IdentifierReferenceFault {

    private static final long serialVersionUID = 1L;

    /**
     * @param parser        the parser that read the word
     * @param node          the parse node holding the word
     * @param endsTheReport whether nothing after this line is reported for the statement
     */
    public RefusedWordFault(final Parser parser, final ParserRuleContext node, final boolean endsTheReport) {
        super(parser, node, endsTheReport);
    }
}
