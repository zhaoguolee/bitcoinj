/*
 * Copyright 2018 bitcoinj-cash developers
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

import org.bitcoinj.params.Networks;
import org.bitcoinj.script.Script;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * This is a factory class that creates Address or CashAddress objects from strings.
 * It will create an Address object from Base58 strings or a CashAddress object from
 * cashaddr format strings.
 */
public class AddressFactory {

    public static AddressFactory create() {
        return new AddressFactory();
    }

    /**
     * Construct an address from a string representation.
     * @param params
     *            The expected NetworkParameters or null if you don't want validation.
     * @param plainAddress
     *            The textual form of the address, such as "17kzeh4N8g49GFvdDzSf8PjaPfyoD1MndL" or
     *            "bitcoincash:qpk4hk3wuxe2uqtqc97n8atzrrr6r5mleczf9sur4h"
     * @throws AddressFormatException
     *             if the given base58 doesn't parse or the checksum is invalid or the address
     *             is for the wrong network.
     */
    public Address getAddress(NetworkParameters params, String plainAddress) {
        try {
            return CashAddressFactory.create().getFromFormattedAddress(params, plainAddress);
        } catch (AddressFormatException x) {
            try {
                return fromString(params, plainAddress);
            } catch (AddressFormatException x2) {
                throw new AddressFormatException("Address " + plainAddress + " does not match cash (" + x.getMessage() + ") or legacy formats (" + x2.getMessage());
            }
        }
    }

    /**
     * Construct an address from its textual form.
     *
     * @param params
     *            the expected network this address is valid for, or null if the network should be derived from the
     *            textual form
     * @param str
     *            the textual form of the address, such as "17kzeh4N8g49GFvdDzSf8PjaPfyoD1MndL" or
     *            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
     * @return constructed address
     * @throws AddressFormatException
     *             if the given string doesn't parse or the checksum is invalid
     * @throws AddressFormatException.WrongNetwork
     *             if the given string is valid but not for the expected network (eg testnet vs mainnet)
     */
    public Address fromString(@Nullable NetworkParameters params, String str)
            throws AddressFormatException {
        if(Address.isValidLegacyAddress(params, str)) {
            return fromBase58(params, str);
        } else if(Address.isValidCashAddr(params, str)) {
            return CashAddressFactory.create().getFromFormattedAddress(params, str);
        } else {
            return null;
        }
    }

    public LegacyAddress fromCashAddress(NetworkParameters params, String cashAddr) {
        if(Address.isValidCashAddr(params, cashAddr)) {
            CashAddress cashAddress = CashAddressFactory.create().getFromFormattedAddress(params, cashAddr);
            if(cashAddress.isP2SHAddress()) {
                return fromScriptHash(params, cashAddress.getHash());
            } else {
                return fromPubKeyHash(params, cashAddress.getHash());
            }
        } else {
            throw new AddressFormatException("Invalid address!");
        }
    }

    /**
     * Construct a {@link LegacyAddress} that represents the given pubkey hash. The resulting address will be a P2PKH type of
     * address.
     *
     * @param params
     *            network this address is valid for
     * @param hash160
     *            20-byte pubkey hash
     * @return constructed address
     */
    public LegacyAddress fromPubKeyHash(NetworkParameters params, byte[] hash160) throws AddressFormatException {
        return new LegacyAddress(params, false, hash160);
    }

    /**
     * Construct a {@link LegacyAddress} that represents the public part of the given {@link ECKey}. Note that an address is
     * derived from a hash of the public key and is not the public key itself.
     *
     * @param params
     *            network this address is valid for
     * @param key
     *            only the public part is used
     * @return constructed address
     */
    public LegacyAddress fromKey(NetworkParameters params, ECKey key) {
        return fromPubKeyHash(params, key.getPubKeyHash());
    }

    /**
     * Construct a {@link LegacyAddress} that represents the given P2SH script hash.
     *
     * @param params
     *            network this address is valid for
     * @param hash160
     *            P2SH script hash
     * @return constructed address
     */
    public LegacyAddress fromScriptHash(NetworkParameters params, byte[] hash160) throws AddressFormatException {
        return new LegacyAddress(params, true, hash160);
    }

    /**
     * Construct a {@link LegacyAddress} from its base58 form.
     *
     * @param params
     *            expected network this address is valid for, or null if if the network should be derived from the
     *            base58
     * @param base58
     *            base58-encoded textual form of the address
     * @throws AddressFormatException
     *             if the given base58 doesn't parse or the checksum is invalid
     * @throws AddressFormatException.WrongNetwork
     *             if the given address is valid but for a different chain (eg testnet vs mainnet)
     */
    public LegacyAddress fromBase58(@Nullable NetworkParameters params, String base58)
            throws AddressFormatException, AddressFormatException.WrongNetwork {
        byte[] versionAndDataBytes = Base58.decodeChecked(base58);
        int version = versionAndDataBytes[0] & 0xFF;
        byte[] bytes = Arrays.copyOfRange(versionAndDataBytes, 1, versionAndDataBytes.length);
        if (params == null) {
            for (NetworkParameters p : Networks.get()) {
                if (version == p.getAddressHeader())
                    return new LegacyAddress(p, false, bytes);
                else if (version == p.getP2SHHeader())
                    return new LegacyAddress(p, true, bytes);
            }
            throw new AddressFormatException.InvalidPrefix("No network found for " + base58);
        } else {
            if (version == params.getAddressHeader())
                return new LegacyAddress(params, false, bytes);
            else if (version == params.getP2SHHeader())
                return new LegacyAddress(params, true, bytes);
            throw new AddressFormatException.WrongNetwork(version);
        }
    }

    /**
     * Construct an {@link Address} that represents the public part of the given {@link ECKey}.
     *
     * @param params
     *            network this address is valid for
     * @param key
     *            only the public part is used
     * @param outputScriptType
     *            script type the address should use
     * @return constructed address
     */
    public Address fromKey(final NetworkParameters params, final ECKey key, final Script.ScriptType outputScriptType) {
        if (outputScriptType == Script.ScriptType.P2PKH)
            return CashAddressFactory.create().getFromBase58(params, fromKey(params, key).toBase58());
        else
            throw new IllegalArgumentException(outputScriptType.toString());
    }
}
