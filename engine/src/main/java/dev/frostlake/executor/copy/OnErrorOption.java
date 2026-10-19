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

package dev.frostlake.executor.copy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ON_ERROR as a COPY statement and a stage's COPY_OPTIONS take it, in one domain (live-verified): ABORT_STATEMENT,
 * CONTINUE, SKIP_FILE, SKIP_FILE_<i>n</i> with a whole number of records and {@code 'SKIP_FILE_p%'} with a share
 * of the records parsed, a number from 0 to 100 that may carry a fraction, each spelling in any case. Anything else
 * is outside the domain: a share past 100, a negative count, SKIP_FILE_ with no number, a bare count.
 *
 * <p>The option is a per-file error budget: how many rejected records ONE file may hold before the WHOLE file is
 * dropped, the records that parsed cleanly along with it — and, identically, the {@code error_limit} the per-file
 * result reports. Live-verified, every row of this table observed directly with a control run beside it:
 *
 * <ul>
 *   <li>{@code CONTINUE} — the record count itself, {@code max(rows_parsed, 1)}: a 4-record file reported 4, a
 *       2-record file 2, and a header-only file with nothing parsed reported 1 rather than 0. NOT unlimited, though
 *       it behaves so — a budget equal to the record count can only be reached by a file whose every record was
 *       rejected, which loads nothing and is LOAD_FAILED on that ground alone, so no clean record is ever dropped
 *       by it;</li>
 *   <li>{@code ABORT_STATEMENT} — 1, whatever the file holds: clean loads of a 0-, 1- and 2-record file all reported
 *       1. It can never be reached, the statement having already failed on the first bad record, so this value only
 *       ever shows up in the result;</li>
 *   <li>{@code SKIP_FILE} — 1: a single bad record drops the file (a 2-record file with 1 bad record loaded NOTHING
 *       and reported LOAD_FAILED, not the good record);</li>
 *   <li>{@code SKIP_FILE_<n>} — n, taken as-is and NOT clamped: a 4-record file with 1 bad record survived
 *       {@code SKIP_FILE_2} (PARTIALLY_LOADED, 3 loaded) and was dropped by {@code SKIP_FILE_1}, and
 *       {@code SKIP_FILE_0} dropped a wholly CLEAN file — 0 rejected records already reaches a budget of 0;</li>
 *   <li>{@code 'SKIP_FILE_<p>%'} — p percent of the records parsed, rounded DOWN, but never below 1. Pinned by three
 *       readings of the account's own reported error_limit: 70% of 4 records = 2 (not 3, so not rounded up), 50% of
 *       3 = 1 (not 2, so not rounded to nearest), and 0% of 2 = 1 (not 0, so floored at one) — the last confirmed
 *       by a clean file loading normally under {@code 'SKIP_FILE_0%'} while {@code SKIP_FILE_0} dropped it.</li>
 * </ul>
 *
 * The budget is per FILE, not per statement: one run over four files under {@code SKIP_FILE_2} reported LOADED /
 * PARTIALLY_LOADED (1 error) / LOAD_FAILED (2 errors) / LOAD_FAILED (2 errors) side by side, so the count never
 * carries from one file to the next.
 */
public final class OnErrorOption {

    /** The default: the statement fails on the first record a file refuses. */
    public static final OnErrorOption ABORT_STATEMENT = new OnErrorOption("ABORT_STATEMENT", null, null);

    private static final Pattern SKIP_FILE_COUNT = Pattern.compile("SKIP_FILE_(\\d+)");

    private static final Pattern SKIP_FILE_SHARE = Pattern.compile("SKIP_FILE_(\\d+(?:\\.\\d+)?)%");

    private static final BigDecimal WHOLE = new BigDecimal(100);

    private final String spelled;

    /** SKIP_FILE_<i>n</i>'s count, or null. */
    private final Integer count;

    /** SKIP_FILE_<i>p</i>%'s share, or null. */
    private final BigDecimal share;

    private OnErrorOption(final String spelled, final Integer count, final BigDecimal share) {
        this.spelled = spelled;
        this.count = count;
        this.share = share;
    }

    /**
     * The option a text spells, or null for a text outside the domain.
     *
     * @param written the value with its quotes already stripped
     * @return the option, or null
     */
    public static OnErrorOption parse(final String written) {
        if (written == null) {
            return null;
        }
        final String upper = written.trim().toUpperCase(Locale.ROOT);
        if ("ABORT_STATEMENT".equals(upper) || "CONTINUE".equals(upper) || "SKIP_FILE".equals(upper)) {
            return new OnErrorOption(upper, null, null);
        }
        final Matcher count = SKIP_FILE_COUNT.matcher(upper);
        if (count.matches()) {
            return new OnErrorOption(upper, Integer.valueOf(countOf(count.group(1))), null);
        }
        final Matcher share = SKIP_FILE_SHARE.matcher(upper);
        if (share.matches()) {
            final BigDecimal percent = new BigDecimal(share.group(1));
            return percent.compareTo(WHOLE) <= 0 ? new OnErrorOption(upper, null, percent) : null;
        }
        return null;
    }

    /** A count too wide for an int is as good as unlimited. */
    private static int countOf(final String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (final NumberFormatException tooWide) {
            return Integer.MAX_VALUE;
        }
    }

    /** The option as COPY keeps it: upper-cased, its quotes gone. */
    public String spelled() {
        return spelled;
    }

    /** Whether the first refused record fails the statement. */
    public boolean aborts() {
        return "ABORT_STATEMENT".equals(spelled);
    }

    /**
     * How many rejected records one file may hold before the whole file is dropped: the budget the class comment
     * describes.
     *
     * @param rowsParsed how many records the file held
     * @return the budget, at least 1
     */
    public int errorLimit(final int rowsParsed) {
        if ("CONTINUE".equals(spelled)) {
            return Math.max(rowsParsed, 1);
        }
        if (count != null) {
            return count.intValue();
        }
        if (share != null) {
            final BigDecimal budget = share.multiply(BigDecimal.valueOf(rowsParsed))
                .divide(WHOLE, 0, RoundingMode.FLOOR);
            return budget.compareTo(BigDecimal.ONE) < 0 ? 1
                : budget.min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
        }
        return 1;
    }
}
