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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What the router found for a request: the route and its path parameters, or — when no route serves the method
 * but some serve the path — the methods that do.
 */
public final class RestRouteMatch {

    private final RestRoute route;
    private final Map<String, String> parameters;
    private final List<String> allowedMethods;

    RestRouteMatch(final RestRoute route, final Map<String, String> parameters, final List<String> allowedMethods) {
        this.route = route;
        this.parameters = parameters == null ? Collections.<String, String>emptyMap() : parameters;
        this.allowedMethods = allowedMethods == null ? Collections.<String>emptyList() : allowedMethods;
    }

    /** The route, or null when the path is served under other methods only. */
    public RestRoute route() {
        return route;
    }

    /** The path parameters, decoded. */
    public Map<String, String> parameters() {
        return parameters;
    }

    /** The methods the path is served under, when {@link #route()} is null. */
    public List<String> allowedMethods() {
        return allowedMethods;
    }
}
