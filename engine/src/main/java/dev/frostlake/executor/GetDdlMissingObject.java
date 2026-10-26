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
 * GET_DDL's refusal of an object it does not find, in the sentence of the kind asked for. It carries error 2003
 * with SQLSTATE 02000 to a client's driver and to a handler whatever the kind, where the same sentence raised for
 * a missing table or view anywhere else - {@code SELECT * FROM}, DROP, DESCRIBE - carries 42S02 (live-verified).
 */
public class GetDdlMissingObject extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The error code a handler's SQLCODE and a client's driver read. */
    public static final int CODE = 2003;

    /** The SQLSTATE a handler and a client's driver read. */
    public static final String STATE = "02000";

    /**
     * @param message the refusal's sentence
     */
    public GetDdlMissingObject(final String message) {
        super(message);
    }
}
