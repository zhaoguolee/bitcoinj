package org.bitcoinj.examples;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;

import java.io.File;
import java.io.IOException;

public class CreateSLPWallet {
    public static void main(String[] args) throws UnreadableWalletException, InterruptedException, IOException, BlockStoreException {
        //Create wallet and let bitcoinj generate a seed.
        NetworkParameters params = MainNetParams.get();
        SlpAppKit slpAppKit = new SlpAppKit().initialize(params, new File("slp.wallet"), null);
        slpAppKit.startWallet();
        System.out.println("Current SLP receiving address: " + slpAppKit.currentSlpReceiveAddress().toString());
        System.out.println("Current BCH receiving address: " + slpAppKit.currentSlpReceiveAddress().toCashAddress());

        //Create wallet but set our own seed.
        long walletBirthday = 1576815964L;
        SlpAppKit slpAppKitSeed = new SlpAppKit().initialize(params, new File("slpSeed.wallet"), new DeterministicSeed("witch collapse practice feed shame open despair creek road again ice least", null, "", walletBirthday));
        slpAppKitSeed.startWallet();
        System.out.println("Current SLP receiving address: " + slpAppKitSeed.currentSlpReceiveAddress().toString());
        System.out.println("Current BCH receiving address: " + slpAppKitSeed.currentSlpReceiveAddress().toCashAddress());
    }
}
