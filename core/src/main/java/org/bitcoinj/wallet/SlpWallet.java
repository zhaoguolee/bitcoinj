package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SlpUTXO;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.ChildNumber;

import java.security.SecureRandom;
import java.util.ArrayList;

public class SlpWallet extends Wallet {
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<SlpUTXO>();

    public SlpWallet(NetworkParameters params) {
        this(params, new KeyChainGroup(params));
    }

    public SlpWallet(NetworkParameters params, DeterministicSeed seed) {
        this(params, new KeyChainGroup(params, seed));
    }

    public SlpWallet(NetworkParameters params, KeyChainGroup keyChainGroup) {
        super(params, keyChainGroup);
        this.addAndActivateHDChain(new DeterministicKeyChain(new SecureRandom()) {
            @Override
            protected ImmutableList<ChildNumber> getAccountPath() {
                return BIP44_ACCOUNT_SLP_PATH;
            }
        });
        this.removeHDChainByIndex(0);
    }

    public void sendToken(String slpDestinationAddress, String tokenId, long tokenAmount) {
        ArrayList<SlpUTXO> tempSlpUtxos = new ArrayList<>();
        for(SlpUTXO slpUtxo : slpUtxos) {
            if(slpUtxo.getTokenId().equals(tokenId)) {
                tempSlpUtxos.add(slpUtxo);
            }
        }

        ArrayList<SlpUTXO> selectedSlpUtxos = new ArrayList<>();
        long selectedAmount = 0;
        for(SlpUTXO tempSlpUtxo : tempSlpUtxos) {
            if(selectedAmount < tokenAmount) {
                selectedSlpUtxos.add(tempSlpUtxo);
                selectedAmount += tempSlpUtxo.getTokenAmount();
            }
        }

        Transaction tx = new Transaction(this.params);
        tx.addOutput(this.params.getMinNonDustOutput(), Address.fromCashAddr(this.params, slpDestinationAddress));

        //TODO add token change output

        //TODO add BCH change output

        for(SlpUTXO selectedUtxo : selectedSlpUtxos) {
            tx.addInput(selectedUtxo.getTxUtxo());
        }

        //TODO add BCH input
    }
}
