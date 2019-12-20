package org.bitcoinj.kits;

import com.google.common.collect.ImmutableList;
import com.subgraph.orchid.encoders.Hex;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.net.SlpDbProcessor;
import org.bitcoinj.net.SlpDbTokenDetails;
import org.bitcoinj.net.SlpDbValidTransaction;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SlpAppKit {
    private Wallet wallet;
    private SPVBlockStore spvBlockStore;
    private BlockChain blockChain;
    private PeerGroup peerGroup;
    private File walletFile;
    private File tokensFile;

    private long MIN_DUST = 546L;
    private long OP_RETURN_NUM_BYTES_BASE = 55;
    private long QUANTITY_NUM_BYTES = 9;

    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<SlpUTXO>();
    private ArrayList<TransactionOutput> bchUtxos = new ArrayList<TransactionOutput>();
    private ArrayList<SlpToken> slpTokens = new ArrayList<SlpToken>();
    private ArrayList<SlpTokenBalance> slpBalances = new ArrayList<SlpTokenBalance>();
    private ArrayList<String> verifiedSlpTxs = new ArrayList<String>();

    private SlpDbProcessor slpDbProcessor;

    public SlpAppKit() {

    }

    public SlpAppKit(NetworkParameters params, File file) {
        this(params, new KeyChainGroup(params), file);
    }

    public SlpAppKit(NetworkParameters params, DeterministicSeed seed, File file) {
        this(params, new KeyChainGroup(params, seed), file);
    }

    public SlpAppKit(Wallet wallet, File file) {
        this.wallet = wallet;
        this.walletFile = file;
        this.completeSetupOfWallet();
    }

    private SlpAppKit(NetworkParameters params, KeyChainGroup keyChainGroup, File file) {
        wallet = new Wallet(params, keyChainGroup);
        this.walletFile = file;
        DeterministicKeyChain cachedChain = wallet.getActiveKeyChain();
        wallet.removeHDChain(wallet.getActiveKeyChain());
        wallet.addAndActivateHDChain(new DeterministicKeyChain(cachedChain.getSeed()) {
            @Override
            protected ImmutableList<ChildNumber> getAccountPath() {
                return BIP44_ACCOUNT_SLP_PATH;
            }
        });
        this.completeSetupOfWallet();
    }

    public SlpAppKit initialize(NetworkParameters params, File file, @Nullable DeterministicSeed seed) throws UnreadableWalletException {
        if(file.exists()) {
            return loadFromFile(file);
        } else {
            if(seed != null) {
                return new SlpAppKit(params, seed, file);
            } else {
                return new SlpAppKit(params, file);
            }
        }
    }

    private void saveTokens(ArrayList<SlpToken> slpTokens) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tokensFile.getName()), StandardCharsets.UTF_8))) {
            JSONArray json = new JSONArray();
            for(SlpToken slpToken : slpTokens) {
                JSONObject tokenObj = new JSONObject();
                tokenObj.put("tokenId", slpToken.getTokenId());
                tokenObj.put("ticker", slpToken.getTicker());
                tokenObj.put("decimals", slpToken.getDecimals());
                json.put(tokenObj);
            }
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadTokens() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(this.tokensFile.getName()));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String jsonString = sb.toString();
            JSONArray tokensJson = new JSONArray(jsonString);
            for(int x = 0; x < tokensJson.length(); x++) {
                JSONObject tokenObj = tokensJson.getJSONObject(x);
                String tokenId = tokenObj.getString("tokenId");
                String ticker = tokenObj.getString("ticker");
                int decimals = tokenObj.getInt("decimals");
                SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                this.slpTokens.add(slpToken);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveVerifiedTxs(ArrayList<String> recordedSlpTxs) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.walletFile.getName() + ".txs"), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            for(String txHash : recordedSlpTxs) {
                text.append(txHash).append("\n");
            }
            writer.write(text.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadRecordedTxs() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(this.walletFile.getName() + ".txs"));
            String line = br.readLine();
            while (line != null) {
                String txHash = line;
                this.verifiedSlpTxs.add(txHash);
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static SlpAppKit loadFromFile(File file, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        DeterministicKeyChain.setAccountPath(DeterministicKeyChain.BIP44_ACCOUNT_SLP_PATH);
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(file);
                Wallet wallet = Wallet.loadFromFileStream(stream, walletExtensions);
                return new SlpAppKit(wallet, file);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    public void startWallet(DownloadProgressTracker progressTracker) throws BlockStoreException, IOException {
        File chainFile = new File(this.walletFile.getName() + ".spvchain");
        spvBlockStore = new SPVBlockStore(this.wallet.getParams(), chainFile);
        blockChain = new BlockChain(this.wallet.getParams(), spvBlockStore);
        peerGroup = new PeerGroup(this.wallet.getParams(), blockChain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(this.wallet.getParams()));

        blockChain.addWallet(wallet);
        peerGroup.addWallet(wallet);
        wallet.autosaveToFile(this.walletFile, 5, TimeUnit.SECONDS, null);
        wallet.saveToFile(this.walletFile);

        peerGroup.start();

        if(progressTracker == null) {
            DownloadProgressTracker bListener = new DownloadProgressTracker() {
                @Override
                public void doneDownload() {
                    super.doneDownload();
                    System.out.println("blockchain downloaded");
                }
            };

            peerGroup.startBlockChainDownload(bListener);
        } else {
            peerGroup.startBlockChainDownload(progressTracker);
        }
    }

    public void startWallet() throws IOException, BlockStoreException {
        this.startWallet(null);
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens) throws InsufficientMoneyException {
        SlpAddress slpAddress = new SlpAddress(this.wallet.getParams(), slpDestinationAddress);
        String destinationAddr = slpAddress.toCashAddress();
        int tokenDecimals = this.getSlpToken(tokenId).getDecimals();
        long sendTokensRaw = BigDecimal.valueOf(numTokens).scaleByPowerOfTen(tokenDecimals).longValueExact();
        long sendSatoshi = this.MIN_DUST;

        ArrayList<SlpUTXO> tempSlpUtxos = new ArrayList<>();
        for(SlpUTXO slpUtxo : slpUtxos) {
            if(slpUtxo.getTokenId().equals(tokenId)) {
                tempSlpUtxos.add(slpUtxo);
            }
        }

        ArrayList<TransactionOutput> selectedUtxos = new ArrayList<>();
        ArrayList<SlpUTXO> selectedSlpUtxos = new ArrayList<>();
        long inputTokensRaw = 0L;
        long inputSatoshi = 0L;
        for(SlpUTXO tempSlpUtxo : tempSlpUtxos) {
            if(inputTokensRaw < sendTokensRaw) {
                selectedUtxos.add(tempSlpUtxo.getTxUtxo());
                selectedSlpUtxos.add(tempSlpUtxo);
                inputTokensRaw += BigDecimal.valueOf(tempSlpUtxo.getTokenAmount()).scaleByPowerOfTen(tokenDecimals).doubleValue();
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

        long propagationFixFee = 550L; //Helps avoid not having enough BCH for tx, and propagating tx
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

        SendRequest req = SendRequest.createSlpTransaction(this.wallet.getParams());
        req.shuffleOutputs = false;
        req.feePerKb = Coin.valueOf(1000L);
        req.utxos = selectedUtxos;
        SlpOpReturnOutput slpOpReturn = new SlpOpReturnOutput(tokenId, sendTokensRaw, changeTokens);
        req.tx.addOutput(Coin.ZERO, slpOpReturn.getScript());
        req.tx.addOutput(this.wallet.getParams().getMinNonDustOutput(), Address.fromCashAddr(this.wallet.getParams(), destinationAddr));

        if(inputTokensRaw > sendTokensRaw) {
            req.tx.addOutput(this.wallet.getParams().getMinNonDustOutput(), wallet.freshAddress(KeyChain.KeyPurpose.CHANGE));
        }

        if (changeSatoshi >= MIN_DUST) {
            req.tx.addOutput(Coin.valueOf(changeSatoshi), wallet.freshAddress(KeyChain.KeyPurpose.CHANGE));
        }

        Transaction tx = wallet.sendCoinsOffline(req);

        for(SlpUTXO slpUTXO : selectedSlpUtxos) {
            this.slpUtxos.remove(slpUTXO);
        }

        return tx;
    }

    public void broadcastSlpTransaction(Transaction tx) {
        for(Peer peer : this.peerGroup.getConnectedPeers()) {
            peer.sendMessage(tx);
        }
    }

    private void completeSetupOfWallet() {
        File txsDataFile = new File(this.walletFile.getName() + ".txs");
        if(txsDataFile.exists()) {
            this.loadRecordedTxs();
        }
        File tokenDataFile = new File(this.walletFile.getName() + ".tokens");
        this.tokensFile = tokenDataFile;
        if(tokenDataFile.exists()) {
            this.loadTokens();
        }

        this.wallet.setAcceptRiskyTransactions(true);
        this.slpDbProcessor = new SlpDbProcessor();
        this.wallet.allowSpendingUnconfirmedTransactions();
        this.wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                recalculateSlpUtxos();
            }
        });
        this.wallet.addCoinsSentEventListener(new WalletCoinsSentEventListener() {
            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                recalculateSlpUtxos();
            }
        });

        this.recalculateSlpUtxos();
    }

    private boolean isValidSlpTx(Transaction tx) {
        if (tx.getOutputs().get(0).getScriptPubKey().isOpReturn()) {
            ScriptChunk protocolChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(1);
            if(protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals("534c5000")) {
                    SlpDbValidTransaction validTxQuery = new SlpDbValidTransaction(tx.getHashAsString());
                    boolean valid = this.slpDbProcessor.isValidSlpTx(validTxQuery.getEncoded(), tx.getHashAsString());
                    if (valid) {
                        this.verifiedSlpTxs.add(tx.getHashAsString());
                        this.saveVerifiedTxs(this.verifiedSlpTxs);
                    }
                    return valid;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void recalculateSlpUtxos() {
        this.slpUtxos.clear();
        this.slpBalances.clear();
        for(TransactionOutput utxo : this.wallet.getUtxos()) {
            Transaction tx = utxo.getParentTransaction();

            //If tx is already a verified SLP tx, we can save time by avoiding contacting SLPDB
            if(this.verifiedSlpTxs.contains(tx.getHashAsString())) {
                this.processUtxo(utxo, tx);
            } else {
                //Not verified tx, doing now.
                boolean validSlp = this.isValidSlpTx(tx);
                if (validSlp) {
                    this.processUtxo(utxo, tx);
                }
            }
        }
    }

    private void processUtxo(TransactionOutput utxo, Transaction tx) {
        if(utxo.getValue().value == this.MIN_DUST) {
            String tokenId = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(4).data), StandardCharsets.UTF_8);
            int chunkPosition = utxo.getIndex() + 4;
            String tokenAmountHex = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(chunkPosition).data), StandardCharsets.UTF_8);
            long tokenAmountRaw = Long.parseLong(tokenAmountHex, 16);

            if (!this.tokenIsMapped(tokenId)) {
                SlpDbTokenDetails tokenQuery = new SlpDbTokenDetails(tokenId);
                JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());
                if (tokenData != null) {
                    int decimals = tokenData.getInt("decimals");
                    String ticker = tokenData.getString("ticker");

                    double tokenAmount = BigDecimal.valueOf(tokenAmountRaw).scaleByPowerOfTen(-decimals).doubleValue();
                    SlpUTXO slpUTXO = new SlpUTXO(tokenId, tokenAmount, utxo);
                    if (!this.tokenUtxoIsMapped(slpUTXO)) {
                        this.slpUtxos.add(slpUTXO);
                    }

                    SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                    this.slpTokens.add(slpToken);
                    this.saveTokens(slpTokens);

                    if (this.isBalanceRecorded(tokenId)) {
                        this.getTokenBalance(tokenId).addToBalance(tokenAmount);
                    } else {
                        this.slpBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
                    }
                }
            } else {
                SlpToken slpToken = this.getSlpToken(tokenId);
                int decimals = slpToken.getDecimals();

                double tokenAmount = BigDecimal.valueOf(tokenAmountRaw).scaleByPowerOfTen(-decimals).doubleValue();
                SlpUTXO slpUTXO = new SlpUTXO(tokenId, tokenAmount, utxo);
                if (!this.tokenUtxoIsMapped(slpUTXO)) {
                    this.slpUtxos.add(slpUTXO);
                }

                if (this.isBalanceRecorded(tokenId)) {
                    this.getTokenBalance(tokenId).addToBalance(tokenAmount);
                } else {
                    this.slpBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
                }
            }
        }
    }

    private long outputFee(long numOutputs) {
        return numOutputs * 34;
    }

    private long sizeInBytes(long numQuantities) {
        return OP_RETURN_NUM_BYTES_BASE + numQuantities * QUANTITY_NUM_BYTES;
    }

    private boolean isBalanceRecorded(String tokenId) {
        for(SlpTokenBalance tokenBalance : this.slpBalances) {
            if(tokenBalance.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    private SlpTokenBalance getTokenBalance(String tokenId) {
        for(SlpTokenBalance tokenBalance : this.slpBalances) {
            if(tokenBalance.getTokenId().equals(tokenId)) {
                return tokenBalance;
            }
        }

        return null;
    }

    private boolean tokenIsMapped(String tokenId) {
        for(SlpToken slpToken : this.slpTokens) {
            if(slpToken.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    private boolean tokenUtxoIsMapped(SlpUTXO slpUtxo) {
        for(SlpUTXO mappedUtxo : this.slpUtxos) {
            String mappedUtxoHash = mappedUtxo.getTxUtxo().getParentTransactionHash().toString();
            int mappedUtxoIndex = mappedUtxo.getTxUtxo().getIndex();
            String slpUtxoHash = slpUtxo.getTxUtxo().getParentTransactionHash().toString();
            int slpUtxoIndex = slpUtxo.getTxUtxo().getIndex();
            if(mappedUtxoHash.equals(slpUtxoHash) && mappedUtxoIndex == slpUtxoIndex) {
                return true;
            }
        }

        return false;
    }

    private boolean tokenUtxoIsMapped(TransactionOutput utxo) {
        for(SlpUTXO mappedUtxo : this.slpUtxos) {
            String mappedUtxoHash = mappedUtxo.getTxUtxo().getParentTransactionHash().toString();
            int mappedUtxoIndex = mappedUtxo.getTxUtxo().getIndex();
            String slpUtxoHash = utxo.getParentTransactionHash().toString();
            int slpUtxoIndex = utxo.getIndex();
            if(mappedUtxoHash.equals(slpUtxoHash) && mappedUtxoIndex == slpUtxoIndex) {
                return true;
            }
        }

        return false;
    }

    private SlpUTXO getSlpUtxo(String txHash, int index) {
        for(SlpUTXO slpUtxo : this.slpUtxos) {
            String utxoHash = slpUtxo.getTxUtxo().getParentTransactionHash().toString();
            int utxoIndex = slpUtxo.getTxUtxo().getIndex();
            if(utxoHash.equals(txHash) && utxoIndex == index) {
                return slpUtxo;
            }
        }

        return null;
    }

    public SlpToken getSlpToken(String tokenId) {
        for(SlpToken slpToken : this.slpTokens) {
            if(slpToken.getTokenId().equals(tokenId)) {
                return slpToken;
            }
        }

        return null;
    }

    public Wallet getWallet() {
        return this.wallet;
    }

    public SPVBlockStore getSpvBlockStore() {
        return this.spvBlockStore;
    }

    public BlockChain getBlockchain() {
        return this.blockChain;
    }

    public PeerGroup getPeerGroup() {
        return this.peerGroup;
    }

    public ArrayList<SlpTokenBalance> getSlpBalances() {
        return this.slpBalances;
    }

    public SlpAddress currentSlpReceiveAddress() {
        return SlpAddress.fromCashAddr(this.wallet.getParams(), this.wallet.currentReceiveAddress().toString());
    }

    public SlpAddress freshSlpReceiveAddress() {
        return SlpAddress.fromCashAddr(this.wallet.getParams(), this.wallet.freshReceiveAddress().toString());
    }
}
