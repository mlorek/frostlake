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

package dev.frostlake.types;

import dev.frostlake.executor.SessionTimestampMapping;
import java.util.Locale;

/**
 * Which timestamp flavour a FUNCTION NAME asks for. Several conversions share one implementation but
 * not one declared type — live reports {@code TO_TIMESTAMP_LTZ(d)} as TIMESTAMP_LTZ,
 * {@code TIMESTAMP_TZ_FROM_PARTS(…)} as TIMESTAMP_TZ, and the unsuffixed spellings as TIMESTAMP_NTZ —
 * so the registered name is what decides, not the shared code behind it.
 */
public final class TimestampFlavours {

    private TimestampFlavours() {
    }

    /**
     * The flavour a function of that name declares.
     *
     * @param functionName the registered name
     * @return the timestamp type it reports
     */
    public static DateTimeType forFunctionName(final String functionName) {
        final String name = functionName == null ? "" : functionName.toUpperCase(Locale.ROOT);
        // Checked longest-first: NTZ contains TZ, and the underscore-less spellings
        // (TIMESTAMPLTZFROMPARTS) carry the flavour without a separator to anchor on.
        if (name.contains("LTZ")) {
            return DateTimeType.TIMESTAMP_LTZ;
        }
        if (name.contains("NTZ")) {
            return DateTimeType.TIMESTAMP_NTZ;
        }
        if (name.contains("TZ")) {
            return DateTimeType.TIMESTAMP_TZ;
        }
        // A name carrying NO flavour is the BARE TIMESTAMP spelling, and follows the session's
        // TIMESTAMP_TYPE_MAPPING: live declares TO_TIMESTAMP(x) TIMESTAMP_LTZ(9) under that mapping.
        if (SessionTimestampMapping.isZoned()) {
            return new DateTimeType(SessionTimestampMapping.current(), 9, true);
        }
        return DateTimeType.TIMESTAMP_NTZ;
    }
}
