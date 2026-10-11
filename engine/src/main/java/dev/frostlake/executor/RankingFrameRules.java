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
import java.util.Locale;
import java.util.Set;

/**
 * The frames the ranking and navigation functions take. They number, bucket or step through the whole
 * ordered partition, so the only frames they stand beside are the two that restate what they read
 * anyway — the whole partition, in ROWS or RANGE, and the frame an ORDER BY brings by itself,
 * {@code RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW}. Any other frame is refused when the
 * statement compiles, at the frame, in one of three sentences (all live-verified):
 *
 * <pre>
 *   ROWS, one edge unbounded                        Cumulative window frame unsupported for function NTILE
 *   ROWS, no edge unbounded                         Sliding window frame unsupported for function NTILE
 *   RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING   Cumulative window frame unsupported for …
 *   any other RANGE frame                           RANGE BETWEEN 1 PRECEDING AND CURRENT ROW window frame
 *                                                   unsupported for function NTILE
 * </pre>
 *
 * <p>The last echoes the frame in the account's own spelling: upper case, single blanks, a one-sided
 * frame's missing edge written out as CURRENT ROW, an offset as it was written.
 *
 * <p>RANK and DENSE_RANK are a family apart: they take every ROWS frame and refuse the same RANGE
 * frames, with one sentence of their own — {@code RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING} is
 * reported as the frame {@code RANGE BETWEEN CURRENT ROW AND CURRENT ROW}, at the OVER keyword.
 */
final class RankingFrameRules {

    /** The functions that take no frame but the two that restate the partition. */
    private static final Set<String> FRAMELESS = Set.of("ROW_NUMBER", "NTILE", "CUME_DIST", "PERCENT_RANK", "LAG",
        "LEAD", "CONDITIONAL_TRUE_EVENT", "CONDITIONAL_CHANGE_EVENT");

    /** The counter the account plans the other one as, and names when it refuses its frame. */
    private static final String CHANGE_EVENT = "CONDITIONAL_CHANGE_EVENT";

    private static final String TRUE_EVENT = "CONDITIONAL_TRUE_EVENT";

    /** The two that take any ROWS frame. */
    private static final Set<String> RANKS = Set.of("RANK", "DENSE_RANK");

    private static final String UNSUPPORTED = " window frame unsupported for function ";

    private static final String CURRENT_ROW = "CURRENT ROW";

    private static final String UNBOUNDED_PRECEDING = "UNBOUNDED PRECEDING";

    private static final String UNBOUNDED_FOLLOWING = "UNBOUNDED FOLLOWING";

    private static final String MIRRORED = "RANGE BETWEEN CURRENT ROW AND CURRENT ROW";

    private RankingFrameRules() {
    }

    /**
     * The sentence a function's frame is refused with.
     *
     * @param written the function, upper-cased
     * @param frame the frame written beside its ORDER BY
     * @return the refusal, or null when the function takes that frame or is none of these
     */
    static String refusal(final String written, final FrostlakeParser.WindowFrameContext frame) {
        final boolean ranks = RANKS.contains(written);
        if (frame == null || !ranks && !FRAMELESS.contains(written) || WindowFrameShape.spansWholePartition(frame)) {
            return null;
        }
        final String name = CHANGE_EVENT.equals(written) ? TRUE_EVENT : written;
        if (!WindowFrameShape.isRange(frame)) {
            return ranks ? null : WindowFrameShape.kindWord(frame) + UNSUPPORTED + name;
        }
        final String start = bound(frame.frameBound(0));
        final String end = frame.frameBound().size() > 1 ? bound(frame.frameBound(1)) : CURRENT_ROW;
        if (UNBOUNDED_PRECEDING.equals(start) && CURRENT_ROW.equals(end)) {
            return null;
        }
        if (CURRENT_ROW.equals(start) && UNBOUNDED_FOLLOWING.equals(end)) {
            return (ranks ? MIRRORED : "Cumulative") + UNSUPPORTED + name;
        }
        return "RANGE BETWEEN " + start + " AND " + end + UNSUPPORTED + name;
    }

    /**
     * Whether a refusal of this frame points at the OVER keyword rather than at the frame.
     *
     * @param name the function, upper-cased
     * @param frame the refused frame
     * @return true for the one frame RANK and DENSE_RANK report in another frame's words
     */
    static boolean pointsAtOver(final String name, final FrostlakeParser.WindowFrameContext frame) {
        final List<FrostlakeParser.FrameBoundContext> bounds = frame.frameBound();
        return RANKS.contains(name) && WindowFrameShape.isRange(frame) && bounds.size() > 1
            && CURRENT_ROW.equals(bound(bounds.get(0))) && UNBOUNDED_FOLLOWING.equals(bound(bounds.get(1)));
    }

    /** One edge in the account's spelling. */
    private static String bound(final FrostlakeParser.FrameBoundContext bound) {
        if (bound.CURRENT() != null) {
            return CURRENT_ROW;
        }
        if (bound.UNBOUNDED() != null) {
            return bound.PRECEDING() != null ? UNBOUNDED_PRECEDING : UNBOUNDED_FOLLOWING;
        }
        return ParseTreeText.getOriginalText(bound.expression()).toUpperCase(Locale.ROOT)
            + (bound.PRECEDING() != null ? " PRECEDING" : " FOLLOWING");
    }
}
