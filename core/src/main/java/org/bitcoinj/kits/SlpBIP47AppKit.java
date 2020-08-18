package org.bitcoinj.kits;

import com.google.common.io.Closeables;
import com.google.common.util.concurrent.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.bip47.BIP47Account;
import org.bitcoinj.core.bip47.BIP47Address;
import org.bitcoinj.core.bip47.BIP47Channel;
import org.bitcoinj.core.bip47.BIP47PaymentCode;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.slp.*;
import org.bitcoinj.crypto.BIP47SecretPoint;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.SlpDbProcessor;
import org.bitcoinj.net.SlpDbTokenDetails;
import org.bitcoinj.net.SlpDbValidTransaction;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.protocols.payments.slp.SlpPaymentSession;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BIP47Util;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.bip47.listeners.BlockchainDownloadProgressTracker;
import org.bitcoinj.wallet.bip47.listeners.TransactionEventListenerSlp;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.utils.BIP47Util.getReceiveAddress;
import static org.bitcoinj.utils.BIP47Util.getSendAddress;

public class SlpBIP47AppKit extends AbstractIdleService {
    public Wallet wallet;
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
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();
    private ArrayList<SlpToken> slpTokens = new ArrayList<>();
    private ArrayList<SlpTokenBalance> slpBalances = new ArrayList<>();
    private ArrayList<String> verifiedSlpTxs = new ArrayList<>();
    private SlpDbProcessor slpDbProcessor;
    private InputStream checkpoints;
    private NetworkParameters params;
    private final String genesisTxType = "47454e45534953";
    private final String mintTxType = "4d494e54";
    private final String sendTxType = "53454e44";
    private boolean useTor = false;
    private String torProxyIp = "127.0.0.1";
    private String torProxyPort = "9050";
    private boolean recalculatingTokens = false;

    @Nullable protected DeterministicSeed restoreFromSeed;
    @Nullable protected PeerDiscovery discovery;

    protected volatile Context context;

    // Support for BIP47-type accounts. Only one account is currently handled in this wallet.
    private List<BIP47Account> mAccounts = new ArrayList<BIP47Account>(1);

    // The progress tracker will callback the listener with a porcetage of the blockchain that it has downloaded, while downloading..
    private BlockchainDownloadProgressTracker mBlockchainDownloadProgressTracker;

    // This wallet allows one listener to be invoked when there are coins received and
    private TransactionEventListenerSlp mTransactionsEventListener = null;
    private Runnable onReceiveRunnable;
    private boolean mBlockchainDownloadStarted = false;

    // The payment channels indexed by payment codes.
    // A payment channel is created and saved if:
    //   - someone sends a notification transaction to this wallet's notifiction address
    //   - this wallet creates a notification transaction to a payment channel.
    //
    // It doesn't check if the notification transactions are mined before adding a payment code.
    // If you want to know a transaction's confidence, see #{@link Transaction.getConfidence()}
    private ConcurrentHashMap<String, BIP47Channel> bip47MetaData = new ConcurrentHashMap<String, BIP47Channel>();
    private static final Logger log = LoggerFactory.getLogger(SlpBIP47AppKit.class);

