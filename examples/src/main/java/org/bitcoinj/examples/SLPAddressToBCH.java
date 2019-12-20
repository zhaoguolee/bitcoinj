package org.bitcoinj.examples;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SlpAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.params.MainNetParams;

import java.io.File;

public class SLPAddressToBCH {
    public static void main(String[] args) throws Exception {
        //First, we need an SLP address.
        SlpAddress slpAddress = new SlpAddress(MainNetParams.get(), "simpleledger:qrgeeckxe4yel66mxmhpc230quajk8axr5vuta405n");

        //Then, we convert it to a BCH address.
        String bchAddress = slpAddress.toCashAddress();
        System.out.println(bchAddress);
    }
}
