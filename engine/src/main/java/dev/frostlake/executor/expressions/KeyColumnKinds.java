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

package dev.frostlake.executor.expressions;

import java.util.List;

/**
 * The kind each column of a hashed correlation key holds (see {@link CorrelationKeys}), shared by
 * {@link PreparedKeySet} and {@link PreparedKeyedValues}: a column settles a probe only while its members are all of
 * one kind and the probe's value is of that kind too.
 */
final class KeyColumnKinds {

    private KeyColumnKinds() {
    }

    /**
     * Notes a member's kinds in {@code kinds}: a column whose members differ in kind, or hold a kind no hash settles,
     * settles no probe from then on.
     *
     * @param kinds each column's kind so far
     * @param key   the member, none of its values NULL
     * @return whether every value of the member is of a kind a hash settles
     */
    static boolean note(final String[] kinds, final List<Object> key) {
        boolean hashed = true;
        for (int i = 0; i < kinds.length; i++) {
            final String kind = CorrelationKeys.kindOf(key.get(i));
            if (kind == null) {
                kinds[i] = "";
                hashed = false;
            } else if (kinds[i] == null) {
                kinds[i] = kind;
            } else if (!kinds[i].equals(kind)) {
                kinds[i] = "";
            }
        }
        return hashed;
    }

    /**
     * Whether a probe is settled: each value of the kind its column's members hold, or of any kind a hash settles
     * where the column has no member.
     *
     * @param kinds each column's kind
     * @param probe the probe, none of its values NULL
     * @return whether it is settled
     */
    static boolean answers(final String[] kinds, final List<Object> probe) {
        for (int i = 0; i < kinds.length; i++) {
            final String kind = CorrelationKeys.kindOf(probe.get(i));
            if (kind == null || kinds[i] != null && !kinds[i].equals(kind)) {
                return false;
            }
        }
        return true;
    }
}
