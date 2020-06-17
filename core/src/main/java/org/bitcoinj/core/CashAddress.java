package org.bitcoinj.core;

import com.github.kiulian.converter.AddressConverter;
import org.bitcoinj.params.Networks;
import org.bitcoinj.script.Script;

import java.util.Arrays;

public class CashAddress extends Address {
    private byte[] hash160;
    private String cashAddress;
    private LegacyAddress legacyAddress;
    private Script.ScriptType addressType;

    private CashAddress(NetworkParameters params, byte[] hash160, String cashAddress, LegacyAddress legacyAddress) {
        super(params, hash160);
        this.hash160 = hash160;
        this.cashAddress = cashAddress;
        this.legacyAddress = legacyAddress;
        byte[] versionAndDataBytes = Base58.decodeChecked(legacyAddress.toBase58());
        int version = versionAndDataBytes[0] & 0xFF;
        if (version == params.getAddressHeader()) {
            this.addressType = Script.ScriptType.P2PKH;
        } else if (version == params.getP2SHHeader()) {
            this.addressType = Script.ScriptType.P2SH;
        }
    }

    public static CashAddress fromCashAddress(NetworkParameters params, String cashAddr) {
        String legacy = AddressConverter.toLegacyAddress(cashAddr);
        try {
            LegacyAddress legacyAddress = LegacyAddress.fromBase58(params, legacy);

            if (Address.isValidLegacyAddress(params, legacy)) {
                return new CashAddress(params, legacyAddress.getHash(), cashAddr, legacyAddress);
            } else {
                throw new AddressFormatException("Invalid address!");
            }
        } catch(Exception e) {
            throw e;
        }
    }

    public static CashAddress fromBase58(NetworkParameters params, String legacy) {
        if(Address.isValidLegacyAddress(params, legacy)) {
            String cashAddr = AddressConverter.toCashAddress(legacy);
            return fromCashAddress(params, cashAddr);
        } else {
            throw new AddressFormatException("Invalid address!");
        }
    }

    public LegacyAddress getLegacyAddress() {
        return this.legacyAddress;
    }

    @Override
    public String toString() {
        return this.cashAddress;
    }

    @Override
    public byte[] getHash() {
        return hash160;
    }

    @Override
    public Script.ScriptType getOutputScriptType() {
        return addressType;
    }
}
