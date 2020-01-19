package org.bitcoinj.examples;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SlpToken;
import org.bitcoinj.core.SlpTokenBalance;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.params.MainNetParams;

import java.io.File;

public class SLPWalletListBalances {
    public static void main(String[] args) throws Exception {
        //Create wallet and let bitcoinj generate a seed.
        NetworkParameters params = MainNetParams.get();
        SlpAppKit slpAppKit = new SlpAppKit().initialize(params, new File("."), "wallet_test", null);
        slpAppKit.startWallet();

        for(SlpTokenBalance tokenBalance : slpAppKit.getSlpBalances()) {
            SlpToken slpToken = slpAppKit.getSlpToken(tokenBalance.getTokenId());
            //The wallet might not have the SLP token saved yet. So we check if it's null or not.
            if(slpToken != null) {
                System.out.println(slpToken.getTicker() + " > " + tokenBalance.getTokenId() + " > " + tokenBalance.getBalance());
            } else {
                System.out.println(tokenBalance.getTokenId() + " > " + tokenBalance.getBalance());
            }
        }
    }
}
