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
 * One resource of the REST API — warehouses, databases, tables, … — serving the endpoints its specification
 * defines. A resource registers each endpoint under the specification's {@code operationId} and dispatches on
 * {@link RestCall#operation()} when one is called.
 */
public interface RestResource {

    /** Registers this resource's endpoints. */
    void register(RestRouter router);

    /**
     * Serves one call of an endpoint this resource registered.
     *
     * @return the answer
     * @throws RestException for a request answered with an error
     */
    RestResponse handle(RestCall call);
}
