package org.bitcoinj.core;

import com.github.kiulian.converter.AddressConverter;
import org.bitcoinj.script.Script;

public class CashAddress extends Address {
    private byte[] hash160;
    private String cashAddress;
    private LegacyAddress legacyAddress;

    private CashAddress(NetworkParameters params, byte[] hash160, String cashAddress, LegacyAddress legacyAddress) {
        super(params, hash160);
        this.hash160 = hash160;
        this.cashAddress = cashAddress;
        this.legacyAddress = legacyAddress;
    }

    public static CashAddress fromCashAddress(NetworkParameters params, String cashAddr) {
        String legacy = AddressConverter.toLegacyAddress(cashAddr);
        try {
            LegacyAddress legacyAddress = LegacyAddress.fromBase58(params, legacy);

            if (Address.isValidLegacyAddress(params, legacy)) {
                return new CashAddress(params, legacyAddress.getHash(), cashAddr, legacyAddress);
            } else {
                return null;
            }
        } catch(Exception e) {
            throw e;
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
        return Script.ScriptType.P2PKH;
    }
}
