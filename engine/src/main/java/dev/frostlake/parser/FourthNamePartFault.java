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
 * A syntax error the parser raises itself at the dot that opens a name's fourth part where a table function is
 * called, and at the tokens live's recovery names after it. The listener reports it where the parser placed it, as
 * it reports a reference fault: a JOIN it names stands for itself, not for a join missing its table.
 */
public final class FourthNamePartFault extends IdentifierReferenceFault {

    private static final long serialVersionUID = 1L;

    /**
     * @param parser        the parser that read the name
     * @param node          the parse node holding the call
     * @param endsTheReport whether nothing after this line is reported for the statement
     */
    public FourthNamePartFault(final Parser parser, final ParserRuleContext node, final boolean endsTheReport) {
        super(parser, node, endsTheReport);
    }
}
