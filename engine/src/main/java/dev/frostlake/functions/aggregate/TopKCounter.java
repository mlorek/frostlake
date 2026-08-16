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

package dev.frostlake.functions.aggregate;

/**
 * One counter of a {@link TopKSummary}: a value, how many times the summary has counted it, and the
 * tick of the summary's clock at which it was last touched — the tie-breaker every ordering rule of
 * the APPROX_TOP_K family reads.
 */
public class TopKCounter {

    private final Object key;
    private final Object value;
    private long count;
    private long updatedAt;

    TopKCounter(final Object key, final Object value, final long count, final long updatedAt) {
        this.key = key;
        this.value = value;
        this.count = count;
        this.updatedAt = updatedAt;
    }

    /** The value as it was first seen, which is how the counter renders. */
    public Object value() {
        return value;
    }

    public long count() {
        return count;
    }

    Object key() {
        return key;
    }

    long updatedAt() {
        return updatedAt;
    }

    void increase(final long by, final long now) {
        count += by;
        updatedAt = now;
    }
}
