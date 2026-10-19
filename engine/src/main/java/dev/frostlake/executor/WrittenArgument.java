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

package dev.frostlake.executor;

import org.antlr.v4.runtime.ParserRuleContext;

/** One argument of a table-function call as the statement writes it: by position, or under a name. */
final class WrittenArgument {

    private final String name;
    private final String text;
    private final ParserRuleContext node;

    /**
     * @param name the name the argument is given under, canonical, or null for a positional one
     * @param text the argument's value as written, a query in its parentheses
     * @param node the argument in the parse tree
     */
    WrittenArgument(final String name, final String text, final ParserRuleContext node) {
        this.name = name;
        this.text = text;
        this.node = node;
    }

    String getName() {
        return name;
    }

    String getText() {
        return text;
    }

    ParserRuleContext getNode() {
        return node;
    }
}
