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
 * A syntax error the parser raises itself at a token after which the account reports nothing more — of the
 * statement or of any statement after it: the parenthesis after a stage's name in a DROP or a DESCRIBE.
 */
public final class InputEndingFault extends IdentifierReferenceFault {

    private static final long serialVersionUID = 1L;

    /**
     * @param parser the parser that read the token
     * @param node   the parse node the token stands in
     */
    public InputEndingFault(final Parser parser, final ParserRuleContext node) {
        super(parser, node, true);
    }
}
