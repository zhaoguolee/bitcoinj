package org.bitcoinj.kits;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import io.reactivex.Single;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.core.slp.*;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.SlpDbProcessor;
import org.bitcoinj.net.SlpDbTokenDetails;
import org.bitcoinj.net.SlpDbValidTransaction;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.protocols.payments.slp.SlpPaymentSession;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.*;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class SlpAppKit extends AbstractIdleService {
    private Wallet wallet;
    private SPVBlockStore spvBlockStore;
    private BlockChain blockChain;
    private PeerGroup peerGroup;
    protected PeerAddress[] peerAddresses;
    private File baseDirectory;
    private String walletName;
    private File walletFile;
    private File tokensFile;
    private DownloadProgressTracker progressTracker;
    private long MIN_DUST = 546L;
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<SlpUTXO>();
    private ArrayList<SlpToken> slpTokens = new ArrayList<SlpToken>();
    private ArrayList<SlpTokenBalance> slpBalances = new ArrayList<SlpTokenBalance>();
    private ArrayList<String> verifiedSlpTxs = new ArrayList<String>();
    private SlpDbProcessor slpDbProcessor;
    private InputStream checkpoints;
    private NetworkParameters params;
    private final String genesisTxType = "47454e45534953";
    private final String mintTxType = "4d494e54";
    private final String sendTxType = "53454e44";
    private boolean useTor = false;
    private String torProxyIp = "127.0.0.1";
    private String torProxyPort = "9050";

    @Nullable protected DeterministicSeed restoreFromSeed;
    @Nullable protected PeerDiscovery discovery;

    protected volatile Context context;

    public SlpAppKit() {

    }

    @Override
    protected void startUp() throws Exception {
        Context.propagate(context);
        this.startWallet();
    }

    @Override
    protected void shutDown() throws Exception {
        Context.propagate(context);
        this.saveTokens(this.slpTokens);
        this.peerGroup.stop();
        this.wallet.saveToFile(this.walletFile);
        this.spvBlockStore.close();

        this.peerGroup = null;
        this.wallet = null;
        this.spvBlockStore = null;
        this.blockChain = null;
    }

    public SlpAppKit(NetworkParameters params, File file, String walletName) {
        this(params, KeyChainGroup.builder(params, KeyChainGroupStructure.SLP).fromRandom(Script.ScriptType.P2PKH).build(), file, walletName);
    }

    public SlpAppKit(NetworkParameters params, DeterministicSeed seed, File file, String walletName) {
        this(params, KeyChainGroup.builder(params, KeyChainGroupStructure.SLP).fromSeed(seed, Script.ScriptType.P2PKH).build(), file, walletName);
    }

    public SlpAppKit(Wallet wallet, File file, String walletName) {
        this.wallet = wallet;
        this.params = this.wallet.getParams();
        this.context = new Context(this.wallet.getParams());
        this.baseDirectory = file;
        this.walletName = walletName;
        this.walletFile = new File(this.baseDirectory, walletName + ".wallet");
        this.completeSetupOfWallet();
    }

    private SlpAppKit(NetworkParameters params, KeyChainGroup keyChainGroup, File file, String walletName) {
        this.setupWallet(params, keyChainGroup, file, walletName);
    }

    private void setupWallet(NetworkParameters params, KeyChainGroup keyChainGroup, File file, String walletName) {
        wallet = new Wallet(params, keyChainGroup);
        this.context = new Context(params);
        this.params = params;
        this.baseDirectory = file;
        this.walletName = walletName;
        this.walletFile = new File(this.baseDirectory, walletName + ".wallet");
        this.completeSetupOfWallet();
    }

    public SlpAppKit initialize(NetworkParameters params, File baseDir, String walletName, @Nullable DeterministicSeed seed) throws UnreadableWalletException {
        File tmpWalletFile = new File(baseDir, walletName + ".wallet");
        if(tmpWalletFile.exists()) {
            return loadFromFile(baseDir, walletName);
        } else {
            if(seed != null) {
                this.restoreFromSeed = seed;
                return new SlpAppKit(params, seed, baseDir, walletName);
            } else {
                return new SlpAppKit(params, baseDir, walletName);
            }
        }
    }

    private void saveTokens(ArrayList<SlpToken> slpTokens) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.baseDirectory, tokensFile.getName())), StandardCharsets.UTF_8))) {
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
            FileInputStream is = new FileInputStream(new File(this.baseDirectory, this.tokensFile.getName()));
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String jsonString = sb.toString();

            try {
                JSONArray tokensJson = new JSONArray(jsonString);
                for (int x = 0; x < tokensJson.length(); x++) {
                    JSONObject tokenObj = tokensJson.getJSONObject(x);
                    String tokenId = tokenObj.getString("tokenId");
                    String ticker = tokenObj.getString("ticker");
                    int decimals = tokenObj.getInt("decimals");
                    SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                    if(!this.tokenIsMapped(tokenId)) {
                        this.slpTokens.add(slpToken);
                    }
                }
            } catch (Exception e) {
                this.slpTokens = new ArrayList<>();
            }
        } catch (Exception e) {
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
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.baseDirectory, this.walletName + ".txs")), StandardCharsets.UTF_8))) {
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
            br = new BufferedReader(new FileReader(new File(this.baseDirectory, this.walletName + ".txs")));
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

    private static SlpAppKit loadFromFile(File baseDir, String walletName, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(new File(baseDir, walletName + ".wallet"));
                Wallet wallet = Wallet.loadFromFileStream(stream, DeterministicKeyChain.BIP44_ACCOUNT_SLP_PATH, walletExtensions);
                return new SlpAppKit(wallet, baseDir, walletName);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    public void startWallet() throws BlockStoreException, IOException {
        File chainFile = new File(this.baseDirectory, this.walletName + ".spvchain");
        boolean chainFileExists = chainFile.exists();
        this.spvBlockStore = new SPVBlockStore(this.wallet.getParams(), chainFile);
        if (!chainFileExists || this.restoreFromSeed != null) {
            if (this.checkpoints == null && !Utils.isAndroidRuntime()) {
                this.checkpoints = CheckpointManager.openStream(params);
            }

            if (this.checkpoints != null) {
                // Initialize the chain file with a checkpoint to speed up first-run sync.
                long time;
                if (this.restoreFromSeed != null) {
                    time = this.restoreFromSeed.getCreationTimeSeconds();
                    if (chainFileExists) {
                        this.spvBlockStore.close();
                        if (!chainFile.delete())
                            throw new IOException("Failed to delete chain file in preparation for restore.");
                        this.spvBlockStore = new SPVBlockStore(params, chainFile);
                    }
                } else {
                    time = this.wallet.getEarliestKeyCreationTime();
                }
                if (time > 0) {
                    CheckpointManager.checkpoint(params, checkpoints, this.spvBlockStore, time);
                }
            } else if (chainFileExists) {
                this.spvBlockStore.close();
                if (!chainFile.delete())
                    throw new IOException("Failed to delete chain file in preparation for restore.");
                this.spvBlockStore = new SPVBlockStore(params, chainFile);
            }
        }

        this.blockChain = new BlockChain(this.wallet.getParams(), this.spvBlockStore);
        if(useTor) {
            System.setProperty("socksProxyHost", torProxyIp);
            System.setProperty("socksProxyPort", torProxyPort);
            this.peerGroup = new PeerGroup(this.wallet.getParams(), this.blockChain, new BlockingClientManager());
        } else {
            this.peerGroup = new PeerGroup(this.wallet.getParams(), this.blockChain);
        }

        if (peerAddresses != null) {
            for (PeerAddress addr : peerAddresses) this.peerGroup.addAddress(addr);
            this.peerGroup.setMaxConnections(peerAddresses.length);
            peerAddresses = null;
        } else if (!params.getId().equals(NetworkParameters.ID_REGTEST)) {
            this.peerGroup.addPeerDiscovery(new DnsDiscovery(this.wallet.getParams()));
        }

        this.blockChain.addWallet(this.wallet);
        this.peerGroup.addWallet(this.wallet);
        this.wallet.autosaveToFile(new File(this.baseDirectory, this.walletName + ".wallet"), 5, TimeUnit.SECONDS, null);
        this.wallet.saveToFile(new File(this.baseDirectory, this.walletName + ".wallet"));

        Futures.addCallback(peerGroup.startAsync(), new FutureCallback() {
            @Override
            public void onSuccess(@Nullable Object result) {
                final DownloadProgressTracker l = progressTracker == null ? new DownloadProgressTracker() : progressTracker;
                peerGroup.startBlockChainDownload(l);
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);

            }
        }, MoreExecutors.directExecutor());

        new Thread() {
            @Override
            public void run() {
                recalculateSlpUtxos();
            }
        }.start();
    }

    public SlpAppKit setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Closeables.closeQuietly(checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    public void setUseTor(boolean status) {
        this.useTor = status;
    }

    public void setTorProxyIp(String ip) {
        this.torProxyIp = ip;
    }

    public void setTorProxyPort(String port) {
        this.torProxyPort = port;
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        return this.createSlpTransaction(slpDestinationAddress, tokenId, numTokens, aesKey, true);
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens, @Nullable KeyParameter aesKey, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildTx(tokenId, numTokens, slpDestinationAddress, this, aesKey, allowUnconfirmed).blockingGet();
    }

    public Transaction createSlpTransactionBip70(String tokenId, @Nullable KeyParameter aesKey, List<Long> rawTokens, List<String> addresses, SlpPaymentSession paymentSession) throws InsufficientMoneyException {
        return this.createSlpTransactionBip70(tokenId, aesKey, rawTokens, addresses, paymentSession, true);
    }

    public Transaction createSlpTransactionBip70(String tokenId, @Nullable KeyParameter aesKey, List<Long> rawTokens, List<String> addresses, SlpPaymentSession paymentSession, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildTxBip70(tokenId, this, aesKey, rawTokens, addresses, paymentSession, allowUnconfirmed).blockingGet();
    }

    public Transaction createSlpGenesisTransaction(String ticker, String name, String url, int decimals, long tokenQuantity, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        SendRequest req = SendRequest.createSlpTransaction(this.wallet.getParams());
        req.aesKey = aesKey;
        req.shuffleOutputs = false;
        req.feePerKb = Coin.valueOf(1000L);
        SlpOpReturnOutputGenesis slpOpReturn = new SlpOpReturnOutputGenesis(ticker, name, url, decimals, tokenQuantity);
        req.tx.addOutput(Coin.ZERO, slpOpReturn.getScript());
        req.tx.addOutput(this.wallet.getParams().getMinNonDustOutput(), CashAddress.fromCashAddress(this.wallet.getParams(), this.wallet.currentChangeAddress().toString()));
        return wallet.sendCoinsOffline(req);
    }

    public void broadcastSlpTransaction(Transaction tx) {
        for(Peer peer : this.peerGroup.getConnectedPeers()) {
            peer.sendMessage(tx);
        }
    }

    private void completeSetupOfWallet() {
        File txsDataFile = new File(this.baseDirectory, this.walletName + ".txs");
        if(txsDataFile.exists()) {
            this.loadRecordedTxs();
        }
        File tokenDataFile = new File(this.baseDirectory, this.walletName + ".tokens");
        this.tokensFile = tokenDataFile;
        if(tokenDataFile.exists()) {
            this.loadTokens();
        }

        this.wallet.setAcceptRiskyTransactions(true);
        this.slpDbProcessor = new SlpDbProcessor();
    }

    private boolean isValidSlpTx(Transaction tx) {
        if (tx.getOutputs().get(0).getScriptPubKey().isOpReturn()) {
            ScriptChunk protocolChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(1);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals("534c5000")) {
                    ScriptChunk tokenTypeChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(2);
                    if (tokenTypeChunk != null) {
                        String tokenType = new String(Hex.encode(tokenTypeChunk.data), StandardCharsets.UTF_8);
                        if (tokenType.equals("01")) {
                            SlpDbValidTransaction validTxQuery = new SlpDbValidTransaction(tx.getHashAsString());
                            boolean valid = this.slpDbProcessor.isValidSlpTx(validTxQuery.getEncoded(), tx.getHashAsString());
                            if (valid) {
                                this.verifiedSlpTxs.add(tx.getHashAsString());
                                this.saveVerifiedTxs(this.verifiedSlpTxs);
                            }
                            return valid;
                        }
                    }
                }
            }
        }

        return false;
    }

    public String getSlpTxType(Transaction tx) {
        if (tx.getOutputs().get(0).getScriptPubKey().isOpReturn()) {
            ScriptChunk protocolChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(1);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals("534c5000")) {
                    ScriptChunk slpTxTypeChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(3);
                    if (slpTxTypeChunk != null) {
                        String txType = new String(Hex.encode(slpTxTypeChunk.data), StandardCharsets.UTF_8);
                        return txType;
                    }
                }
            }
        }

        return null;
    }

    public void recalculateSlpUtxos() {
        this.slpUtxos.clear();
        this.slpBalances.clear();
        List<TransactionOutput> utxos = this.wallet.calculateAllSpendCandidates(false, true, true);
        for (Iterator<TransactionOutput> iterator = utxos.iterator(); iterator.hasNext();) {
            TransactionOutput utxo = iterator.next();
            Transaction tx = utxo.getParentTransaction();

            //If tx is already a verified SLP tx, we can save time by avoiding contacting SLPDB
            if(this.verifiedSlpTxs.contains(tx.getHashAsString())) {
                this.determineUtxoThenMaybeProcess(tx, utxo);
            } else {
                //Not verified tx, doing now.
                boolean validSlp = this.isValidSlpTx(tx);
                if (validSlp) {
                    this.determineUtxoThenMaybeProcess(tx, utxo);
                }
            }
        }
    }

    private void determineUtxoThenMaybeProcess(Transaction tx, TransactionOutput utxo) {
        String slpTxType = this.getSlpTxType(tx);

        switch (slpTxType) {
            case genesisTxType:  // GENESIS
            case mintTxType:  // MINT
                if (!this.utxoIsMintBaton(tx, utxo, slpTxType)) {
                    this.processGenesisOrMintUtxo(utxo, tx, slpTxType);
                }
                break;
            case sendTxType:  // SEND
                this.processUtxo(utxo, tx);
                break;
        }
    }

    private boolean utxoIsMintBaton(Transaction tx, TransactionOutput utxo, String slpTxType) {
        int mintingBatonVout = 0;
        int utxoVout = utxo.getIndex();
        int chunkIndex = 0;

        if(slpTxType.equals(genesisTxType)) { // GENESIS
            chunkIndex = 9;
        } else if(slpTxType.equals(mintTxType)) { // MINT
            chunkIndex = 5;
        }

        ScriptChunk mintBatonVoutChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(chunkIndex);
        if (mintBatonVoutChunk != null) {
            String voutHex = new String(Hex.encode(mintBatonVoutChunk.data), StandardCharsets.UTF_8);

            if(!voutHex.equals("")) {
                mintingBatonVout = Integer.parseInt(voutHex, 16);
            } else {
                return false;
            }
        }

        return mintingBatonVout == utxoVout;
    }

    private void processUtxo(TransactionOutput utxo, Transaction tx) {
        if(utxo.getValue().value == this.MIN_DUST) {
            String tokenId = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(4).data), StandardCharsets.UTF_8);
            int chunkPosition = utxo.getIndex() + 4;
            String tokenAmountHex = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(chunkPosition).data), StandardCharsets.UTF_8);
            long tokenAmountRaw = Long.parseLong(tokenAmountHex, 16);

            ArrayList<SlpUTXO> slpUtxosToAdd = new ArrayList<>();
            ArrayList<SlpToken> slpTokensToAdd = new ArrayList<>();
            ArrayList<SlpTokenBalance> slpBalancesToAdd = new ArrayList<>();
            ArrayList<SlpTokenBalance> cachedTokenBalances = this.slpBalances;

            if (!this.tokenIsMapped(tokenId)) {
                SlpDbTokenDetails tokenQuery = new SlpDbTokenDetails(tokenId);
                JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());
                if (tokenData != null) {
                    int decimals = tokenData.getInt("decimals");
                    String ticker = tokenData.getString("ticker");

                    double tokenAmount = BigDecimal.valueOf(tokenAmountRaw).scaleByPowerOfTen(-decimals).doubleValue();
                    SlpUTXO slpUTXO = new SlpUTXO(tokenId, tokenAmount, utxo);
                    if (!this.tokenUtxoIsMapped(slpUTXO)) {
                        slpUtxosToAdd.add(slpUTXO);
                    }

                    SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                    slpTokensToAdd.add(slpToken);

                    if (this.isBalanceRecorded(tokenId)) {
                        this.getTokenBalance(cachedTokenBalances, tokenId).addToBalance(tokenAmount);
                    } else {
                        slpBalancesToAdd.add(new SlpTokenBalance(tokenId, tokenAmount));
                    }
                }
            } else {
                SlpToken slpToken = this.getSlpToken(tokenId);
                int decimals = slpToken.getDecimals();

                double tokenAmount = BigDecimal.valueOf(tokenAmountRaw).scaleByPowerOfTen(-decimals).doubleValue();
                SlpUTXO slpUTXO = new SlpUTXO(tokenId, tokenAmount, utxo);
                if (!this.tokenUtxoIsMapped(slpUTXO)) {
                    slpUtxosToAdd.add(slpUTXO);
                }

                if (this.isBalanceRecorded(tokenId)) {
                    this.getTokenBalance(cachedTokenBalances, tokenId).addToBalance(tokenAmount);
                } else {
                    slpBalancesToAdd.add(new SlpTokenBalance(tokenId, tokenAmount));
                }
            }
            this.slpUtxos.addAll(slpUtxosToAdd);
            this.slpBalances = cachedTokenBalances;
            this.slpBalances.addAll(slpBalancesToAdd);
            this.slpTokens.addAll(slpTokensToAdd);
            this.saveTokens(slpTokens);
        }
    }

    private void processGenesisOrMintUtxo(TransactionOutput utxo, Transaction tx, String slpTxType) {
        if(utxo.getValue().value == this.MIN_DUST) {
            String tokenId = "";
            int chunkPosition = 0;

            if(slpTxType.equals(genesisTxType)) {
                tokenId = tx.getHashAsString();
                chunkPosition = 10;
            } else if(slpTxType.equals(mintTxType)) {
                tokenId = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(4).data), StandardCharsets.UTF_8);
                chunkPosition = 6;
            }

            String tokenAmountHex = new String(Hex.encode(tx.getOutputs().get(0).getScriptPubKey().getChunks().get(chunkPosition).data), StandardCharsets.UTF_8);
            long tokenAmountRaw = Long.parseLong(tokenAmountHex, 16);

            ArrayList<SlpUTXO> slpUtxosToAdd = new ArrayList<>();
            ArrayList<SlpToken> slpTokensToAdd = new ArrayList<>();
            ArrayList<SlpTokenBalance> slpBalancesToAdd = new ArrayList<>();
            ArrayList<SlpTokenBalance> cachedTokenBalances = this.slpBalances;

            if (!this.tokenIsMapped(tokenId)) {
                SlpDbTokenDetails tokenQuery = new SlpDbTokenDetails(tokenId);
                JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());
                if (tokenData != null) {
                    int decimals = tokenData.getInt("decimals");
                    String ticker = tokenData.getString("ticker");

                    double tokenAmount = BigDecimal.valueOf(tokenAmountRaw).scaleByPowerOfTen(-decimals).doubleValue();
                    SlpUTXO slpUTXO = new SlpUTXO(tokenId, tokenAmount, utxo);
                    if (!this.tokenUtxoIsMapped(slpUTXO)) {
                        slpUtxosToAdd.add(slpUTXO);
                    }

                    SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                    slpTokensToAdd.add(slpToken);

                    if (this.isBalanceRecorded(tokenId)) {
                        this.getTokenBalance(cachedTokenBalances, tokenId).addToBalance(tokenAmount);
                    } else {
                        slpBalancesToAdd.add(new SlpTokenBalance(tokenId, tokenAmount));
                    }
                }
            } else {
                SlpToken slpToken = this.getSlpToken(tokenId);
                int decimals = slpToken.getDecimals();

                double tokenAmount = BigDecimal.valueOf(tokenAmountRaw).scaleByPowerOfTen(-decimals).doubleValue();
                SlpUTXO slpUTXO = new SlpUTXO(tokenId, tokenAmount, utxo);
                if (!this.tokenUtxoIsMapped(slpUTXO)) {
                    slpUtxosToAdd.add(slpUTXO);
                }

                if (this.isBalanceRecorded(tokenId)) {
                    this.getTokenBalance(cachedTokenBalances, tokenId).addToBalance(tokenAmount);
                } else {
                    slpBalancesToAdd.add(new SlpTokenBalance(tokenId, tokenAmount));
                }
            }
            this.slpUtxos.addAll(slpUtxosToAdd);
            this.slpBalances = cachedTokenBalances;
            this.slpBalances.addAll(slpBalancesToAdd);
            this.slpTokens.addAll(slpTokensToAdd);
            this.saveTokens(slpTokens);
        }
    }

    private boolean isBalanceRecorded(String tokenId) {
        for (Iterator<SlpTokenBalance> iterator = this.slpBalances.iterator(); iterator.hasNext();) {
            SlpTokenBalance tokenBalance = iterator.next();
            if(tokenBalance.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    private SlpTokenBalance getTokenBalance(ArrayList<SlpTokenBalance> tokenBalances, String tokenId) {
        for (Iterator<SlpTokenBalance> iterator = tokenBalances.iterator(); iterator.hasNext();) {
            SlpTokenBalance tokenBalance = iterator.next();
            if(tokenBalance.getTokenId().equals(tokenId)) {
                return tokenBalance;
            }
        }

        return null;
    }

    private boolean tokenIsMapped(String tokenId) {
        for (Iterator<SlpToken> iterator = this.slpTokens.iterator(); iterator.hasNext();) {
            SlpToken slpToken = iterator.next();
            if(slpToken.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    private boolean tokenUtxoIsMapped(SlpUTXO slpUtxo) {
        for (Iterator<SlpUTXO> iterator = this.slpUtxos.iterator(); iterator.hasNext();) {
            SlpUTXO mappedUtxo = iterator.next();
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
        for (Iterator<SlpUTXO> iterator = this.slpUtxos.iterator(); iterator.hasNext();) {
            SlpUTXO mappedUtxo = iterator.next();
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
        for (Iterator<SlpUTXO> iterator = this.slpUtxos.iterator(); iterator.hasNext();) {
            SlpUTXO slpUtxo = iterator.next();
            String utxoHash = slpUtxo.getTxUtxo().getParentTransactionHash().toString();
            int utxoIndex = slpUtxo.getTxUtxo().getIndex();
            if(utxoHash.equals(txHash) && utxoIndex == index) {
                return slpUtxo;
            }
        }

        return null;
    }

    public SlpToken getSlpToken(String tokenId) {
        for (Iterator<SlpToken> iterator = this.slpTokens.iterator(); iterator.hasNext();) {
            SlpToken slpToken = iterator.next();
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

    public void setDownloadProgressTracker(DownloadProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
    }
    public ArrayList<SlpTokenBalance> getSlpBalances() {
        return this.slpBalances;
    }
    public ArrayList<SlpToken> getSlpTokens() {
        return this.slpTokens;
    }
    public ArrayList<SlpUTXO> getSlpUtxos() {
        return this.slpUtxos;
    }

    public SlpAddress currentSlpReceiveAddress() {
        return SlpAddress.fromCashAddr(this.wallet.getParams(), this.wallet.currentReceiveAddress().toString());
    }

    public SlpAddress currentSlpChangeAddress() {
        return SlpAddress.fromCashAddr(this.wallet.getParams(), this.wallet.currentChangeAddress().toString());
    }

    public SlpAddress freshSlpReceiveAddress() {
        return SlpAddress.fromCashAddr(this.wallet.getParams(), this.wallet.freshReceiveAddress().toString());
    }

    public SlpAddress freshSlpChangeAddress() {
        return SlpAddress.fromCashAddr(this.wallet.getParams(), this.wallet.freshChangeAddress().toString());
    }

    public void setDiscovery(@Nullable PeerDiscovery discovery) {
        this.discovery = discovery;
    }

    public void setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
    }
}
