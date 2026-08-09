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

package dev.frostlake.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The separator ladder each {@code format} of SPLIT_TEXT_RECURSIVE_CHARACTER splits on, most
 * structural first. The chunker tries each in turn and only falls through to the next when a piece is
 * still too big, so a paragraph break is preferred to a line break, a line break to a space, and a
 * bare character boundary is the last resort.
 *
 * <p>The empty string closes every ladder: it means "cut anywhere", which is what makes the chunk-size
 * bound a guarantee rather than a preference.
 */
final class ChunkSeparators {

    private static final List<String> PLAIN = Arrays.asList("\n\n", "\n", " ", "");
    private static final List<String> MARKDOWN = Arrays.asList(
        "\n###### ", "\n##### ", "\n#### ", "\n### ", "\n## ", "\n# ",
        "\n\n", "\n", " ", "");
    private static final List<String> HTML = Arrays.asList(
        "</div>", "</p>", "</section>", "</article>", "<br>", "<br/>", "\n\n", "\n", " ", "");
    private static final List<String> LATEX = Arrays.asList(
        "\n\\chapter{", "\n\\section{", "\n\\subsection{", "\n\\subsubsection{",
        "\n\\begin{", "\n\\end{", "\n\n", "\n", " ", "");

    private ChunkSeparators() {
    }

    /** The ladder for a format name, case-insensitively; null when the name is not one we model. */
    static List<String> forFormat(final String format) {
        final String name = format == null ? "" : format.trim().toLowerCase();
        if ("none".equals(name)) {
            return new ArrayList<>(PLAIN);
        }
        if ("markdown".equals(name)) {
            return new ArrayList<>(MARKDOWN);
        }
        if ("html".equals(name)) {
            return new ArrayList<>(HTML);
        }
        if ("latex".equals(name)) {
            return new ArrayList<>(LATEX);
        }
        return null;
    }
}
