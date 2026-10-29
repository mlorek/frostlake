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
package dev.frostlake.executor.commands;

import dev.frostlake.executor.PageSizeRefusal;
import dev.frostlake.executor.SqlCompilationError;

import java.math.BigInteger;

/**
 * The page size a SHOW's LIMIT writes: a whole number from 1 to 10,000, judged in two steps. While the statement
 * compiles, before its scope is looked up, zero is no page size — "page size "0" must be greater than 0 in limit
 * clause" — and a number past 2,147,483,647 is no value for the clause: "invalid value [2147483648] for parameter
 * 'LIMIT_COUNT'". Once the scope is found, a size past 10,000 is refused for every listing, the ones that go on to
 * ignore a LIMIT included: "The LIMIT value for SHOW commands cannot exceed 10,000." (live-verified).
 */
final class ShowPageSize {

    /** The most rows one page may ask for. */
    private static final BigInteger LARGEST = BigInteger.valueOf(10000);

    /** The largest number the clause reads at all. */
    private static final BigInteger LARGEST_READ = BigInteger.valueOf(Integer.MAX_VALUE);

    private ShowPageSize() {
    }

    /**
     * The first step: refuse a size of zero and a number the clause does not read.
     *
     * @param written the LIMIT's number as written
     */
    static void requireReadable(final String written) {
        final BigInteger limit = new BigInteger(written);
        if (limit.signum() <= 0) {
            throw new PageSizeRefusal("page size \"" + limit + "\" must be greater than 0 in limit clause");
        }
        if (limit.compareTo(LARGEST_READ) > 0) {
            throw new PageSizeRefusal(SqlCompilationError.of("invalid value [" + written
                + "] for parameter 'LIMIT_COUNT'"));
        }
    }

    /**
     * Both steps: the page size a LIMIT writes, or its refusal.
     *
     * @param written the LIMIT's number as written
     * @return the page size
     */
    static int require(final String written) {
        requireReadable(written);
        final BigInteger limit = new BigInteger(written);
        if (limit.compareTo(LARGEST) > 0) {
            throw new RuntimeException("The LIMIT value for SHOW commands cannot exceed 10,000.");
        }
        return limit.intValue();
    }
}
