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
 * A refusal raised while a stored view's body is expanded into the statement that reads it, of the kind the
 * account reports under the view's name: a relation the body reads is gone, or a materialized view it reads is
 * invalid. Each view between the statement and the fault adds its own
 * {@code Failure during expansion of view 'V':} in front, outermost first (live-verified). A body that no longer
 * compiles for another reason — a column its table lost — is refused in that refusal's own words, at its place
 * in the view's CREATE statement, and is no failure of this kind.
 */
final class ViewExpansionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal as a client reads it
     */
    ViewExpansionException(final String message) {
        super(message);
    }

    /**
     * The refusal as the view around it reports it.
     *
     * @param viewName the view whose body was being expanded, as it is stored
     * @return the refusal with that view named in front
     */
    ViewExpansionException within(final String viewName) {
        return new ViewExpansionException(SqlCompilationError.of(
            "Failure during expansion of view '" + viewName + "': " + getMessage()));
    }
}
