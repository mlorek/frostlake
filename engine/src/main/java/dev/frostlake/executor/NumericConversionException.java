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
 * A value that could not be read as a number where one was required — live's
 * "Numeric value 'a' is not recognized", and the VARIANT spelling of the same failure.
 *
 * <p>It is a type of its own for one reason: the aggregate paths wrap their evaluation in a blanket
 * catch that turns a failure into a NULL result, which is the right thing for an expression that
 * merely does not apply to a group and the wrong thing for a refusal the user has to see. This is
 * rethrown through those catches, as {@link InvalidQualifierException} already is.
 */
public class NumericConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message live's sentence, which carries no compilation prefix
     */
    public NumericConversionException(final String message) {
        super(message);
    }
}
