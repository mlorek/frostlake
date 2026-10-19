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

package dev.frostlake.stream;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * How a row's identity is spelled as METADATA$ROW_ID. The account spells a table row's id as forty lower-case hex
 * digits, the same in every stream over the table, in a stream over a view of it and in a CHANGES clause; a row of
 * a UNION ALL view's branch as the base row's id, a colon and twenty-eight base64 characters; a row of a join view
 * as forty hex digits of its own (live-verified). The digits themselves are the account's secret: here they are a
 * digest of the engine's own row identity, which keeps every property a query can rely on, that an id is one row's
 * alone, that an UPDATE and a DELETE report the id the row was inserted under, and that it reads the same wherever
 * the row is seen.
 */
public final class StreamRowIds {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Tells a table row's digest from a view branch's, a join row's, a refreshed source's and joined rows'. */
    private static final byte TABLE_ROW = 1;
    private static final byte BRANCH_ROW = 2;
    private static final byte JOIN_ROW = 3;
    private static final byte REFRESHED_ROW = 4;
    private static final byte JOINED_ROWS = 5;

    private StreamRowIds() {
    }

    /**
     * The id of a table's row.
     *
     * @param rowId the row's identity in the table's storage
     * @return forty lower-case hex digits
     */
    public static String ofRow(final long rowId) {
        return hex(digest(TABLE_ROW, ByteBuffer.allocate(Long.BYTES).putLong(rowId).array()));
    }

    /**
     * The id of a row read through one branch of a UNION ALL view: the base row's id, then what tells the branch.
     *
     * @param rowId  the base row's identity in its table's storage
     * @param branch the branch's position in the view's query, from 0
     * @return the base row's id, a colon and twenty-eight base64 characters
     */
    public static String ofBranchRow(final long rowId, final int branch) {
        final byte[] suffix = digest(BRANCH_ROW,
            ByteBuffer.allocate(Long.BYTES + Integer.BYTES).putLong(rowId).putInt(branch).array());
        return ofRow(rowId) + ":" + Base64.getEncoder().encodeToString(suffix);
    }

    /**
     * The id of a join view's row, which no single table row stands behind: a digest of what the row holds.
     *
     * @param content the row's values, spelled so that equal rows spell alike
     * @return forty lower-case hex digits
     */
    public static String ofJoinRow(final String content) {
        return hex(digest(JOIN_ROW, content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The id of a join view's row, from the identities of the table rows it joins: an UPDATE of either keeps the
     * row's id, so the change reads as a DELETE and an INSERT of one row (live-verified).
     *
     * @param rowIds the joined rows' identities in their tables' storage, in the order the view's query reads them
     * @return forty lower-case hex digits
     */
    public static String ofJoinedRows(final List<Long> rowIds) {
        final ByteBuffer ids = ByteBuffer.allocate(Long.BYTES * rowIds.size());
        for (final Long rowId : rowIds) {
            ids.putLong(rowId.longValue());
        }
        return hex(digest(JOINED_ROWS, ids.array()));
    }

    /**
     * The id of a row of a source no DML writes, a dynamic table refreshed into a stream.
     *
     * @param ordinal the row's number among those the stream recorded
     * @return forty lower-case hex digits
     */
    public static String ofRefreshedRow(final long ordinal) {
        return hex(digest(REFRESHED_ROW, ByteBuffer.allocate(Long.BYTES).putLong(ordinal).array()));
    }

    private static byte[] digest(final byte kind, final byte[] content) {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(kind);
            return sha1.digest(content);
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 is required of every Java platform", impossible);
        }
    }

    private static String hex(final byte[] bytes) {
        final char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }
}
