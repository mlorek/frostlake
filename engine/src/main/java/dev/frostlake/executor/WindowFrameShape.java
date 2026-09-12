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

import dev.frostlake.parser.FrostlakeParser;
import java.util.List;

/**
 * A window FRAME's shape, in the vocabulary live's refusals use. Two of them name a frame kind, and
 * which one a frame gets is decided by HOW MANY of its two edges are unbounded — not by WHICH edge,
 * and not by ROWS against RANGE:
 *
 * <pre>
 *   BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING   both   the whole partition
 *   BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW           one    cumulative
 *   BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING           one    cumulative
 *   BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING           one    cumulative
 *   BETWEEN 1 PRECEDING AND UNBOUNDED FOLLOWING           one    cumulative
 *   UNBOUNDED PRECEDING                                   one    cumulative
 *   BETWEEN 1 PRECEDING AND CURRENT ROW                   none   sliding
 *   1 PRECEDING                                           none   sliding
 * </pre>
 *
 * <p>★ A ONE-SIDED FRAME'S MISSING EDGE IS CURRENT ROW, which is bounded. That is what separates the
 * last two lines from each other, and it is why the count — rather than "does it start at the
 * partition's edge" — is the rule: a frame with one edge pinned to the partition grows or shrinks
 * monotonically as the rows advance, and one with neither keeps a fixed span and slides.
 *
 * <p>Live-verified over every line above, for each of the four aggregates that carry the refusal.
 */
final class WindowFrameShape {

    private WindowFrameShape() {
    }

    /** Whether the frame covers the partition end to end, which is no restriction at all. */
    static boolean spansWholePartition(final FrostlakeParser.WindowFrameContext frame) {
        return unboundedEdges(frame) == 2;
    }

    /** Whether the frame is written with RANGE rather than ROWS. */
    static boolean isRange(final FrostlakeParser.WindowFrameContext frame) {
        return frame != null && frame.RANGE() != null;
    }

    /**
     * The word live puts in front of "window frame unsupported for function" for this frame, or for
     * the implicit frame an ORDER BY brings when no frame is written.
     *
     * @param frame the frame, or null for the implicit one
     * @return "Cumulative" or "Sliding"
     */
    static String kindWord(final FrostlakeParser.WindowFrameContext frame) {
        return unboundedEdges(frame) == 0 ? "Sliding" : "Cumulative";
    }

    /**
     * How many of the frame's two edges are UNBOUNDED. A null frame is the implicit one an ORDER BY
     * carries, which runs from the partition's start to the current row — one unbounded edge.
     */
    private static int unboundedEdges(final FrostlakeParser.WindowFrameContext frame) {
        if (frame == null) {
            return 1;
        }
        final List<FrostlakeParser.FrameBoundContext> bounds = frame.frameBound();
        int unbounded = 0;
        for (final FrostlakeParser.FrameBoundContext bound : bounds) {
            if (bound.UNBOUNDED() != null) {
                unbounded++;
            }
        }
        // The missing edge of a one-sided frame is CURRENT ROW, and contributes nothing.
        return unbounded;
    }
}
