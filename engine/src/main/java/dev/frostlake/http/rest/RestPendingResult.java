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

package dev.frostlake.http.rest;

import java.util.concurrent.Future;

/** An operation answered with {@code 202}: its future, and when it was accepted, for expiry. */
final class RestPendingResult {

    private final Future<RestResponse> future;
    private final long acceptedAtMillis;

    RestPendingResult(final Future<RestResponse> future, final long acceptedAtMillis) {
        this.future = future;
        this.acceptedAtMillis = acceptedAtMillis;
    }

    Future<RestResponse> future() {
        return future;
    }

    long acceptedAtMillis() {
        return acceptedAtMillis;
    }
}
