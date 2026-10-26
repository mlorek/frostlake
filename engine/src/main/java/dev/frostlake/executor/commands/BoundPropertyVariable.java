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

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

/**
 * A session variable a statement gives a property as its value, read for the statement as the literal of the value
 * it holds (see {@link PropertyVariables}), and given back its written token once the statement has run.
 */
public final class BoundPropertyVariable {

    private final TerminalNodeImpl node;
    private final Token written;

    /**
     * @param node    the variable's place in the statement
     * @param written the variable as written
     */
    BoundPropertyVariable(final TerminalNodeImpl node, final Token written) {
        this.node = node;
        this.written = written;
    }

    /** Give the place back the variable as written. */
    void restore() {
        node.symbol = written;
    }
}
