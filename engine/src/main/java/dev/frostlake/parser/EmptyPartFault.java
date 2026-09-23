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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * One name with an empty middle part refused where it stands: the tokens its lines name, in order, its
 * shape, and the name itself. A token that is null or the end of input is reported as {@code <EOF>}.
 */
public final class EmptyPartFault {

    private final EmptyPartShape shape;
    private final List<Token> refused;
    private final ParserRuleContext name;
    private final Token castClose;
    private final boolean closing;

    /**
     * @param shape     the shape of the refused name
     * @param refused   the tokens its lines name, at least one
     * @param name      the name's parse tree
     * @param castClose the closing parenthesis of the CAST around it, for {@link EmptyPartShape#COLUMN_IN_CAST}
     * @param closing   whether live reports nothing after this refusal
     */
    public EmptyPartFault(final EmptyPartShape shape, final List<Token> refused, final ParserRuleContext name,
                          final Token castClose, final boolean closing) {
        this.shape = shape;
        this.refused = new ArrayList<>(refused);
        this.name = name;
        this.castClose = castClose;
        this.closing = closing;
    }

    public EmptyPartShape shape() {
        return shape;
    }

    public List<Token> refused() {
        return Collections.unmodifiableList(refused);
    }

    /** The first token the refusal names. */
    public Token first() {
        return refused.get(0);
    }

    public ParserRuleContext name() {
        return name;
    }

    public Token castClose() {
        return castClose;
    }

    /** Whether live reports nothing more once this refusal is reported. */
    public boolean closing() {
        return closing;
    }
}
