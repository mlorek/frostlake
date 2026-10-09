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
package dev.frostlake.executor.commands;

import java.io.Serializable;
import java.util.Comparator;

/** Orders one statement's session parameter names by their text, an upper-case letter before a lower-case one. */
final class SessionParameterSettingOrder implements Comparator<SessionParameterSetting>, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public int compare(final SessionParameterSetting left, final SessionParameterSetting right) {
        return left.ordering().compareTo(right.ordering());
    }
}
