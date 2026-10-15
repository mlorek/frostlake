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

/**
 * A syntax error the parser raises itself for a type's parameter list its type does not take (see
 * {@link TypeParameterLines}). The lines are placed by the parser, one fault each, so the listener reports them as
 * they come and stacks nothing of its own after them.
 */
public final class TypeParameterFault extends IdentifierReferenceFault {

    private static final long serialVersionUID = 1L;

    private final boolean repeated;

    /**
     * @param parser        the parser that read the list
     * @param list          the list's parse node
     * @param named         the token the line names
     * @param endsTheReport whether nothing after this line is reported for the statement
     * @param repeated      whether the line names the token of the line before it once more, as the account does
     *                      for the first token inside an ARRAY's or an OBJECT's parentheses
     */
    public TypeParameterFault(final Parser parser, final ParserRuleContext list, final Token named,
                              final boolean endsTheReport, final boolean repeated) {
        super(parser, list, endsTheReport);
        setOffendingToken(named);
        this.repeated = repeated;
    }

    /** Whether this line repeats the one before it on purpose, which the listener must not fold into it. */
    public boolean repeatsTheLineBefore() {
        return repeated;
    }
}
