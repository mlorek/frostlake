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

package dev.frostlake.parser;

/**
 * How many parameters a type's name takes in its parentheses, family by family (live-verified):
 *
 * <pre>
 *   VARCHAR, CHAR, STRING, TEXT, NCHAR, NVARCHAR…  none or one    VARCHAR()  VARCHAR(5)
 *   FLOAT, FLOAT4, FLOAT8, REAL                    none or one    FLOAT()    FLOAT(5)
 *   NUMBER, DECIMAL, NUMERIC, DEC                  one or two     NUMBER(5)  NUMBER(5,2)
 *   BINARY, VARBINARY, TIME, DATETIME, TIMESTAMP…  exactly one    TIME(3)
 *   INT…, DOUBLE, BOOLEAN, DATE, VARIANT, ARRAY…   no list at all
 * </pre>
 */
enum TypeParameterFamily {

    /** The character types and the FLOAT spellings: empty parentheses or one parameter. */
    NONE_OR_ONE(0, 1, false),
    /** NUMBER and its spellings: a precision, or a precision and a scale. */
    ONE_OR_TWO(1, 2, false),
    /** The types of one parameter: a length or a fractional-seconds precision. */
    ONE(1, 1, false),
    /** The types that take no parentheses of this kind at all, an empty pair included. */
    NO_LIST(0, 0, true),
    /** A type whose list is judged elsewhere: up to two parameters, as the list itself reads. */
    UNJUDGED(0, 2, false);

    private final int fewest;
    private final int most;
    private final boolean refusesItsParentheses;

    TypeParameterFamily(final int fewest, final int most, final boolean refusesItsParentheses) {
        this.fewest = fewest;
        this.most = most;
        this.refusesItsParentheses = refusesItsParentheses;
    }

    /** The fewest parameters the list may hold. */
    int fewest() {
        return fewest;
    }

    /** The most parameters the list may hold. */
    int most() {
        return most;
    }

    /** Whether the type takes no list, so that whatever follows its opening parenthesis is refused. */
    boolean refusesItsParentheses() {
        return refusesItsParentheses;
    }

    /**
     * The family of the type a list follows.
     *
     * @param type the type's name, or null when the list follows none
     * @return the family
     */
    static TypeParameterFamily of(final FrostlakeParser.DataTypeNameContext type) {
        return type == null ? UNJUDGED : ofWord(type.getStart().getType());
    }

    /**
     * The family of the type whose name opens with a word.
     *
     * @param word the token type of the name's first word
     * @return the family, {@link #UNJUDGED} for a word that opens no type judged here
     */
    static TypeParameterFamily ofWord(final int word) {
        switch (word) {
            case FrostlakeParser.VARCHAR:
            case FrostlakeParser.STRING:
            case FrostlakeParser.TEXT:
            case FrostlakeParser.CHAR:
            case FrostlakeParser.CHARACTER:
            case FrostlakeParser.NCHAR:
            case FrostlakeParser.NVARCHAR:
            case FrostlakeParser.NVARCHAR2:
            case FrostlakeParser.VARCHAR2:
            case FrostlakeParser.FLOAT:
            case FrostlakeParser.FLOAT4:
            case FrostlakeParser.FLOAT8:
            case FrostlakeParser.REAL:
                return NONE_OR_ONE;
            case FrostlakeParser.NUMBER:
            case FrostlakeParser.DECIMAL:
            case FrostlakeParser.NUMERIC:
            case FrostlakeParser.DEC:
                return ONE_OR_TWO;
            case FrostlakeParser.BINARY:
            case FrostlakeParser.VARBINARY:
            case FrostlakeParser.TIME:
            case FrostlakeParser.DATETIME:
            case FrostlakeParser.TIMESTAMP:
            case FrostlakeParser.TIMESTAMP_NTZ:
            case FrostlakeParser.TIMESTAMPNTZ:
            case FrostlakeParser.TIMESTAMP_LTZ:
            case FrostlakeParser.TIMESTAMPLTZ:
            case FrostlakeParser.TIMESTAMP_TZ:
            case FrostlakeParser.TIMESTAMPTZ:
            case FrostlakeParser.GEOMETRY:
            case FrostlakeParser.DECFLOAT:
                return ONE;
            case FrostlakeParser.INTEGER:
            case FrostlakeParser.INT:
            case FrostlakeParser.BIGINT:
            case FrostlakeParser.SMALLINT:
            case FrostlakeParser.TINYINT:
            case FrostlakeParser.BYTEINT:
            case FrostlakeParser.DOUBLE:
            case FrostlakeParser.BOOLEAN:
            case FrostlakeParser.DATE:
            case FrostlakeParser.VARIANT:
            case FrostlakeParser.GEOGRAPHY:
            case FrostlakeParser.UUID:
            case FrostlakeParser.ARRAY:
            case FrostlakeParser.OBJECT:
            case FrostlakeParser.FILE:
                return NO_LIST;
            default:
                return UNJUDGED;
        }
    }
}
