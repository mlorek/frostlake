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

package dev.frostlake.functions.scalar.conversion;

/**
 * {@code DATE(<expr>)} — the alias of TO_DATE that ALSO accepts an epoch NUMBER. Live-verified on a
 * real account: {@code SELECT DATE(1631711999)} is 2021-09-15 while the identical
 * {@code SELECT TO_DATE(1631711999)} fails "invalid type [TO_DATE(1631711999)] for parameter
 * 'TO_DATE'". Everything else is TO_DATE's behaviour.
 */
public class DateFunction extends ToDate {

    public DateFunction() { super("DATE"); }

    @Override
    protected boolean acceptsNumericEpoch() { return true; }
}
