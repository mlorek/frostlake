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
 * Cosine similarity between two embeddings — how the AI pack scores one text against another, whether
 * that is AI_SIMILARITY comparing two arguments or a search preview ranking rows against a query.
 *
 * <p>Vectors of different widths are compared over their common prefix rather than refused: two models,
 * or one model across a version, can disagree on width, and a hard failure there would surface as a
 * broken query rather than as the configuration problem it is.
 */
final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /** The similarity, from -1 to 1; zero when either vector is empty or all-zero. */
    static double of(final List<Double> left, final List<Double> right) {
        final int width = Math.min(left.size(), right.size());
        double dot = 0.0;
        double normLeft = 0.0;
        double normRight = 0.0;
        for (int i = 0; i < width; i++) {
            final double x = left.get(i).doubleValue();
            final double y = right.get(i).doubleValue();
            dot += x * y;
            normLeft += x * x;
            normRight += y * y;
        }
        if (normLeft == 0.0 || normRight == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }
}
