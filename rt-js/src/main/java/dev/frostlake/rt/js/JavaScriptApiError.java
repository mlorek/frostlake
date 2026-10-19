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

package dev.frostlake.rt.js;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * An error of the procedure API that left a handler uncaught: a failed statement's or a misuse's (see
 * {@code JavaScriptHandler}). An account words it by its sentence and the place of the API call (live-verified):
 * {@code Execution error in store procedure P:}, the sentence, {@code At Snowflake.execute, line 1 position 11}.
 */
final class JavaScriptApiError {

    private final String message;
    private final String place;

    private JavaScriptApiError(final String message, final String place) {
        this.message = message;
        this.place = place;
    }

    /**
     * @param failure the engine's failure
     * @return the API's error behind it, or null when what was thrown is no error of the API
     */
    static JavaScriptApiError of(final Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof PolyglotException)) {
            cause = cause.getCause();
        }
        if (cause == null || !((PolyglotException) cause).isGuestException()) {
            return null;
        }
        final Value thrown = ((PolyglotException) cause).getGuestObject();
        if (thrown == null || !thrown.hasMembers()) {
            return null;
        }
        final Value made = thrown.getMetaObject();
        final String kind = made == null ? null : made.getMetaSimpleName();
        if (!"createExecQueryError".equals(kind) && !"createExecError".equals(kind)) {
            return null;
        }
        final Value message = thrown.getMember("message");
        final Value place = thrown.getMember("stack");
        if (message == null || !message.isString() || place == null || !place.isString()) {
            return null;
        }
        return new JavaScriptApiError(message.asString(), place.asString());
    }

    String message() {
        return message;
    }

    String place() {
        return place;
    }
}
