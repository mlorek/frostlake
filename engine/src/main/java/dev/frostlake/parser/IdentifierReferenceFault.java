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
import org.antlr.v4.runtime.RecognitionException;

/**
 * A syntax error the parser raises itself for an IDENTIFIER() reference that is not whole: its parentheses parse
 * as a group, and the parser names the token inside them that live refuses. The listener reports it where it
 * stands; a line for the parenthesis closing a comma's list ends the statement's report.
 */
public class IdentifierReferenceFault extends RecognitionException {

    private static final long serialVersionUID = 1L;

    private final boolean endsTheReport;

    /**
     * @param parser        the parser that found the reference
     * @param reference     the reference's parse node
     * @param endsTheReport whether nothing after this line is reported for the statement
     */
    public IdentifierReferenceFault(final Parser parser, final ParserRuleContext reference, final boolean endsTheReport) {
        super(parser, parser.getInputStream(), reference);
        this.endsTheReport = endsTheReport;
    }

    /** Whether nothing after this line is reported for the statement. */
    public boolean endsTheReport() {
        return endsTheReport;
    }
}
