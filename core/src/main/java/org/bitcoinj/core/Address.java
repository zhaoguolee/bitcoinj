/*
 * Copyright by the original author or authors.
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

import javax.annotation.Nullable;

import org.bitcoinj.core.bip47.BIP47PaymentCode;
import org.bitcoinj.core.slp.SlpAddress;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.Script.ScriptType;

/**
 * <p>
 * Base class for addresses, e.g. cash address ({@link CashAddress}) or legacy addresses ({@link LegacyAddress}).
 * </p>
 *
 */
public abstract class Address extends PrefixedChecksummedBytes {
    public Address(NetworkParameters params, byte[] bytes) {
        super(params, bytes);
    }

    /**
     *
     * @param params
     *             The expected NetworkParameters to validate the address against.
     * @param legacyAddress
     *             The Bitcoin Cash legacy address. Starts with a "1"
     * @return
     *             Whether the address is valid or not.
     */
    @Deprecated
    public static boolean isValidLegacyAddress(NetworkParameters params, String legacyAddress)
    {
        try {
            AddressFactory.create().fromBase58(params, legacyAddress);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public static boolean isValidSlpAddress(NetworkParameters params, String slpAddress)
    {
        try {
            SlpAddress.Util.AddressVersionAndBytes addrData = SlpAddress.Util.decode(params.getSimpleledgerPrefix(), slpAddress);
            new CashAddress(params, addrData.version, addrData.bytes);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public static boolean isValidCashAddr(NetworkParameters params, String cashAddress)
    {
        try {
            CashAddressFactory.create().getFromFormattedAddress(params, cashAddress);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public static boolean isValidPaymentCode(String paymentCode)
    {
        try {
            new BIP47PaymentCode(paymentCode);
            return true;
        } catch(AddressFormatException | NullPointerException e) {
            return false;
        }
    }

    public static boolean isValidPaymentCode(byte[] paymentCodeBytes)
    {
        try {
            BIP47PaymentCode paymentCode = new BIP47PaymentCode(paymentCodeBytes);
            new BIP47PaymentCode(paymentCode.toString());
            return true;
        } catch(AddressFormatException | NullPointerException e) {
            return false;
        }
    }

    public static boolean isAcceptableVersion(NetworkParameters params, int version) {
        for (int v : params.getAcceptableAddressCodes()) {
            if (version == v) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get either the public key hash or script hash that is encoded in the address.
     * 
     * @return hash that is encoded in the address
     */
    public abstract byte[] getHash();

    /**
     * Get the type of output script that will be used for sending to the address.
     * 
     * @return type of output script
     */
    public abstract ScriptType getOutputScriptType();
}
