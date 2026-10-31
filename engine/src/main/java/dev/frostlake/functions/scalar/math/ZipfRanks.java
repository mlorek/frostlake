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

package dev.frostlake.functions.scalar.math;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ranks of a Zipf law over 1..n as the account draws them (live-verified): a draw's fraction u picks the first
 * rank whose weights, 1 / k^s summed from rank 1, reach u of all of them. The sums of the laws a thread drew from last
 * are kept, as the rows of a statement draw from one law.
 */
final class ZipfRanks {

    /** The most ranks whose running sums are kept, eight megabytes of them; past it each draw sums as it goes. */
    private static final int KEPT_RANKS = 1 << 20;

    /** How many laws a thread keeps. */
    private static final int KEPT_LAWS = 4;

    private static final ThreadLocal<Map<String, ZipfRanks>> KEPT = new ThreadLocal<Map<String, ZipfRanks>>() {
        @Override
        protected Map<String, ZipfRanks> initialValue() {
            return new LinkedHashMap<String, ZipfRanks>(KEPT_LAWS * 2, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, ZipfRanks> eldest) {
                    return size() > KEPT_LAWS;
                }
            };
        }
    };

    private final double exponent;
    private final long n;
    /** The running sum of the weights through each rank, or null for a law past {@link #KEPT_RANKS}. */
    private final double[] sums;
    private final double total;

    private ZipfRanks(final double exponent, final long n) {
        this.exponent = exponent;
        this.n = n;
        double running = 0.0;
        if (n <= KEPT_RANKS) {
            sums = new double[(int) n];
            for (int k = 1; k <= n; k++) {
                running += weight(k);
                sums[k - 1] = running;
            }
        } else {
            sums = null;
            for (long k = 1; k <= n; k++) {
                running += weight(k);
            }
        }
        total = running;
    }

    /**
     * The law of an exponent over n ranks, kept for the thread's next draws.
     *
     * @param exponent the law's exponent, finite and not negative
     * @param n        how many ranks it has
     * @return the law
     */
    static ZipfRanks of(final double exponent, final long n) {
        final String key = Double.doubleToLongBits(exponent) + ":" + n;
        final Map<String, ZipfRanks> kept = KEPT.get();
        ZipfRanks law = kept.get(key);
        if (law == null) {
            law = new ZipfRanks(exponent, n);
            kept.put(key, law);
        }
        return law;
    }

    /**
     * The rank a fraction draws: the first whose running sum over the total reaches it.
     *
     * @param fraction a draw's fraction, in [0, 1)
     * @return the rank, in 1..n
     */
    long rank(final double fraction) {
        if (sums != null) {
            int low = 0;
            int high = sums.length - 1;
            while (low < high) {
                final int middle = (low + high) >>> 1;
                if (sums[middle] / total >= fraction) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            return low + 1L;
        }
        double running = 0.0;
        for (long k = 1; k <= n; k++) {
            running += weight(k);
            if (running / total >= fraction) {
                return k;
            }
        }
        return n;
    }

    private double weight(final long rank) {
        return 1.0 / Math.pow(rank, exponent);
    }
}
