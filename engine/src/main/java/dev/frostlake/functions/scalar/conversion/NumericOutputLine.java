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

package dev.frostlake.functions.scalar.conversion;

import java.util.ArrayList;
import java.util.List;

/**
 * A number being printed under an output model: one cell per element, in model order, each remembering
 * whether it is a LITERAL the model wrote or text a numeric element produced. Fill mode's compaction
 * removes the spaces numeric elements produce and keeps a literal's.
 */
final class NumericOutputLine {

    private final List<String> cells = new ArrayList<>();
    private final List<Boolean> literals = new ArrayList<>();

    /** Appends the text a numeric element produced. */
    void add(final String text) {
        cells.add(text);
        literals.add(Boolean.FALSE);
    }

    /** Appends a literal the model wrote. */
    void addLiteral(final String text) {
        cells.add(text);
        literals.add(Boolean.TRUE);
    }

    /** Inserts the text a numeric element produced before the cell at {@code index}. */
    void insert(final int index, final String text) {
        cells.add(index, text);
        literals.add(index, Boolean.FALSE);
    }

    /** How many cells the line holds. */
    int size() {
        return cells.size();
    }

    /**
     * The line as text.
     *
     * @param compact whether fill mode removes the spaces numeric elements produced
     * @return the text
     */
    String text(final boolean compact) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            out.append(compact && !literals.get(i).booleanValue() ? cells.get(i).replace(" ", "") : cells.get(i));
        }
        return out.toString();
    }
}
