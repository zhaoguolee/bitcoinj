package org.bitcoinj.examples;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;

import java.io.File;
import java.io.IOException;

public class SLPWalletSendToken {
    public static void main(String[] args) throws Exception {
        //Create wallet and let bitcoinj generate a seed.
        NetworkParameters params = MainNetParams.get();
        SlpAppKit slpAppKit = new SlpAppKit().initialize(params, new File("."), "wallet_test", null);
        slpAppKit.startWallet();
        System.out.println("Current SLP receiving address: " + slpAppKit.currentSlpReceiveAddress().toString());
        System.out.println("Current BCH receiving address: " + slpAppKit.currentSlpReceiveAddress().toCashAddress());


        //You would need to call this manually later on, as this wallet has no SLP tokens:
        Transaction tx = slpAppKit.createSlpTransaction("simpleledger:qrgeeckxe4yel66mxmhpc230quajk8axr5vuta405n", "fa6c74c52450fc164e17402a46645ce494a8a8e93b1383fa27460086931ef59f", 1);
        slpAppKit.broadcastSlpTransaction(tx);
    }
}
