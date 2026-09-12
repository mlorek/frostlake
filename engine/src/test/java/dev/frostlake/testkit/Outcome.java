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

package dev.frostlake.testkit;

import java.util.ArrayList;
import java.util.List;

/** The verdict on one step's expectations, and the checks it had to skip for a missing capability. */
final class Outcome {

    private final boolean passed;
    private final String detail;
    private final List<String> capabilitySkips = new ArrayList<>();

    private Outcome(final boolean passed, final String detail) {
        this.passed = passed;
        this.detail = detail;
    }

    static Outcome success() {
        return new Outcome(true, null);
    }

    static Outcome failure(final String detail) {
        return new Outcome(false, detail);
    }

    boolean passed() {
        return passed;
    }

    /** @return why the step failed, or null when it passed */
    String detail() {
        return detail;
    }

    List<String> capabilitySkips() {
        return capabilitySkips;
    }

    void skipCheck(final String why) {
        capabilitySkips.add(why);
    }
}
