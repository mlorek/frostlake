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

package dev.frostlake.executor.udf;

import java.util.Objects;

/**
 * What a handler runtime's state belongs to within one statement: a routine, the place in the statement that
 * calls it, and whatever else of the session the state was made for. The routine is told apart by identity, the
 * place as a {@link CallSite} tells places apart, and the rest by value.
 */
final class HandlerStateKey {

    private final Object owner;
    private final Object site;
    private final Object variant;

    HandlerStateKey(final Object owner, final Object site, final Object variant) {
        this.owner = owner;
        this.site = site;
        this.variant = variant;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof HandlerStateKey)) {
            return false;
        }
        final HandlerStateKey that = (HandlerStateKey) other;
        return owner == that.owner && Objects.equals(site, that.site) && Objects.equals(variant, that.variant);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * System.identityHashCode(owner) + Objects.hashCode(site)) + Objects.hashCode(variant);
    }
}
