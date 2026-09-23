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

package dev.frostlake.metastore.model;

/** The two kinds of app object a schema holds: a notebook and a Streamlit app. */
public enum AppObjectKind {
    /** A notebook ({@code CREATE NOTEBOOK}). */
    NOTEBOOK("Notebook", "notebook"),
    /** A Streamlit app ({@code CREATE STREAMLIT}). */
    STREAMLIT("Streamlit", "streamlit");

    private final String displayName;
    private final String uriScheme;

    AppObjectKind(final String displayName, final String uriScheme) {
        this.displayName = displayName;
        this.uriScheme = uriScheme;
    }

    /** The kind as refusals name it, e.g. {@code Notebook}. */
    public String displayName() {
        return displayName;
    }

    /** The kind's segment in a version location URI, e.g. {@code snow://notebook/...}. */
    public String uriScheme() {
        return uriScheme;
    }
}
