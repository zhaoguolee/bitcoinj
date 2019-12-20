package org.bitcoinj.examples;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.UnreadableWalletException;

import java.io.File;
import java.io.IOException;

public class CreateSLPWallet {
    public static void main(String[] args) throws UnreadableWalletException, InterruptedException, IOException, BlockStoreException {
        NetworkParameters params = MainNetParams.get();
        SlpAppKit slpAppKit = new SlpAppKit().initialize(params, new File("slp.wallet"), null);
        slpAppKit.startWallet();
        System.out.println("Current SLP receiving address: " + slpAppKit.currentSlpReceiveAddress().toString());
        System.out.println("Current BCH receiving address: " + slpAppKit.currentSlpReceiveAddress().toCashAddress());
    }
}
