/*
<<<<<<< HEAD
 * Copyright 2018 Hash Engineering
=======
 * Copyright 2018 bitcoinj-cash developers
>>>>>>> a26cc4d1... javadoc:  add documentation for public methods / add copyright info
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
package org.bchj.core.slp;

import org.bchj.core.*;
import org.bchj.script.Script;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * <p>A Bitcoin Cash address looks like bitcoincash:qpk4hk3wuxe2uqtqc97n8atzrrr6r5mleczf9sur4h and is derived from
 * an elliptic curve public key plus a set of network parameters. Not to be confused with a {@link PeerAddress}
 * or {@link AddressMessage} which are about network (TCP) addresses.</p>
 *
 * <p>A cash address is built by taking the RIPE-MD160 hash of the public key bytes, with a network prefix (bitcoincash:
 * for mainnet) a version and a checksum, then encoding it textually as cashaddr. The network prefix denotes the network
 * for which the address is valid (see {@link NetworkParameters}.  The version is to indicate how the bytes inside the
 * address should be interpreted. Whilst almost all addresses today are hashes of public keys, another type can contain
 * a hash of a script instead.</p>
 */

public class SlpAddress extends Address {

    public enum CashAddressType {
        PubKey(0),
        Script(1);

        private int value;

        CashAddressType(int value) {
            this.value = value;
        }

        public byte getValue() {
            return (byte) value;
        }
    }

    private CashAddressType addressType;

    static int getLegacyVersion(NetworkParameters params, CashAddressType type) {
        switch (type) {
            case PubKey:
                return params.getAddressHeader();
            case Script:
                return params.getP2SHHeader();
        }
        throw new AddressFormatException("Invalid Cash address type: " + type.value);
    }

    static CashAddressType getType(NetworkParameters params, int version) {
        if (version == params.getAddressHeader()) {
            return CashAddressType.PubKey;
        } else if (version == params.getP2SHHeader()) {
            return CashAddressType.Script;
        }
        throw new AddressFormatException("Invalid Cash address version: " + version);
    }

    public SlpAddress(NetworkParameters params, CashAddressType addressType, byte[] hash) {
        super(params, getLegacyVersion(params, addressType), hash);
        this.addressType = addressType;
    }

    public SlpAddress(NetworkParameters params, int version, byte[] hash160) {
        super(params, version, hash160);
        this.addressType = getType(params, version);
    }

    /**
     * Returns an Address that represents the given P2SH script hash.
     */
    public static SlpAddress fromP2PKHHash(NetworkParameters params, byte[] hash160) {
        return new SlpAddress(params, SlpAddress.CashAddressType.PubKey, hash160);
    }

    /**
     * Returns an Address that represents the given P2SH script hash.
     */
    public static SlpAddress fromP2SHHash(NetworkParameters params, byte[] hash160) {
        return new SlpAddress(params, SlpAddress.CashAddressType.Script, hash160);
    }

    /**
     * Returns an Address that represents the script hash extracted from the given scriptPubKey
     */
    public static SlpAddress fromP2SHScript(NetworkParameters params, Script scriptPubKey) {
        checkArgument(scriptPubKey.isPayToScriptHash(), "Not a P2SH script");
        return fromP2SHHash(params, scriptPubKey.getPubKeyHash());
    }

    /**
     * Returns true if this address is a Pay-To-Script-Hash (P2SH) address.
     * See also https://github.com/bitcoin/bips/blob/master/bip-0013.mediawiki: Address Format for pay-to-script-hash
     */
    public boolean isP2SHAddress() {
        return addressType == CashAddressType.Script;
    }

    public CashAddressType getAddressType() {
        return addressType;
    }

    public String toString() {
        return SlpAddressHelper.encodeCashAddress(getParameters().getSimpleledgerPrefix(),
                SlpAddressHelper.packAddressData(getHash160(), addressType.getValue()));
    }

    @Override
    public Address clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Given an address, examines the version byte and attempts to find a matching NetworkParameters. If you aren't sure
     * which network the address is intended for (eg, it was provided by a user), you can use this to decide if it is
     * compatible with the current wallet.
     *
     * @return a NetworkParameters of the address
     * @throws AddressFormatException if the string wasn't of a known version
     */
    public static NetworkParameters getParametersFromAddress(String address) throws AddressFormatException {
        try {
            return SlpAddressFactory.create().getFromFormattedAddress(null, address).getParameters();
        } catch (WrongNetworkException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }
}