    public SlpBIP47AppKit() {

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

    public SlpBIP47AppKit(NetworkParameters params, File file, String walletName) {
        this(params, KeyChainGroup.builder(params, KeyChainGroupStructure.SLP).fromRandom(Script.ScriptType.P2PKH).build(), file, walletName);
    }

    public SlpBIP47AppKit(NetworkParameters params, DeterministicSeed seed, File file, String walletName) {
        this(params, KeyChainGroup.builder(params, KeyChainGroupStructure.SLP).fromSeed(seed, Script.ScriptType.P2PKH).build(), file, walletName);
    }

    public SlpBIP47AppKit(Wallet wallet, File file, String walletName) {
        this.wallet = wallet;
        this.params = this.wallet.getParams();
        this.context = new Context(this.wallet.getParams());
        this.baseDirectory = file;
        this.walletName = walletName;
        this.walletFile = new File(this.baseDirectory, walletName + ".wallet");
        this.completeSetupOfWallet();
    }

    private SlpBIP47AppKit(NetworkParameters params, KeyChainGroup keyChainGroup, File file, String walletName) {
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

    public SlpBIP47AppKit initialize(NetworkParameters params, File baseDir, String walletName, @Nullable DeterministicSeed seed) throws UnreadableWalletException {
        File tmpWalletFile = new File(baseDir, walletName + ".wallet");
        if(tmpWalletFile.exists()) {
            return loadFromFile(baseDir, walletName);
        } else {
            if(seed != null) {
                this.restoreFromSeed = seed;
                return new SlpBIP47AppKit(params, seed, baseDir, walletName);
            } else {
                return new SlpBIP47AppKit(params, baseDir, walletName);
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

    private JSONObject getJSONObject(String url) {
        InputStream is = null;
        try {
            is = new URL(url).openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            String jsonText = "{}";
            BufferedReader rd = null;
            if (is != null) {
                rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                int cp;
                while ((cp = rd.read()) != -1) {
                    sb.append((char) cp);
                }
                jsonText = sb.toString();
            }
            return new JSONObject(jsonText);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
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

    private static SlpBIP47AppKit loadFromFile(File baseDir, String walletName, @Nullable WalletExtension... walletExtensions) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(new File(baseDir, walletName + ".wallet"));
                Wallet wallet = Wallet.loadFromFileStream(stream, DeterministicKeyChain.BIP44_ACCOUNT_SLP_PATH, walletExtensions);
                return new SlpBIP47AppKit(wallet, baseDir, walletName);
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

    public SlpBIP47AppKit setCheckpoints(InputStream checkpoints) {
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
        req.tx.addOutput(this.wallet.getParams().getMinNonDustOutput(), this.wallet.currentChangeAddress());
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
        this.slpDbProcessor = new SlpDbProcessor();

        this.setAccount();
        this.loadBip47MetaData();
        Address notificationAddress = this.mAccounts.get(0).getNotificationAddress();
        System.out.println("SlpBIP47AppKit notification address: " + notificationAddress.toString());
        System.out.println("SlpBIP47AppKit payment code: " + getPaymentCode());

        this.wallet.allowSpendingUnconfirmedTransactions();
        this.wallet.setAcceptRiskyTransactions(true);

        if (!this.wallet.isAddressWatched(notificationAddress)) {
            this.wallet.addWatchedAddress(notificationAddress);
        }

        String notifAsCashAddr = notificationAddress.toString();
        this.grabNotificationAddressUtxos(notifAsCashAddr);
    }

    public void recalculateSlpUtxos() {
        if(!recalculatingTokens)  {
            recalculatingTokens = true;
            this.slpUtxos.clear();
            this.slpBalances.clear();
            List<TransactionOutput> utxos = this.wallet.getAllDustUtxos(false, false);
            ArrayList<SlpUTXO> slpUtxosToAdd = new ArrayList<>();

            for (TransactionOutput utxo : utxos) {
                Transaction tx = utxo.getParentTransaction();
                if (tx != null) {
                    if (SlpOpReturn.isSlpTx(tx)) {
                        SlpOpReturn slpOpReturn = new SlpOpReturn(tx);
                        String tokenId = slpOpReturn.getTokenId();

                        if (!hasTransactionBeenRecorded(tx.getTxId().toString())) {
                            SlpDbValidTransaction validTxQuery = new SlpDbValidTransaction(tx.getTxId().toString());
                            boolean valid = this.slpDbProcessor.isValidSlpTx(validTxQuery.getEncoded());
                            if (valid) {
                                SlpUTXO slpUTXO = processSlpUtxo(slpOpReturn, utxo);
                                slpUtxosToAdd.add(slpUTXO);
                                if (!this.tokenIsMapped(tokenId)) {
                                    this.tryCacheToken(tokenId);
                                } else {
                                    SlpToken slpToken = this.getSlpToken(tokenId);
                                    this.calculateSlpBalance(slpUTXO, slpToken);
                                }
                                this.verifiedSlpTxs.add(tx.getTxId().toString());
                            }
                        } else {
                            SlpUTXO slpUTXO = processSlpUtxo(slpOpReturn, utxo);
                            slpUtxosToAdd.add(slpUTXO);
                            if (!this.tokenIsMapped(tokenId)) {
                                this.tryCacheToken(tokenId);
                            } else {
                                SlpToken slpToken = this.getSlpToken(tokenId);
                                this.calculateSlpBalance(slpUTXO, slpToken);
                            }
                        }
                    }
                }
            }

            this.slpUtxos.addAll(slpUtxosToAdd);
            this.saveVerifiedTxs(this.verifiedSlpTxs);
            recalculatingTokens = false;
        }
    }

    private void grabNotificationAddressUtxos(final String cashAddr) {
        new Thread() {
            @Override
            public void run() {
                ArrayList<String> txids = new ArrayList<String>();
                String apiUrl = "http://rest.bitcoin.com/v2/address/utxo/" + cashAddr;
                JSONObject utxosJson = getJSONObject(apiUrl);
                try {
                    JSONArray utxos = utxosJson.getJSONArray("utxos");
                    for (int x = 0; x < utxos.length(); x++) {
                        JSONObject utxo = utxos.getJSONObject(x);
                        String txid = utxo.getString("txid");
                        txids.add(txid);
                    }

                    for (String txid : txids) {
                        grabTransactionAndProcessNotificationTransaction(txid);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void grabTransactionAndProcessNotificationTransaction(String txid) {
        String url = "http://rest.bitcoin.com/v2/rawtransactions/getRawTransaction/" + txid + "?verbose=true";

        JSONObject txJson = getJSONObject(url);
        if(txJson != null) {
            try {
                String txHexVariable = "hex";
                String txHex = txJson.getString(txHexVariable);
                Transaction tx = new Transaction(this.params, Hex.decode(txHex));
                if (isNotificationTransaction(tx)) {
                    BIP47PaymentCode BIP47PaymentCode = getPaymentCodeInNotificationTransaction(tx);
                    if (BIP47PaymentCode != null) {
                        boolean needsSaving = savePaymentCode(BIP47PaymentCode);
                        if (needsSaving) {
                            saveBip47MetaData();
                        }
                    }
                }
            } catch(JSONException e) {
                e.printStackTrace();
            }
        }
    }

    // BIP47-specific listener
    // When a new *notification* transaction is received:
    //  - new keys are generated and imported for incoming payments in the bip47 account/contact payment channel
    //  - the chain is rolled back 2 blocks so that payment transactions are not missed if in the same block as the notification transaction.
    //
    // When a new *payment* transaction is received:
    //  - a new key is generated and imported to the wallet
    private void addTransactionsListener(final Runnable runnable) {
        this.addTransactionEvenListener(new TransactionEventListenerSlp() {
            @Override
            public void onTransactionReceived(SlpBIP47AppKit bip47AppKit, Transaction transaction) {
                if (isNotificationTransaction(transaction)) {
                    BIP47PaymentCode BIP47PaymentCode = getPaymentCodeInNotificationTransaction(transaction);
                    if (BIP47PaymentCode != null) {
                        boolean needsSaving = savePaymentCode(BIP47PaymentCode);
                        if (needsSaving) {
                            try {
                                rescanTxBlock(transaction);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }
                            saveBip47MetaData();
                        }
                    }
                } else if (isToBIP47Address(transaction)) {
                    boolean needsSaving = generateNewBip47IncomingAddress(getAddressOfReceived(transaction).toString());
                    if (needsSaving) {
                        saveBip47MetaData();
                    }
                }

                if(runnable != null)
                    runnable.run();
            }

            @Override
            public void onTransactionSent(SlpBIP47AppKit wallet, Transaction transaction) {
                if(isNotificationTransactionTo(transaction)) {
                    String notificationAddress = getOutgoingNtxAddress(transaction);

                    if(notificationAddress != null) {
                        boolean needsSaving = saveOutgoingChannel(notificationAddress, transaction);
                        if (needsSaving) {
                            try {
                                rescanTxBlock(transaction);
                            } catch (BlockStoreException e) {
                                e.printStackTrace();
                            }

                            saveBip47MetaData();
                        }
                    }
                }

                if(runnable != null)
                    runnable.run();
            }
        });
    }

    /**
     * <p>Create the account M/47'/0'/0' from the seed as a Bip47Account.</p>
     *
     * <p>After deriving, this wallet's payment code is available in @{link Bip47Wallet.getPaymentCode()}</p>
     */
    public void setAccount() {
        byte[] hd_seed = this.restoreFromSeed != null ?
                this.restoreFromSeed.getSeedBytes() :
                this.wallet.getKeyChainSeed().getSeedBytes();


        DeterministicKey mKey = HDKeyDerivation.createMasterPrivateKey(hd_seed);
        DeterministicKey purposeKey = HDKeyDerivation.deriveChildKey(mKey, 47 | ChildNumber.HARDENED_BIT);
        DeterministicKey coinKey = HDKeyDerivation.deriveChildKey(purposeKey, ChildNumber.HARDENED_BIT);

        BIP47Account account = new BIP47Account(params, coinKey, 0);

        mAccounts.clear();
        mAccounts.add(account);
    }

    /**
     * <p>Reads the channels from .bip47 file. Return true if any payment code was loaded. </p>
     */
    public boolean loadBip47MetaData() {
        String jsonString = readBip47MetaDataFile();

        if (StringUtils.isEmpty(jsonString)) {
            return false;
        }

        return importBip47MetaData(jsonString);
    }

    /**
     * <p>Reads the channels from .bip47 file. Return true if any payment code was loaded. </p>
     */
    public String readBip47MetaDataFile() {
        File file = new File(baseDirectory, this.walletName.concat(".bip47"));
        String jsonString;
        try {
            jsonString = FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (IOException e) {
            System.out.println("Creating BIP47 wallet file at " + file.getAbsolutePath() + "  ...");
            saveBip47MetaData();
            loadBip47MetaData();
            return null;
        }

        return jsonString;
    }

    /**
     * <p>Load channels from json. Return true if any payment code was loaded. </p>
     */
    public boolean importBip47MetaData(String jsonString) {
        Gson gson = new Gson();
        Type collectionType = new TypeToken<Collection<BIP47Channel>>() {
        }.getType();
        try {
            List<BIP47Channel> BIP47ChannelList = gson.fromJson(jsonString, collectionType);
            if (BIP47ChannelList != null) {
                for (BIP47Channel channel : BIP47ChannelList) {
                    if(channel.getNotificationAddress() == null && channel.getPaymentCode() != null) {
                        BIP47Account bip47Account = new BIP47Account(getParams(), channel.getPaymentCode());
                        channel.setNotificationAddress(bip47Account.getNotificationAddress().toString());
                    }
                    bip47MetaData.put(channel.getNotificationAddress(), channel);
                }
            }
        } catch (JsonSyntaxException e) {
            return true;
        }
        return false;
    }

    /**
     * <p>Persists the .bip47 file with the channels. </p>
     */
    public synchronized void saveBip47MetaData() {
        try {
            wallet.saveToFile(walletFile);
        } catch (IOException io) {
            log.error("Failed to save wallet file", io);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(bip47MetaData.values());

        File file = new File(baseDirectory, this.walletName.concat(".bip47"));

        try {
            FileUtils.writeStringToFile(file, json, Charset.defaultCharset(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>A listener is added to be invoked when the wallet sees an incoming transaction. </p>
     */
    public void addTransactionEvenListener(TransactionEventListenerSlp transactionEventListener) {
        if (this.mTransactionsEventListener != null)
            wallet.removeCoinsReceivedEventListener(mTransactionsEventListener);

        transactionEventListener.setWallet(this);
        wallet.addCoinsReceivedEventListener(transactionEventListener);
        wallet.addCoinsSentEventListener(transactionEventListener);
        mTransactionsEventListener = transactionEventListener;
    }

    /**
     * <p> Retrieve the relevant address (P2PKH or P2PSH) and compares it with the notification address of this wallet. </p>
     */
    public boolean isNotificationTransaction(Transaction tx) {
        Address address = getAddressOfReceived(tx);
        Address myNotificationAddress = mAccounts.get(0).getNotificationAddress();
        return address != null && address.toString().equals(myNotificationAddress.toString());
    }

    public boolean isNotificationTransactionTo(Transaction tx) {
        if(tx.getValue(getvWallet()).isNegative()) {
            for (TransactionOutput utxo : tx.getOutputs()) {
                if(ScriptPattern.isOpReturn(utxo.getScriptPubKey()) && BIP47Util.isValidNotificationTransactionOpReturn(utxo)) {
                    return true;
                }
            }
        }

        return false;
    }

    public String getOutgoingNtxAddress(Transaction ntx) {
        if(isNotificationTransactionTo(ntx)) {
            for (TransactionOutput utxo : ntx.getOutputs()) {
                if (!utxo.isMine(getvWallet()) && !ScriptPattern.isOpReturn(utxo.getScriptPubKey()) && utxo.getValue().value == 546L) {
                    return Objects.requireNonNull(utxo.getAddressFromP2PKHScript(params)).toString();
                }
            }
        }
        return null;
    }

    /**
     * <p> Retrieve the relevant address (P2PKH or P2PSH), return true if any key in this wallet translates to it. </p>
     */
    // TODO: return true if and only if it is a channel address.
    public boolean isToBIP47Address(Transaction transaction) {
        List<ECKey> keys = wallet.getImportedKeys();
        for (ECKey key : keys) {
            Address address = key.toAddress(params);
            if (address == null) {
                continue;
            }
            Address addressOfReceived = getAddressOfReceived(transaction);
            if (addressOfReceived != null && address.toString().equals(addressOfReceived.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the address that received the transaction (P2PKH or P2PSH output)
     */
    public Address getAddressOfReceived(Transaction tx) {
        for (final TransactionOutput output : tx.getOutputs()) {
            try {
                if (output.isMineOrWatched(wallet)) {
                    final Script script = output.getScriptPubKey();
                    return script.getToAddress(params, true);
                }
            } catch (final ScriptException x) {
                // swallow
            }
        }

        return null;
    }

    /**
     * Given a notification transaction, extracts a valid payment code
     */
    public BIP47PaymentCode getPaymentCodeInNotificationTransaction(Transaction tx) {
        byte[] privKeyBytes = mAccounts.get(0).getNotificationKey().getPrivKeyBytes();

        return BIP47Util.getPaymentCodeInNotificationTransaction(privKeyBytes, tx);
    }

    // <p> Receives a payment code and returns true iff there is already an incoming address generated for the channel</p>
    public boolean savePaymentCode(BIP47PaymentCode bip47PaymentCode) {
        boolean save = true;
        BIP47Account bip47Account = new BIP47Account(params, bip47PaymentCode.toString());
        String notificationAddress = bip47Account.getNotificationAddress().toString();
        for(BIP47Channel channel : bip47MetaData.values()) {
            if (channel.getNotificationAddress().equals(notificationAddress) && channel.getPaymentCode() != null && channel.getIncomingAddresses().size() != 0) {
                save = false;
                break;
            }
        }

        if (bip47MetaData.containsKey(notificationAddress)) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            if (bip47Channel.getIncomingAddresses().size() != 0) {
                save = false;
            } else {
                try {
                    if(bip47Channel.getPaymentCode() == null)
                        bip47Channel.setPaymentCode(bip47PaymentCode.toString());

                    bip47Channel.generateKeys(this);
                    save = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    save = false;
                }
            }
        }

        if(save) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            if (bip47Channel == null) {
                bip47Channel = new BIP47Channel(bip47PaymentCode.toString());
            } else {
                if (bip47Channel.getPaymentCode() == null)
                    bip47Channel.setPaymentCode(bip47PaymentCode.toString());
            }

            try {
                bip47Channel.generateKeys(this);
                bip47MetaData.put(notificationAddress, bip47Channel);
            } catch (Exception e) {
                e.printStackTrace();
                save = false;
            }
        }

        return save;
    }

    public boolean saveOutgoingChannel(String notificationAddress, Transaction ntx) {
        boolean save = true;
        for(BIP47Channel channel : bip47MetaData.values()) {
            if (channel.getNotificationAddress().equals(notificationAddress) && channel.isNotificationTransactionSent()) {
                save = false;
                break;
            }
        }

        if(save) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            if(bip47Channel == null) {
                bip47Channel = new BIP47Channel(notificationAddress, ntx.getTxId());
            } else {
                bip47Channel.setNtxHash(ntx.getTxId());
            }
            bip47Channel.setStatusSent();

            try {
                bip47MetaData.put(notificationAddress, bip47Channel);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        return save;
    }

    public void rescanTxBlock(Transaction tx) throws BlockStoreException {
        try {
            if(tx.getConfidence().getAppearedAtChainHeight() - 2 > this.blockChain.getBestChainHeight()) {
                System.out.println("Transaction is from block " + tx.getConfidence().getAppearedAtChainHeight() + " which is above our local chain height " + this.blockChain.getBestChainHeight());
            } else {
                int blockHeight = tx.getConfidence().getAppearedAtChainHeight() - 2;
                this.blockChain.rollbackBlockStore(blockHeight);
            }
        } catch (IllegalStateException e) {
            //fail silently, we dont need to rollback as it works when txs are in mempool
        }
    }

    public BIP47Account getAccount(int i) {
        return mAccounts.get(i);
    }

    public void importKey(ECKey key) {
        wallet.importKey(key);
    }

    /**
     * Return true if this is the first time the address is seen used
     */
    public boolean generateNewBip47IncomingAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (!bip47Address.getAddress().equals(address)) {
                    continue;
                }
                if (bip47Address.isSeen()) {
                    return false;
                }

                int nextIndex = BIP47Channel.getCurrentIncomingIndex() + 1;
                try {
                    ECKey key = getReceiveAddress(this, BIP47Channel.getPaymentCode(), nextIndex).getReceiveECKey();
                    wallet.importKey(key);
                    Address newAddress = getAddressOfKey(key);
                    BIP47Channel.addNewIncomingAddress(newAddress.toString(), nextIndex);
                    bip47Address.setSeen(true);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        return false;
    }

    public BIP47Channel getBip47MetaForAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return BIP47Channel;
                }
            }
        }
        return null;
    }

    public String getPaymentCodeForAddress(String address) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            for (BIP47Address bip47Address : BIP47Channel.getIncomingAddresses()) {
                if (bip47Address.getAddress().equals(address)) {
                    return BIP47Channel.getPaymentCode();
                }
            }
        }
        return null;
    }

    public BIP47Channel getBip47MetaForPaymentCode(String paymentCode) {
        BIP47Account bip47Account = new BIP47Account(params, paymentCode);
        return getBip47MetaForNotificationAddress(bip47Account.getNotificationAddress().toString());
    }

    public BIP47Channel getBip47MetaForNotificationAddress(String notificationAddress) {
        for (BIP47Channel BIP47Channel : bip47MetaData.values()) {
            if (BIP47Channel.getNotificationAddress().equals(notificationAddress)) {
                return BIP47Channel;
            }
        }
        return null;
    }

    /*If true, it means we have the ntx stored, and can freely get a receiving address.
    If false, it means we need to create a ntx.*/
    public boolean canSendToPaymentCode(String paymentCode) {
        if(Address.isValidPaymentCode(paymentCode)) {
            BIP47Account bip47Account = new BIP47Account(params, paymentCode);
            String notificationAddress = bip47Account.getNotificationAddress().toString();
            BIP47Channel bip47Channel = getBip47MetaForNotificationAddress(notificationAddress);
            if (bip47Channel != null && bip47Channel.getPaymentCode() == null) {
                bip47Channel.setPaymentCode(paymentCode);
                saveBip47MetaData();
            }

            if (bip47Channel != null) {
                return bip47Channel.isNotificationTransactionSent();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public Coin getValueOfTransaction(Transaction transaction) {
        return transaction.getValue(wallet);
    }

    public Coin getValueSentToMe(Transaction transaction) {
        return transaction.getValueSentToMe(wallet);
    }

    public Coin getValueSentFromMe(Transaction transaction) {
        return transaction.getValueSentFromMe(wallet);
    }

    public List<Transaction> getTransactions() {
        return wallet.getTransactionsByTime();
    }

    public long getBalanceValue() {
        return wallet.getBalance(org.bitcoinj.wallet.Wallet.BalanceType.ESTIMATED_SPENDABLE).getValue();
    }

    public Coin getBalance() {
        return wallet.getBalance(org.bitcoinj.wallet.Wallet.BalanceType.ESTIMATED_SPENDABLE);
    }

    public boolean isDownloading() {
        return mBlockchainDownloadProgressTracker != null && mBlockchainDownloadProgressTracker.isDownloading();
    }

    public int getBlockchainProgress() {
        return mBlockchainDownloadProgressTracker != null ? mBlockchainDownloadProgressTracker.getProgress() : -1;
    }

    public boolean isTransactionEntirelySelf(Transaction tx) {
        for (final TransactionInput input : tx.getInputs()) {
            final TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput == null || !connectedOutput.isMine(wallet))
                return false;
        }

        for (final TransactionOutput output : tx.getOutputs()) {
            if (!output.isMine(wallet))
                return false;
        }

        return true;
    }

    public String getPaymentCode() {
        return getAccount(0).getStringPaymentCode();
    }

    public void resetBlockchainSync() {
        File chainFile = new File(baseDirectory, this.walletName + ".spvchain");
        if (chainFile.exists()) {
            System.out.println("deleteSpvFile: exits");
            chainFile.delete();
        }
    }

    public String getMnemonicCode() {
        return StringUtils.join(this.restoreFromSeed != null ?
                this.restoreFromSeed.getMnemonicCode() :
                wallet.getKeyChainSeed().getMnemonicCode());
    }

    public Transaction createSend(Address address, long amount) throws InsufficientMoneyException {
        SendRequest sendRequest = SendRequest.to(this.getParams(), address.toString(), Coin.valueOf(amount));

        sendRequest.feePerKb = getDefaultFee(getParams());

        wallet.completeTx(sendRequest);
        return sendRequest.tx;
    }

    private static Coin getDefaultFee(NetworkParameters params) {
        return Transaction.DEFAULT_TX_FEE;
    }

    public SendRequest makeNotificationTransaction(String paymentCode, boolean allowUnconfirmedSpends) throws InsufficientMoneyException {
        BIP47Account toAccount = new BIP47Account(getParams(), paymentCode);
        Coin ntValue = getParams().getMinNonDustOutput();
        Address ntAddress = toAccount.getNotificationAddress();

        System.out.println("Balance: " + wallet.getBalance());
        System.out.println("To notification address: " + ntAddress.toString());
        System.out.println("Value: " + ntValue.toFriendlyString());

        SendRequest sendRequest = SendRequest.to(this.getParams(), ntAddress.toString(), ntValue);

        if(allowUnconfirmedSpends)
            sendRequest.allowUnconfirmed();

        sendRequest.feePerKb = Coin.valueOf(1000L);
        sendRequest.memo = "notification_transaction";

        org.bitcoinj.utils.BIP47Util.FeeCalculation feeCalculation = BIP47Util.calculateFee(wallet, sendRequest, ntValue, wallet.calculateAllSpendCandidates());

        for (TransactionOutput output : feeCalculation.bestCoinSelection.gathered) {
            sendRequest.tx.addInput(output);
        }

        if (sendRequest.tx.getInputs().size() > 0) {
            TransactionInput txIn = sendRequest.tx.getInput(0);
            RedeemData redeemData = txIn.getConnectedRedeemData(wallet);
            checkNotNull(redeemData, "StashTransaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            System.out.println("Keys: " + redeemData.keys.size());
            System.out.println("Private key 0?: " + redeemData.keys.get(0).hasPrivKey());
            byte[] privKey = redeemData.getFullKey().getPrivKeyBytes();
            System.out.println("Private key: " + Utils.HEX.encode(privKey));
            byte[] pubKey = toAccount.getNotificationKey().getPubKey();
            System.out.println("Public Key: " + Utils.HEX.encode(pubKey));
            byte[] outpoint = txIn.getOutpoint().bitcoinSerialize();

            byte[] mask = null;
            try {
                BIP47SecretPoint BIP47SecretPoint = new BIP47SecretPoint(privKey, pubKey);
                System.out.println("Secret Point: " + Utils.HEX.encode(BIP47SecretPoint.ECDHSecretAsBytes()));
                System.out.println("Outpoint: " + Utils.HEX.encode(outpoint));
                mask = BIP47PaymentCode.getMask(BIP47SecretPoint.ECDHSecretAsBytes(), outpoint);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("My payment code: " + mAccounts.get(0).getPaymentCode().toString());
            System.out.println("Mask: " + Utils.HEX.encode(mask));
            byte[] op_return = BIP47PaymentCode.blind(mAccounts.get(0).getPaymentCode().getPayload(), mask);

            sendRequest.tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(op_return));
        }

        wallet.completeTx(sendRequest);

        System.out.println("Completed SendRequest");

        sendRequest.tx.verify();

        return sendRequest;
    }

    public Transaction getSignedNotificationTransaction(SendRequest sendRequest, String paymentCode) {
        //BIP47Account toAccount = new BIP47Account(getParams(), paymentCode);

        // notification address pub key
        //BIP47Util.signTransaction(vWallet, sendRequest, toAccount.getNotificationKey().getPubKey(), mAccounts.get(0).getPaymentCode());

        wallet.commitTx(sendRequest.tx);

        return sendRequest.tx;
    }

    public ListenableFuture<Transaction> broadcastTransaction(Transaction transactionToSend) {
        wallet.commitTx(transactionToSend);
        return peerGroup.broadcastTransaction(transactionToSend).future();
    }

    public boolean putBip47Meta(String notificationAddress, String paymentCode, @Nullable Transaction ntx) {
        if (bip47MetaData.containsKey(notificationAddress)) {
            BIP47Channel BIP47Channel = bip47MetaData.get(notificationAddress);
            if (ntx != null)
                BIP47Channel.setNtxHash(ntx.getHash());
        } else {
            bip47MetaData.put(notificationAddress, new BIP47Channel(paymentCode));
            if (ntx != null)
                bip47MetaData.get(notificationAddress).setNtxHash(ntx.getHash());
            return true;
        }
        return false;
    }

    /* Mark a channel's notification transaction as sent*/
    public void putPaymenCodeStatusSent(String paymentCode, Transaction ntx) {
        BIP47Account bip47Account = new BIP47Account(getParams(), paymentCode);
        String notificationAddress = bip47Account.getNotificationAddress().toString();
        if (bip47MetaData.containsKey(notificationAddress)) {
            BIP47Channel bip47Channel = bip47MetaData.get(notificationAddress);
            bip47Channel.setNtxHash(ntx.getTxId());
            bip47Channel.setStatusSent();
        } else {
            putBip47Meta(notificationAddress, paymentCode, ntx);
            putPaymenCodeStatusSent(paymentCode, ntx);
        }
    }

    /* Return the next address to send a payment to */
    public String getCurrentOutgoingAddress(BIP47Channel bip47Channel) {
        try {
            ECKey key = getSendAddress(this, new BIP47PaymentCode(bip47Channel.getPaymentCode()), bip47Channel.getCurrentOutgoingIndex()).getSendECKey();
            return key.toAddress(getParams()).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void commitTx(Transaction tx) {
        wallet.commitTx(tx);
    }

    public org.bitcoinj.wallet.Wallet.SendResult sendCoins(SendRequest sendRequest) throws InsufficientMoneyException {
        return wallet.sendCoins(sendRequest);
    }

    public void setOnReceiveTxRunnable(Runnable runnable) {
        this.onReceiveRunnable = runnable;
    }

    public Address getAddressOfKey(ECKey key) {
        return key.toAddress(params);
    }

    public NetworkParameters getParams() {
        return this.params;
    }

    private void calculateSlpBalance(SlpUTXO slpUTXO, SlpToken slpToken) {
        String tokenId = slpToken.getTokenId();
        double tokenAmount = BigDecimal.valueOf(slpUTXO.getTokenAmountRaw()).scaleByPowerOfTen(-slpToken.getDecimals()).doubleValue();
        if (this.isBalanceRecorded(tokenId)) {
            Objects.requireNonNull(this.getTokenBalance(tokenId)).addToBalance(tokenAmount);
        } else {
            this.slpBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
        }
    }

    private SlpUTXO processSlpUtxo(SlpOpReturn slpOpReturn, TransactionOutput utxo) {
        long tokenRawAmount = slpOpReturn.getRawAmountOfUtxo(utxo.getIndex() - 1);
        return new SlpUTXO(slpOpReturn.getTokenId(), tokenRawAmount, utxo, SlpUTXO.SlpUtxoType.NORMAL);
    }

    private void tryCacheToken(String tokenId) {
        if(!this.tokenIsMapped(tokenId)) {
            SlpDbTokenDetails tokenQuery = new SlpDbTokenDetails(tokenId);
            JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());

            if (tokenData != null) {
                int decimals = tokenData.getInt("decimals");
                String ticker = tokenData.getString("ticker");
                SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                this.slpTokens.add(slpToken);
                this.saveTokens(this.slpTokens);
            }
        }
    }

    private boolean isBalanceRecorded(String tokenId) {
        for (SlpTokenBalance tokenBalance : this.slpBalances) {
            if (tokenBalance.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    private SlpTokenBalance getTokenBalance(String tokenId) {
        for (SlpTokenBalance tokenBalance : this.slpBalances) {
            if (tokenBalance.getTokenId().equals(tokenId)) {
                return tokenBalance;
            }
        }

        return null;
    }

    private boolean tokenIsMapped(String tokenId) {
        for (SlpToken slpToken : this.slpTokens) {
            String slpTokenTokenId = slpToken.getTokenId();
            if (slpTokenTokenId != null) {
                if (slpTokenTokenId.equals(tokenId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public SlpToken getSlpToken(String tokenId) {
        for (SlpToken slpToken : this.slpTokens) {
            String slpTokenTokenId = slpToken.getTokenId();
            if (slpTokenTokenId != null) {
                if (slpTokenTokenId.equals(tokenId)) {
                    return slpToken;
                }
            }
        }

        return null;
    }

    public boolean hasTransactionBeenRecorded(String txid) {
        return this.verifiedSlpTxs.contains(txid);
    }

    public Wallet getvWallet() {
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
        return SlpAddressFactory.create().fromCashAddr(this.wallet.getParams(), this.wallet.currentReceiveAddress().toString());
    }

    public SlpAddress currentSlpChangeAddress() {
        return SlpAddressFactory.create().fromCashAddr(this.wallet.getParams(), this.wallet.currentChangeAddress().toString());
    }

    public SlpAddress freshSlpReceiveAddress() {
        return SlpAddressFactory.create().fromCashAddr(this.wallet.getParams(), this.wallet.freshReceiveAddress().toString());
    }

    public SlpAddress freshSlpChangeAddress() {
        return SlpAddressFactory.create().fromCashAddr(this.wallet.getParams(), this.wallet.freshChangeAddress().toString());
    }

    public void setDiscovery(@Nullable PeerDiscovery discovery) {
        this.discovery = discovery;
    }

    public void setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
    }
}
