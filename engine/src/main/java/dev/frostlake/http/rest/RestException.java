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
 * A REST request that ends in an error response: the HTTP status, the message, and the Snowflake error code
 * when the failure belongs to a family that has one. The router turns it into the {@code ErrorResponse} body
 * ({@code message}, {@code code}, {@code error_code}, {@code request_id}).
 */
public final class RestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The account's code for a request entity that fails validation. */
    public static final String CODE_REQUEST_ENTITY = "390400";
    /** The account's code for a create-or-alter whose body names another object. */
    public static final String CODE_CREATE_OR_ALTER_RACE = "001520";

    private final int status;
    private final String code;
    private final boolean bodiless;

    /**
     * @param status the HTTP status to answer
     * @param message the error message
     * @param code the Snowflake error code, or null when the failure has none
     */
    public RestException(final int status, final String message, final String code) {
        this(status, message, code, false);
    }

    private RestException(final int status, final String message, final String code, final boolean bodiless) {
        super(message);
        this.status = status;
        this.code = code;
        this.bodiless = bodiless;
    }

    /** A {@code 400 Bad Request} carrying no error code: the request itself is malformed. */
    public static RestException badRequest(final String message) {
        return new RestException(400, message, null);
    }

    /**
     * A request the API cannot read at all — a body that is not JSON or not of the declared shape, a property or a
     * query parameter of the wrong type: {@code 400} with an empty body, as the account answers it.
     *
     * @param reason what was wrong, for the server log only
     */
    public static RestException unreadable(final String reason) {
        return new RestException(400, reason, null, true);
    }

    /** A required property the body leaves out: {@code 400} with the account's request-entity code {@code 390400}. */
    public static RestException missingProperty(final String property) {
        return new RestException(400, "The request entity had the following errors: " + property
            + " cannot be empty string (was '')", CODE_REQUEST_ENTITY);
    }

    /**
     * A {@code PUT} whose body names another object than its path: the account answers the create-or-alter with a
     * conflict, {@code 409} with code {@code 001520}.
     */
    public static RestException createOrAlterRace() {
        return new RestException(409, "Retryable race condition in create or alter", CODE_CREATE_OR_ALTER_RACE);
    }

    /**
     * A {@code 404 Not Found} for a resource the request names, in the account's does-not-exist family. The
     * sentence is the one the statement would refuse with — {@code Warehouse 'W1' does not exist or not
     * authorized.} — and is answered as the account answers a compilation error (see {@link RestSql#message}).
     */
    public static RestException notFound(final String message) {
        final String sentence = message.startsWith(RestSql.COMPILATION_ERROR) || message.startsWith("\n")
            ? message : "\n" + message;
        return new RestException(404, RestSql.message(sentence), RestSql.CODE_DOES_NOT_EXIST);
    }

    /** A {@code 501 Not Implemented}: the endpoint exists in the API but Frostlake does not provide it. */
    public static RestException notImplemented(final String message) {
        return new RestException(501, message, null);
    }

    /** The HTTP status. */
    public int getStatus() {
        return status;
    }

    /** The Snowflake error code, or null. */
    public String getCode() {
        return code;
    }

    /** Whether the answer has an empty body — see {@link #unreadable}. */
    public boolean isBodiless() {
        return bodiless;
    }
}
