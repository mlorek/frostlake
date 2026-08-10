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

/**
 * Cache key for statement-invariant facts of the generic aggregate path: a kind tag, up to three
 * CONTEXT objects compared by reference IDENTITY (a new table / context object means a new key, so
 * no explicit clearing is needed across statements), and the argument text. Identity comparison —
 * not identityHashCode strings — keeps distinct contexts from ever colliding.
 */
public final class AggFactKey {

    private final String kind;
    private final Object context1;
    private final Object context2;
    private final Object context3;
    private final String text;

    public AggFactKey(final String kind, final Object context1, final Object context2,
                      final Object context3, final String text) {
        this.kind = kind;
        this.context1 = context1;
        this.context2 = context2;
        this.context3 = context3;
        this.text = text;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof AggFactKey)) {
            return false;
        }
        final AggFactKey key = (AggFactKey) other;
        return kind.equals(key.kind) && context1 == key.context1 && context2 == key.context2
            && context3 == key.context3 && text.equals(key.text);
    }

    @Override
    public int hashCode() {
        int h = kind.hashCode();
        h = 31 * h + System.identityHashCode(context1);
        h = 31 * h + System.identityHashCode(context2);
        h = 31 * h + System.identityHashCode(context3);
        h = 31 * h + text.hashCode();
        return h;
    }
}
