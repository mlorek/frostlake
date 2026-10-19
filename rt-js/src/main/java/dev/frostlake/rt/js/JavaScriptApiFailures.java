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

import dev.frostlake.executor.commands.FailureCode;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * What the procedure API's host objects answer in place of a value when a call fails: a led text the script-side
 * API objects read and throw as the error an account's handler catches (live-verified). A statement that failed
 * carries its code, its state and its sentence — {@code 904}, {@code 42000}, {@code SQL compilation error: …} —
 * and a misuse of the API the code 100183 and the state P0000 with its own sentence, such as {@code ResultSet is
 * empty or not prepared (call next() first).} The failures themselves are kept for the CALL, so that one the
 * handler lets through still reaches SQL with what caused it.
 */
final class JavaScriptApiFailures {

    /** Leads a failure's text; its JSON follows. */
    static final String LEAD = "\u0001E:";

    /** The code and the state of a misuse of the API. */
    private static final int MISUSE_CODE = 100183;
    private static final String MISUSE_STATE = "P0000";

    private final List<Throwable> kept = new ArrayList<>();

    /**
     * A statement that failed.
     *
     * @param failure what it failed with
     * @return the led text
     */
    String statement(final Throwable failure) {
        final FailureCode code = FailureCode.of(failure);
        kept.add(failure);
        return told(true, code.code(), code.state(), FailureCode.messageOf(failure));
    }

    /**
     * A call the API refuses.
     *
     * @param sentence the refusal
     * @return the led text
     */
    String misuse(final String sentence) {
        return told(false, MISUSE_CODE, MISUSE_STATE, sentence);
    }

    /**
     * @param message a failed statement's sentence
     * @return what the last statement that failed with it failed with, or null when none did
     */
    Throwable causeOf(final String message) {
        for (int i = kept.size() - 1; i >= 0; i--) {
            if (FailureCode.messageOf(kept.get(i)).equals(message)) {
                return kept.get(i);
            }
        }
        return null;
    }

    private static String told(final boolean statement, final int code, final String state, final String message) {
        final ObjectNode json = ArrayFunctionHelper.MAPPER.createObjectNode();
        json.put("statement", statement);
        json.put("code", code);
        json.put("state", state);
        json.put("message", message);
        return LEAD + ArrayFunctionHelper.MAPPER.writeValueAsString(json);
    }
}
