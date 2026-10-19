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

package dev.frostlake.rt.py;

/**
 * A routine's answer that its declared type does not take, worded as the account words it (see
 * {@link PythonReturn}). The handler ran and the interpreter is as sound as it was, so the refusal travels as it is.
 */
final class PythonReturnRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal, naming the routine and its handler
     */
    PythonReturnRefusal(final String message) {
        super(message);
    }
}
