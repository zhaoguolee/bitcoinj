package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.junit.Assert.*;

public class AddressConversionTests {

    @Test
    public void validateCashAddrs() {
        NetworkParameters params = MainNetParams.get();
        String legacyAddressP2PKH = "1MyZGR8HpaN8Hot5trvxUNALEW6E1haAuk";
        assertEquals("bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff", CashAddress.fromBase58(params, legacyAddressP2PKH).toString());

        String cashAddressP2PKH = "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff";
        String legacyAddress = LegacyAddress.fromCashAddress(params, cashAddressP2PKH).toString();
        assertEquals("1MyZGR8HpaN8Hot5trvxUNALEW6E1haAuk", legacyAddress);

        String legacyAddressP2SH = "36JVCGHDv8KaT3aMzrkS8Y2Qh2CHm8Z129";
        String cashAddressP2SH = CashAddress.fromBase58(params, legacyAddressP2SH).toString();
        System.out.println(cashAddressP2SH);
        assertEquals("bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz", cashAddressP2SH);

        System.out.println(CashAddress.fromCashAddress(params, "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff").getOutputScriptType());
        System.out.println(CashAddress.fromCashAddress(params, "bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz").getOutputScriptType());
        /*String cashAddress = "qzhr268ppgwtr36h20apc6ahzx6lsmlmcy40qejee0";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHNoPrefixValid));

        String cashAddrP2SHValid = "bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHValid));

        String cashAddrP2SHNoPrefixValid = "pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz";
        assertTrue(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHNoPrefixValid));

        String cashAddrP2PKHInvalid = "bitcoincash:qzhr268ppgwtr36h2smlmcy40qejeec";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHInvalid));

        String cashAddrP2PKHNoPrefixInvalid = "qzhr268ppgwtr36xclsmlmcy40qejeec";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2PKHNoPrefixInvalid));

        String cashAddrP2SHInvalid = "bitcoincash:pqef5axxnrq853nc5cuyvvpusv2nz";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHInvalid));

        String cashAddrP2SHNoPrefixInvalid = "pqeft5ukkg9ew2fgt5axxncuyvvpusv2nz";
        assertFalse(Address.isValidCashAddr(MainNetParams.get(), cashAddrP2SHNoPrefixInvalid));*/
    }
}
