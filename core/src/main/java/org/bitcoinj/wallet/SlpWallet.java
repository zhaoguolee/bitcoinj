package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import javax.annotation.Nullable;
import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SlpWallet {
    private Wallet wallet;
    private long MIN_DUST = 546L;
    private long OP_RETURN_NUM_BYTES_BASE = 55;
    private long QUANTITY_NUM_BYTES = 9;

    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<SlpUTXO>();
    private ArrayList<TransactionOutput> bchUtxos = new ArrayList<TransactionOutput>();

    public SlpWallet(NetworkParameters params) {
        this(params, new KeyChainGroup(params));
    }

    public SlpWallet(NetworkParameters params, DeterministicSeed seed) {
        this(params, new KeyChainGroup(params, seed));
    }

    public SlpWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public SlpWallet(NetworkParameters params, KeyChainGroup keyChainGroup) {
        wallet = new Wallet(params, keyChainGroup);
        DeterministicKeyChain cachedChain = wallet.getActiveKeyChain();
        wallet.removeHDChain(wallet.getActiveKeyChain());
        wallet.addAndActivateHDChain(new DeterministicKeyChain(cachedChain.getSeed()) {
            @Override
            protected ImmutableList<ChildNumber> getAccountPath() {
                return BIP44_ACCOUNT_SLP_PATH;
            }
        });
    }

    public static SlpWallet loadFromFile(File file, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        DeterministicKeyChain.setAccountPath(DeterministicKeyChain.BIP44_ACCOUNT_SLP_PATH);
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(file);
                Wallet wallet = Wallet.loadFromFileStream(stream, walletExtensions);
                return new SlpWallet(wallet);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    public void startWallet() throws BlockStoreException, InterruptedException {
        File chainFile = new File("test.spvchain");
        SPVBlockStore chainStore = new SPVBlockStore(wallet.params, chainFile);
        BlockChain chain = new BlockChain(wallet.params, chainStore);
        PeerGroup peers = new PeerGroup(wallet.params, chain);
        peers.addPeerDiscovery(new DnsDiscovery(wallet.params));

        chain.addWallet(wallet);
        peers.addWallet(wallet);
        wallet.autosaveToFile(new File("test.wallet"), 5, TimeUnit.SECONDS, null);
        DownloadProgressTracker bListener = new DownloadProgressTracker() {
            @Override
            public void doneDownload() {
                System.out.println("blockchain downloaded");
            }
        };

        peers.start();
        peers.startBlockChainDownload(bListener);

        System.out.println(this.wallet.toString());
    }

    public void sendToken(String slpDestinationAddress, String tokenId, long tokenAmount) throws InsufficientMoneyException {
        long sendSatoshi = this.MIN_DUST;

        ArrayList<SlpUTXO> tempSlpUtxos = new ArrayList<>();
        for(SlpUTXO slpUtxo : slpUtxos) {
            if(slpUtxo.getTokenId().equals(tokenId)) {
                tempSlpUtxos.add(slpUtxo);
            }
        }

        ArrayList<TransactionOutput> selectedUtxos = new ArrayList<>();
        long selectedAmount = 0L;
        long inputSatoshi = 0L;
        for(SlpUTXO tempSlpUtxo : tempSlpUtxos) {
            if(selectedAmount < tokenAmount) {
                selectedUtxos.add(tempSlpUtxo.getTxUtxo());
                selectedAmount += tempSlpUtxo.getTokenAmount();
                inputSatoshi += (tempSlpUtxo.getTxUtxo().getValue().value - 148L); // Deduct input fee
            }
        }

        if (selectedAmount < tokenAmount) {
            throw new RuntimeException("insufficient token balance=$inputTokensRaw");
        } else if (selectedAmount > tokenAmount) {
            // If there's token change we need at least another dust limit worth of BCH
            sendSatoshi += MIN_DUST;
        }

        for(TransactionOutput txOutput : wallet.getUtxos()) {
            /*
            When grabbing all BCH outputs that can be used for sending the SLP tx, we ignore utxos that
            are of 546 satoshis, as it is either an SLP utxo, or a dusting attack.
             */
            if(txOutput.getValue() != wallet.params.getMinNonDustOutput()) {
                bchUtxos.add(txOutput);
            }
        }

        long propagationExtraFee = 50L; // When too close 1sat/byte tx's don't propagate well
        long numOutputs = 3L; // Assume three outputs in addition to the op return.
        long numQuanitites = 2L; // Assume one token receiver and the token receiver
        long fee = this.outputFee(numOutputs) + this.sizeInBytes(numQuanitites) + propagationExtraFee;

        long changeSatoshi = inputSatoshi - sendSatoshi - fee;
        if (changeSatoshi < 0) {
            throw new IllegalArgumentException("Insufficient BCH balance=$inputSatoshi required $sendSatoshi + fees");
        }

        // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
        for(TransactionOutput utxo : bchUtxos) {
            if(inputSatoshi <= (sendSatoshi + fee)) {
                selectedUtxos.add(utxo);
                inputSatoshi += utxo.getValue().value - 148L;
            }
        }

        long changeAmount = selectedAmount - tokenAmount;

        SendRequest req = SendRequest.createSlpTransaction(wallet.params);
        req.feePerKb = Coin.valueOf(1000L);

        SlpOpReturnOutput slpOpReturn = new SlpOpReturnOutput(tokenId, tokenAmount, changeAmount);
        req.tx.addOutput(Coin.ZERO, slpOpReturn.getScript());

        //TODO convert SLP address to normal cashaddr so it fucking works
        req.tx.addOutput(wallet.params.getMinNonDustOutput(), Address.fromCashAddr(wallet.params, slpDestinationAddress));

        if(selectedAmount > tokenAmount) {
            req.tx.addOutput(wallet.params.getMinNonDustOutput(), wallet.freshAddress(KeyChain.KeyPurpose.CHANGE));
        }

        if (changeSatoshi >= MIN_DUST) {
            req.tx.addOutput(Coin.valueOf(changeSatoshi), wallet.freshAddress(KeyChain.KeyPurpose.CHANGE));
        }

        for(TransactionOutput selectedUtxo : selectedUtxos) {
            req.tx.addInput(selectedUtxo);
        }

        Transaction tx = wallet.sendCoinsOffline(req);

    }

    private long outputFee(long numOutputs) {
        return numOutputs * 34;
    }

    private long sizeInBytes(long numQuantities) {
        return OP_RETURN_NUM_BYTES_BASE + numQuantities * QUANTITY_NUM_BYTES;
    }

    public Wallet getWallet() {
        return this.wallet;
    }
}
