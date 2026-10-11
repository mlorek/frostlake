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

/** A construct {@link BlockReadOn} has read the opening of and not yet the END. */
final class OpenConstruct {

    private final BlockConstruct kind;
    private final int closingWord;
    private boolean handlers;

    /**
     * @param kind        the construct
     * @param closingWord the token type written after its END, or -1 for a block, which has none
     */
    OpenConstruct(final BlockConstruct kind, final int closingWord) {
        this.kind = kind;
        this.closingWord = closingWord;
    }

    BlockConstruct kind() {
        return kind;
    }

    /** The token type written after the construct's END: IF, CASE, FOR, WHILE, LOOP or REPEAT; -1 for a block. */
    int closingWord() {
        return closingWord;
    }

    /** Whether the block's EXCEPTION section has been reached, whose WHEN … THEN open its handlers. */
    boolean inHandlers() {
        return handlers;
    }

    void enterHandlers() {
        handlers = true;
    }
}
