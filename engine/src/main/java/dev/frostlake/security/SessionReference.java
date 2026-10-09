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
package dev.frostlake.security;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A {@link SessionDirectory} entry: the session, held weakly so that a connection nobody closed can still be
 * collected, beside the number it was registered under, which outlives it for the directory to clear.
 */
final class SessionReference extends WeakReference<AbortableSession> {

    private final Long sessionNumber;

    SessionReference(final Long sessionNumber, final AbortableSession session,
                     final ReferenceQueue<AbortableSession> collected) {
        super(session, collected);
        this.sessionNumber = sessionNumber;
    }

    Long sessionNumber() {
        return sessionNumber;
    }
}
