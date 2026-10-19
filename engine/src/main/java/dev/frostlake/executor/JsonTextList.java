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

import java.util.List;

/** A list of texts as the JSON array a metadata cell holds: {@code ["a","b"]}. */
public final class JsonTextList {

    private JsonTextList() {
    }

    /**
     * The JSON array of some texts.
     *
     * @param items the texts
     * @return the array's text, {@code []} for none
     */
    public static String of(final List<String> items) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; items != null && i < items.size(); i++) {
            text.append(i == 0 ? "" : ",").append('"')
                .append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return text.append(']').toString();
    }
}
