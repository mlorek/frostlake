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
 * A failure that is no exception of the body it happens in: no handler of that body catches it, and no block of
 * the body words it as an uncaught exception (live-verified for the two that exist). A FETCH from a cursor that
 * is not open is one — the statement fails with the bare {@code CURSOR C is not open}, past a WHEN OTHER around it
 * — and so is a statement issued past the depth bodies run to (see {@link CallDepthExceeded}).
 */
public class BodyAbort extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the failure's sentence
     */
    public BodyAbort(final String message) {
        super(message);
    }
}
