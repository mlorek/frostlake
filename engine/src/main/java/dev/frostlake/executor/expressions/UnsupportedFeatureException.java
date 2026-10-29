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


package dev.frostlake.executor.expressions;

/**
 * Live's refusal of a construct it does not support, raised while a statement compiles and reported without the
 * compilation-error prefix: {@code Unsupported feature 'VARIANCE(DISTINCT)'.} A type of its own, so the checks that
 * judge a statement's calls before any row is read pass it on as they pass on a compilation error, where they let
 * any other failure go.
 */
public class UnsupportedFeatureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * The refusal of a feature.
     *
     * @param feature the feature as live names it
     */
    public UnsupportedFeatureException(final String feature) {
        super("Unsupported feature '" + feature + "'.");
    }
}
