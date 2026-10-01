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

/**
 * {@code GET /api/v2/results/{result_handler}} ({@code result.yaml}): the state of an operation answered
 * {@code 202}. It is answered in place — never itself deferred — and runs no SQL. Results are not paged, so
 * {@code page} may only name the first page.
 */
public final class RestResultResource implements RestResource {

    @Override
    public void register(final RestRouter router) {
        router.add("GET", "/api/v2/results/{result_handler}", "fetchResult", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        final Long page = call.integer("page");
        if (page != null && page.longValue() != 0L) {
            throw RestException.badRequest("Invalid page " + page + ": the result has 1 page.");
        }
        return call.context().results().fetch(call.pathParameter("result_handler"));
    }
}
