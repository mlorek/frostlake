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

import java.util.List;
import java.util.Map;

/**
 * One endpoint: an HTTP method and a path template — literal segments, {@code {parameter}} segments and an
 * optional {@code :action} — served by a resource under the specification's {@code operationId}.
 */
public final class RestRoute {

    private final String method;
    private final RestPath template;
    private final String operationId;
    private final RestResource resource;
    private final String bodyType;
    private final int literals;

    /**
     * @param method the HTTP method
     * @param template the path template
     * @param operationId the specification's operation id, which the resource dispatches on
     * @param resource the resource serving the endpoint
     * @param bodyType the media type the endpoint reads its body in, or null when it takes any
     */
    RestRoute(final String method, final String template, final String operationId, final RestResource resource,
              final String bodyType) {
        this.method = method;
        this.template = RestPath.ofTemplate(template);
        this.operationId = operationId;
        this.resource = resource;
        this.bodyType = bodyType;
        int count = 0;
        for (final String segment : this.template.segments()) {
            if (!isParameter(segment)) {
                count++;
            }
        }
        this.literals = count;
    }

    private static boolean isParameter(final String segment) {
        return segment.length() > 2 && segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}';
    }

    /**
     * Matches a request path against the template, collecting the parameters.
     *
     * @param path the request path
     * @param parameters receives the parameter values when the path matches
     * @return whether it matches
     */
    boolean matches(final RestPath path, final Map<String, String> parameters) {
        final List<String> want = template.segments();
        final List<String> have = path.segments();
        if (want.size() != have.size()) {
            return false;
        }
        if (template.action() == null ? path.action() != null : !template.action().equals(path.action())) {
            return false;
        }
        for (int i = 0; i < want.size(); i++) {
            final String segment = want.get(i);
            if (isParameter(segment)) {
                if (have.get(i).isEmpty()) {
                    return false;
                }
                parameters.put(segment.substring(1, segment.length() - 1), have.get(i));
            } else if (!segment.equals(have.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The HTTP method. */
    public String method() {
        return method;
    }

    /** The specification's operation id. */
    public String operationId() {
        return operationId;
    }

    /** The resource serving the endpoint. */
    public RestResource resource() {
        return resource;
    }

    /** The media type the endpoint reads its body in, or null when it takes any. */
    public String bodyType() {
        return bodyType;
    }

    /** How many of the template's segments are literal; the more specific route wins a tie. */
    int literalCount() {
        return literals;
    }

    @Override
    public String toString() {
        return method + " " + template + " [" + operationId + "]";
    }
}
