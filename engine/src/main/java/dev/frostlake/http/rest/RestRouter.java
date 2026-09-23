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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The route table of the {@code /api/v2} surface. Routes are matched segment by segment; when two templates
 * match one path the one with more literal segments wins, so {@code /compute-pools/instance-families} is not
 * read as a pool named {@code instance-families}.
 */
public final class RestRouter {

    private final List<RestRoute> routes = new ArrayList<>();

    /** Registers an endpoint that reads a JSON body, or none. */
    public void add(final String method, final String template, final String operationId,
                    final RestResource resource) {
        add(method, template, operationId, resource, RestResponse.JSON);
    }

    /**
     * Registers an endpoint.
     *
     * @param method the HTTP method
     * @param template the path template, {@code {name}} marking a parameter and {@code :action} an action
     * @param operationId the specification's operation id
     * @param resource the resource serving it
     * @param bodyType the media type of the body the endpoint reads, or null when it accepts any
     */
    public void add(final String method, final String template, final String operationId,
                    final RestResource resource, final String bodyType) {
        routes.add(new RestRoute(method, template, operationId, resource, bodyType));
    }

    /** Every registered route, in registration order. */
    public List<RestRoute> routes() {
        return routes;
    }

    /**
     * Finds the route for a request.
     *
     * @return the match; null when no route serves the path under any method
     */
    public RestRouteMatch match(final String method, final RestPath path) {
        RestRoute best = null;
        Map<String, String> bestParameters = null;
        final List<String> allowed = new ArrayList<>();
        for (final RestRoute route : routes) {
            final Map<String, String> parameters = new HashMap<>();
            if (!route.matches(path, parameters)) {
                continue;
            }
            if (!route.method().equals(method)) {
                if (!allowed.contains(route.method())) {
                    allowed.add(route.method());
                }
                continue;
            }
            if (best == null || route.literalCount() > best.literalCount()) {
                best = route;
                bestParameters = parameters;
            }
        }
        if (best != null) {
            return new RestRouteMatch(best, bestParameters, null);
        }
        return allowed.isEmpty() ? null : new RestRouteMatch(null, null, allowed);
    }
}
