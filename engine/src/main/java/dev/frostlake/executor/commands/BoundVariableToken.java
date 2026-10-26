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
package dev.frostlake.executor.commands;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * The literal a session variable is read as for the property it is the value of (see {@link PropertyVariables}),
 * standing where the variable was written, so a property that refuses a written literal where it stands can still
 * take the variable's value.
 */
public final class BoundVariableToken extends CommonToken {

    private static final long serialVersionUID = 1L;

    /**
     * @param written the variable as written, whose place the literal keeps
     * @param type    the literal's token type
     * @param text    the literal's text
     */
    BoundVariableToken(final Token written, final int type, final String text) {
        super(written);
        setType(type);
        setText(text);
    }
}
