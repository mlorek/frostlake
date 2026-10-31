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


package dev.frostlake.functions.scalar.hash;

/** SHA2_HEX(msg [, bits]) — SHA2 under another name: the lowercase hex SHA-2 digest of the message bytes. */
public class Sha2Hex extends Sha2 {
    public Sha2Hex() { super("SHA2_HEX"); }
}
