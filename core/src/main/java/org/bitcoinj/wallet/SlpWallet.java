package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import com.subgraph.orchid.encoders.Hex;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
        this.wallet.allowSpendingUnconfirmedTransactions();
        for(Transaction tx : wallet.getRecentTransactions(0, false)) {
            if(tx.getValue(wallet).isPositive()) {
                if(tx.getValue(wallet).value == MIN_DUST) {
                    if(tx.getOutputs().get(0).getScriptPubKey().isOpReturn()) {
                        //4de69e374a8ed21cbddd47f2338cc0f479dc58daa2bbe11cd604ca488eca0ddf
                        String tokenId = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(4).data), StandardCharsets.UTF_8);
                        if(tokenId.equals("532fca8907107e199b89fa4b1691350edf595ee7d6fb3d053746e3b07cab568c")) {
                            TransactionOutput myOutput = getMyOutput(tx);
                            int chunkPosition = myOutput.getIndex() + 4;
                            String tokenAmountHex = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(chunkPosition).data), StandardCharsets.UTF_8);
                            long tokenAmountRaw = Long.parseLong(tokenAmountHex, 16);
                            System.out.println("Token amount " + tokenAmountRaw);
                            double tokenAmount = tokenAmountRaw / 100000000D;
                            System.out.println("Token amount " + tokenAmount);
                            System.out.println("Tx hash " + tx.getHashAsString());

                            slpUtxos.add(new SlpUTXO(tokenId, (long)tokenAmount, myOutput));
                        }
                    }
                }
            }
        }
    }

    private TransactionOutput getMyOutput(Transaction tx) {
        for(TransactionOutput output : tx.getOutputs()) {
            if(!output.getScriptPubKey().isOpReturn()) {
                byte[] hash160 = output.getScriptPubKey().getToAddress(this.wallet.params).getHash160();

                if (this.wallet.isPubKeyHashMine(hash160)) {
                    return output;
                }
            }
        }

        return null;
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

        try {
            this.sendToken("bitcoincash:qrz4kl5s0na247vv7mgz9zlrvyty6ne74vxz597kun", "532fca8907107e199b89fa4b1691350edf595ee7d6fb3d053746e3b07cab568c", 500L);
        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
        }
    }

    public void sendToken(String slpDestinationAddress, String tokenId, long numTokens) throws InsufficientMoneyException {
        long sendTokensRaw = BigDecimal.valueOf(numTokens).scaleByPowerOfTen(8).longValueExact();
        long sendSatoshi = this.MIN_DUST;

        ArrayList<SlpUTXO> tempSlpUtxos = new ArrayList<>();
        for(SlpUTXO slpUtxo : slpUtxos) {
            if(slpUtxo.getTokenId().equals(tokenId)) {
                tempSlpUtxos.add(slpUtxo);
            }
        }

        ArrayList<TransactionOutput> selectedUtxos = new ArrayList<>();
        long inputTokensRaw = 0L;
        long inputSatoshi = 0L;
        for(SlpUTXO tempSlpUtxo : tempSlpUtxos) {
            if(inputTokensRaw < sendTokensRaw) {
                selectedUtxos.add(tempSlpUtxo.getTxUtxo());
                inputTokensRaw += BigDecimal.valueOf(tempSlpUtxo.getTokenAmount()).scaleByPowerOfTen(8).longValueExact();
                inputSatoshi += (tempSlpUtxo.getTxUtxo().getValue().value - 148L); // Deduct input fee
            }
        }

        if (inputTokensRaw < sendTokensRaw) {
            throw new RuntimeException("insufficient token balance=" + inputTokensRaw);
        } else if (inputTokensRaw > sendTokensRaw) {
            // If there's token change we need at least another dust limit worth of BCH
            sendSatoshi += MIN_DUST;
        }

        for(TransactionOutput txOutput : wallet.getUtxos()) {
            /*
            When grabbing all BCH outputs that can be used for sending the SLP tx, we ignore utxos that
            are of 546 satoshis, as it is either an SLP utxo, or a dusting attack.
             */
            if(txOutput.getValue().value != MIN_DUST) {
                bchUtxos.add(txOutput);
            }
        }

        long propagationFixFee = 600L;
        long numOutputs = 3L; // Assume three outputs in addition to the op return.
        long numQuanitites = 2L; // Assume one token receiver and the token receiver
        long fee = this.outputFee(numOutputs) + this.sizeInBytes(numQuanitites) + propagationFixFee;

        // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
        for(TransactionOutput utxo : bchUtxos) {
            if(inputSatoshi <= (sendSatoshi + fee)) {
                selectedUtxos.add(utxo);
                inputSatoshi += utxo.getValue().value - 148L;
            }
        }

        long changeSatoshi = inputSatoshi - sendSatoshi - fee;
        if (changeSatoshi < 0) {
            throw new IllegalArgumentException("Insufficient BCH balance=" + inputSatoshi + " required " + sendSatoshi + " + fees");
        }

        long changeTokens = inputTokensRaw - sendTokensRaw;

        SendRequest req = SendRequest.createSlpTransaction(wallet.params);
        req.shuffleOutputs = false;
        req.feePerKb = Coin.valueOf(2000L);
        req.utxos = selectedUtxos;
        SlpOpReturnOutput slpOpReturn = new SlpOpReturnOutput(tokenId, sendTokensRaw, changeTokens);
        req.tx.addOutput(Coin.ZERO, slpOpReturn.getScript());

        //TODO convert SLP address to normal cashaddr so it fucking works
        req.tx.addOutput(wallet.params.getMinNonDustOutput(), Address.fromCashAddr(wallet.params, slpDestinationAddress));

        if(inputTokensRaw > sendTokensRaw) {
            req.tx.addOutput(wallet.params.getMinNonDustOutput(), wallet.freshAddress(KeyChain.KeyPurpose.CHANGE));
        }

        if (changeSatoshi >= MIN_DUST) {
            req.tx.addOutput(Coin.valueOf(changeSatoshi), wallet.freshAddress(KeyChain.KeyPurpose.CHANGE));
        }
/*
        for(TransactionOutput selectedUtxo : selectedUtxos) {
            req.tx.addInput(selectedUtxo);
        }
*/
        Transaction tx = wallet.sendCoinsOffline(req);
        System.out.println(new String(Hex.encode(tx.bitcoinSerialize()), StandardCharsets.UTF_8));
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
