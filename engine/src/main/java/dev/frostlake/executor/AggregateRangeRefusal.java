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
 * An aggregate's value that its declared type cannot hold — live's row-time "Number out of
 * representable range: type FIXED[SB16](38,38){nullable}, value 3", raised where the DECLARED type is
 * still a legal NUMBER but the value written into it is not: MEDIAN over a NUMBER(38,35) declares
 * NUMBER(38,38), and a median of 3 has no room there.
 *
 * <p>A type of its own for the reason {@link NumericConversionException} is one: the aggregate paths
 * wrap their evaluation in a blanket catch that turns a failure into a NULL result, which is wrong for
 * a refusal the user has to see. This is rethrown through those catches.
 */
public class AggregateRangeRefusal extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * @param message live's sentence, which carries no compilation prefix
     */
    public AggregateRangeRefusal(final String message) {
        super(message);
    }
}
