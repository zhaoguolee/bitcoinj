/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Giannis Dzegoutanis
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core;

import java.util.Arrays;
import java.util.Objects;

import javax.annotation.Nullable;

import org.bitcoinj.params.Networks;
import org.bitcoinj.script.Script.ScriptType;

/**
 * <p>A Bitcoin address looks like 1MsScoe2fTJoq4ZPdQgqyhgWeoNamYPevy and is derived from an elliptic curve public key
 * plus a set of network parameters. Not to be confused with a {@link PeerAddress} or {@link AddressMessage}
 * which are about network (TCP) addresses.</p>
 *
 * <p>A standard address is built by taking the RIPE-MD160 hash of the public key bytes, with a version prefix and a
 * checksum suffix, then encoding it textually as base58. The version prefix is used to both denote the network for
 * which the address is valid (see {@link NetworkParameters}, and also to indicate how the bytes inside the address
 * should be interpreted. Whilst almost all addresses today are hashes of public keys, another (currently unsupported
 * type) can contain a hash of a script instead.</p>
 */
public class LegacyAddress extends Address {
    /**
     * An address is a RIPEMD160 hash of a public key, therefore is always 160 bits or 20 bytes.
     */
    public static final int LENGTH = 20;

    /** True if P2SH, false if P2PKH. */
    public final boolean p2sh;

    /**
     * 
     * @param params
     *            network this address is valid for
     * @param p2sh
     *            true if hash160 is hash of a script, false if it is hash of a pubkey
     * @param hash160
     *            20-byte hash of pubkey or script
     */
    public LegacyAddress(NetworkParameters params, boolean p2sh, byte[] hash160) throws AddressFormatException {
        super(params, hash160);
        if (hash160.length != 20)
            throw new AddressFormatException.InvalidDataLength(
                    "Legacy addresses are 20 byte (160 bit) hashes, but got: " + hash160.length);
        this.p2sh = p2sh;
    }

    /**
     * Get the version header of an address. This is the first byte of a base58 encoded address.
     * 
     * @return version header as one byte
     */
    public int getVersion() {
        return p2sh ? params.getP2SHHeader() : params.getAddressHeader();
    }

    /**
     * Returns the base58-encoded textual form, including version and checksum bytes.
     * 
     * @return textual form
     */
    public String toBase58() {
        return Base58.encodeChecked(getVersion(), bytes);
    }

    /** The (big endian) 20 byte hash that is the core of a Bitcoin address. */
    @Override
    public byte[] getHash() {
        return bytes;
    }

    /**
     * Get the type of output script that will be used for sending to the address. This is either
     * {@link ScriptType#P2PKH} or {@link ScriptType#P2SH}.
     * 
     * @return type of output script
     */
    @Override
    public ScriptType getOutputScriptType() {
        return p2sh ? ScriptType.P2SH : ScriptType.P2PKH;
    }

    /**
     * Given an address, examines the version byte and attempts to find a matching NetworkParameters. If you aren't sure
     * which network the address is intended for (eg, it was provided by a user), you can use this to decide if it is
     * compatible with the current wallet.
     * 
     * @return network the address is valid for
     * @throws AddressFormatException if the given base58 doesn't parse or the checksum is invalid
     */
    public static NetworkParameters getParametersFromAddress(String address) throws AddressFormatException {
        return AddressFactory.create().fromBase58(null, address).getParameters();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LegacyAddress other = (LegacyAddress) o;
        return super.equals(other) && this.p2sh == other.p2sh;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), p2sh);
    }

    @Override
    public String toString() {
        return toBase58();
    }

    @Override
    public LegacyAddress clone() throws CloneNotSupportedException {
        return (LegacyAddress) super.clone();
    }
}
