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

import java.util.List;
import org.antlr.v4.runtime.Token;

/** The lines a type's parameter list is refused with: the tokens named, in order, and whether they end the report. */
final class TypeParameterRefusal {

    private final List<Token> lines;
    private final boolean endsTheReport;

    TypeParameterRefusal(final List<Token> lines, final boolean endsTheReport) {
        this.lines = lines;
        this.endsTheReport = endsTheReport;
    }

    /** How many lines the refusal reports. */
    int size() {
        return lines.size();
    }

    /** The token the line at {@code index} names, in the order the lines are reported. */
    Token line(final int index) {
        return lines.get(index);
    }

    /** Whether nothing after the last line is reported for the statement. */
    boolean endsTheReport() {
        return endsTheReport;
    }
}
