package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.ChildNumber;

import java.security.SecureRandom;
import java.util.ArrayList;

public class SlpWallet extends Wallet {
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<SlpUTXO>();
    private ArrayList<TransactionOutput> bchUtxos = new ArrayList<TransactionOutput>();

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

    public void sendToken(String slpDestinationAddress, String tokenId, long tokenAmount) throws InsufficientMoneyException {
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

        //TODO convert SLP address to normal cashaddr so it fucking works
        SendRequest req = SendRequest.createSlpTransaction(this.params, slpDestinationAddress, this.params.getMinNonDustOutput());

        //TODO add OP_RETURN

        //TODO add token change output

        //TODO add BCH change output

        for(SlpUTXO selectedUtxo : selectedSlpUtxos) {
            req.tx.addInput(selectedUtxo.getTxUtxo());
        }

        //TODO add BCH input

        Transaction tx = this.sendCoinsOffline(req);
    }
}
