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

package dev.frostlake.http;


/**
 * Serializable column data
 */
public class ColumnData {
    private String name;
    private String dataType;
    private int precision;
    private int scale;
    // Whether the column is KNOWN to accept NULL. A Boolean rather than a boolean so ABSENT stays
    // distinguishable from false: a server that predates this field sends neither, and the client
    // must read that as unknown rather than as "not nullable". Every driver reads this object by
    // key, so the added field is ignored by the ones that do not want it.
    private Boolean nullable;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(final String dataType) {
        this.dataType = dataType;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(final Boolean nullable) {
        this.nullable = nullable;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(final int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(final int scale) {
        this.scale = scale;
    }
}
