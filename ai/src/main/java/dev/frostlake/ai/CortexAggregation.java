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

import java.util.List;

/**
 * The one model call an aggregating Cortex function makes once its group is complete: the collected
 * values are numbered into a single prompt under the caller's instruction.
 *
 * <p>Numbering rather than simple concatenation keeps the rows distinguishable — a model asked to
 * find what several texts have in common answers differently when it can tell where one ends.
 */
final class CortexAggregation {

    private CortexAggregation() {
    }

    static String answer(final String instruction, final List<String> values) {
        final StringBuilder prompt = new StringBuilder(instruction);
        prompt.append("\n\n");
        for (int i = 0; i < values.size(); i++) {
            prompt.append(i + 1).append(". ").append(values.get(i)).append('\n');
        }
        return OllamaClient.generate(OllamaConfig.model(), prompt.toString(),
            Double.valueOf(0.0), null).trim();
    }
}
