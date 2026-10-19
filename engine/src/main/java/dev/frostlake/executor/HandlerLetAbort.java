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
 * An exception handler read a name its block's BEGIN section declared with LET (see {@link HandlerLets}). The
 * name is none of the handler's, so the read is refused as a name that resolves to nothing — and it ends the
 * body: no handler of the body catches it and no block of it words it as an uncaught exception, while the
 * statement that ran the body, a CALL or a dynamic text, fails as any statement does and may be caught there
 * (live-verified).
 */
public final class HandlerLetAbort extends BodyAbort {

    private static final long serialVersionUID = 1L;

    /**
     * @param name the name read, upper-cased
     */
    public HandlerLetAbort(final String name) {
        super(SqlCompilationError.invalidIdentifier(name));
    }

    /**
     * @param name     the name read, upper-cased
     * @param line     the line the name is written on
     * @param position the name's position on that line
     */
    public HandlerLetAbort(final String name, final int line, final int position) {
        super(SqlCompilationError.invalidIdentifier(line, position, name));
    }
}
