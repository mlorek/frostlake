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

package dev.frostlake.jdbc;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Minimal {@link ParameterMetaData} for the engine's PreparedStatements: reports the placeholder count.
 * Per-parameter types are generic ({@code OTHER}) because parameters are inlined client-side as SQL literals
 * (there is no server-side bind that would carry a declared type).
 */
public class JdbcParameterMetaData implements ParameterMetaData {

    private final int parameterCount;

    public JdbcParameterMetaData(final int parameterCount) {
        this.parameterCount = parameterCount;
    }

    @Override
    public int getParameterCount() throws SQLException {
        return parameterCount;
    }

    @Override
    public int isNullable(final int param) throws SQLException {
        return parameterNullableUnknown;
    }

    @Override
    public boolean isSigned(final int param) throws SQLException {
        return true;
    }

    @Override
    public int getPrecision(final int param) throws SQLException {
        return 0;
    }

    @Override
    public int getScale(final int param) throws SQLException {
        return 0;
    }

    @Override
    public int getParameterType(final int param) throws SQLException {
        return Types.OTHER;
    }

    @Override
    public String getParameterTypeName(final int param) throws SQLException {
        return "OTHER";
    }

    @Override
    public String getParameterClassName(final int param) throws SQLException {
        return "java.lang.Object";
    }

    @Override
    public int getParameterMode(final int param) throws SQLException {
        return parameterModeIn;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper for " + iface);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }
}
