/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bchj.wallet;

import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bch.protocols.payments.Protos.PaymentDetails;
import org.bchj.core.*;
import org.bchj.core.flipstarter.FlipstarterInvoicePayload;
import org.bchj.core.flipstarter.FlipstarterPledgePayload;
import org.bchj.core.memo.MemoOpReturnOutput;
import org.bchj.crypto.TransactionSignature;
import org.bchj.net.NetHelper;
import org.bchj.params.MainNetParams;
import org.bchj.script.Script;
import org.bchj.script.ScriptBuilder;
import org.bchj.script.ScriptOpCodes;
import org.bchj.utils.ExchangeRate;
import org.bchj.wallet.KeyChain.KeyPurpose;
import org.bchj.wallet.Wallet.MissingSigsMode;
import org.bchj.wallet.selector.CoinSelector;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A SendRequest gives the wallet information about precisely how to send money to a recipient or set of recipients.
 * Static methods are provided to help you create SendRequests and there are a few helper methods on the wallet that
 * just simplify the most common use cases. You may wish to customize a SendRequest if you want to attach a fee or
 * modify the change address.
 */
public class SendRequest {
    /**
     * <p>A transaction, probably incomplete, that describes the outline of what you want to do. This typically will
     * mean it has some outputs to the intended destinations, but no inputs or change address (and therefore no
     * fees) - the wallet will calculate all that for you and update tx later.</p>
     *
     * <p>Be careful when adding outputs that you check the min output value
     * ({@link TransactionOutput#getMinNonDustValue(Coin)}) to avoid the whole transaction being rejected
     * because one output is dust.</p>
     *
     * <p>If there are already inputs to the transaction, make sure their out point has a connected output,
     * otherwise their value will be added to fee.  Also ensure they are either signed or are spendable by a wallet
     * key, otherwise the behavior of {@link Wallet#completeTx(SendRequest)} is undefined (likely
     * RuntimeException).</p>
     */
    public Transaction tx;

    /**
     * When emptyWallet is set, all coins selected by the coin selector are sent to the first output in tx
     * (its value is ignored and set to {@link Wallet#getBalance()} - the fees required
     * for the transaction). Any additional outputs are removed.
     */
    public boolean emptyWallet = false;

    @Nullable
    public Address preferredChangeAddress = null;

    /**
     * UTXOs to use when sending. If left null, then the wallet automatically determine the UTXOs to use.
     */
    public List<TransactionOutput> utxos = null;

    /**
     * "Change" means the difference between the value gathered by a transactions inputs (the size of which you
     * don't really control as it depends on who sent you money), and the value being sent somewhere else. The
     * change address should be selected from this wallet, normally. <b>If null this will be chosen for you.</b>
     */
    public Address changeAddress = null;

    /**
     * <p>A transaction can have a fee attached, which is defined as the difference between the input values
     * and output values. Any value taken in that is not provided to an output can be claimed by a miner. This
     * is how mining is incentivized in later years of the Bitcoin system when inflation drops. It also provides
     * a way for people to prioritize their transactions over others and is used as a way to make denial of service
     * attacks expensive.</p>
     *
     * <p>This is a dynamic fee (in satoshis) which will be added to the transaction for each virtual kilobyte in size
     * including the first. This is useful as as miners usually sort pending transactions by their fee per unit size
     * when choosing which transactions to add to a block. Note that, to keep this equivalent to Bitcoin Core
     * definition, a virtual kilobyte is defined as 1000 virtual bytes, not 1024.</p>
     */
    public Coin feePerKb = Context.get().getFeePerKb();

    public static SendRequest createSlpTransaction(NetworkParameters params) {
        SendRequest req = new SendRequest();
        checkNotNull(params, "Address is for an unknown network");
        req.tx = new Transaction(params);
        return req;
    }

    public void setFeePerVkb(Coin feePerVkb) {
        this.feePerKb = feePerVkb;
    }

    /**
     * <p>Requires that there be enough fee for a default Bitcoin Core to at least relay the transaction.
     * (ie ensure the transaction will not be outright rejected by the network). Defaults to true, you should
     * only set this to false if you know what you're doing.</p>
     *
     * <p>Note that this does not enforce certain fee rules that only apply to transactions which are larger than
     * 26,000 bytes. If you get a transaction which is that large, you should set a feePerKb of at least
     * {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE}.</p>
     */
    public boolean ensureMinRequiredFee = Context.get().isEnsureMinRequiredFee();

    /**
     * If true (the default), the inputs will be signed.
     */
    public boolean signInputs = true;

    /**
     * The AES key to use to decrypt the private keys before signing.
     * If null then no decryption will be performed and if decryption is required an exception will be thrown.
     * You can get this from a password by doing wallet.getKeyCrypter().deriveKey(password).
     */
    public KeyParameter aesKey = null;

    /**
     * If not null, the {@link CoinSelector} to use instead of the wallets default. Coin selectors are
     * responsible for choosing which transaction outputs (coins) in a wallet to use given the desired send value
     * amount.
     */
    public CoinSelector coinSelector = null;

    /**
     * Shortcut for {@code req.coinSelector = AllowUnconfirmedCoinSelector.get();}.
     */
    public void allowUnconfirmed() {
        coinSelector = AllowUnconfirmedCoinSelector.get();
    }

    /**
     * If true (the default), the outputs will be shuffled during completion to randomize the location of the change
     * output, if any. This is normally what you want for privacy reasons but in unit tests it can be annoying
     * so it can be disabled here.
     */
    public boolean shuffleOutputs = true;

    /**
     * Specifies what to do with missing signatures left after completing this request. Default strategy is to
     * throw an exception on missing signature ({@link MissingSigsMode#THROW}).
     *
     * @see MissingSigsMode
     */
    public MissingSigsMode missingSigsMode = MissingSigsMode.THROW;

    /**
     * If not null, this exchange rate is recorded with the transaction during completion.
     */
    public ExchangeRate exchangeRate = null;

    /**
     * If not null, this memo is recorded with the transaction during completion. It can be used to record the memo
     * of the payment request that initiated the transaction.
     */
    public String memo = null;

    /**
     * If false (default value), tx fee is paid by the sender If true, tx fee is paid by the recipient/s. If there is
     * more than one recipient, the tx fee is split equally between them regardless of output value and size.
     */
    public boolean recipientsPayFees = false;

    // Tracks if this has been passed to wallet.completeTx already: just a safety check.
    boolean completed;

    private SendRequest() {
    }

    /**
     * <p>Creates a new SendRequest to the given address for the given value.</p>
     *
     * <p>Be careful to check the output's value is reasonable using
     * {@link TransactionOutput#getMinNonDustValue(Coin)} afterwards or you risk having the transaction
     * rejected by the network.</p>
     */
    public static SendRequest to(Address recipient, Coin value) throws NullPointerException, AddressFormatException {
        return to(MainNetParams.get(), recipient.toString(), value, null);
    }

    public static SendRequest to(NetworkParameters params, Address recipient, Coin value) throws NullPointerException, AddressFormatException {
        return to(params, recipient.toString(), value, null);
    }

    public static SendRequest to(NetworkParameters params, String recipient, Coin value) throws NullPointerException, AddressFormatException {
        return to(params, recipient, value, null);
    }

    public static SendRequest to(NetworkParameters params, String recipient, Coin value, Proxy proxy) throws NullPointerException, AddressFormatException {
        NetHelper netHelper = new NetHelper();

        SendRequest req = new SendRequest();
        Address destination = null;

        if (recipient.contains("#")) {
            String cashAcctAddress = proxy != null ? netHelper.getCashAccountAddress(params, recipient, proxy) : netHelper.getCashAccountAddress(params, recipient);
            destination = AddressFactory.create().getAddress(params, cashAcctAddress);
        } else {
            destination = AddressFactory.create().getAddress(params, recipient);
        }

        checkNotNull(params, "Address is for an unknown network");
        checkNotNull(destination, "No address set!");
        req.tx = new Transaction(params);
        req.tx.addOutput(value, destination);
        return req;
    }

    /**
     * <p>Creates a new SendRequest to the given pubkey for the given value.</p>
     *
     * <p>Be careful to check the output's value is reasonable using
     * {@link TransactionOutput#getMinNonDustValue(Coin)} afterwards or you risk having the transaction
     * rejected by the network. Note that using {@link SendRequest#to(NetworkParameters, String, Coin)} will result
     * in a smaller output, and thus the ability to use a smaller output value without rejection.</p>
     */
    public static SendRequest to(NetworkParameters params, ECKey destination, Coin value) {
        SendRequest req = new SendRequest();
        req.tx = new Transaction(params);
        req.tx.addOutput(value, destination);
        return req;
    }

    /**
     * Simply wraps a pre-built incomplete transaction provided by you.
     */
    public static SendRequest forTx(Transaction tx) {
        SendRequest req = new SendRequest();
        req.tx = tx;
        return req;
    }

    public static SendRequest emptyWallet(Address recipient) throws NullPointerException, AddressFormatException {
        return emptyWallet(MainNetParams.get(), recipient.toString(), null);
    }

    public static SendRequest emptyWallet(NetworkParameters params, Address recipient) throws NullPointerException, AddressFormatException {
        return emptyWallet(params, recipient.toString(), null);
    }

    public static SendRequest emptyWallet(NetworkParameters params, String recipient) throws NullPointerException, AddressFormatException {
        return emptyWallet(params, recipient, null);
    }

    public static SendRequest emptyWallet(NetworkParameters params, String recipient, Proxy proxy) throws NullPointerException, AddressFormatException {
        NetHelper netHelper = new NetHelper();

        SendRequest req = new SendRequest();
        Address destination = null;

        if (recipient.contains("#")) {
            String cashAcctAddress = proxy != null ? netHelper.getCashAccountAddress(params, recipient, proxy) : netHelper.getCashAccountAddress(params, recipient);
            destination = AddressFactory.create().getAddress(params, cashAcctAddress);
        } else {
            destination = AddressFactory.create().getAddress(params, recipient);
        }

        checkNotNull(params, "Address is for an unknown network");
        checkNotNull(destination, "No address set!");
        req.tx = new Transaction(params);
        req.tx.addOutput(Coin.ZERO, destination);
        req.emptyWallet = true;
        return req;
    }

    public static SendRequest createCashAccount(NetworkParameters params, String desiredAddressForCashAccount, String cashAccountName) throws NullPointerException, AddressFormatException {
        SendRequest req = new SendRequest();
        Address destination = null;

        destination = AddressFactory.create().getAddress(params, desiredAddressForCashAccount);

        checkNotNull(params, "Address is for an unknown network");
        assert destination != null;
        checkNotNull(destination, "No address set!");
        String hash160 = new String(Hex.encode(destination.getHash160()), StandardCharsets.UTF_8);
        req.tx = new Transaction(params);
        ScriptBuilder scriptBuilder = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("01010101"))
                .data(cashAccountName.getBytes())
                .data(Hex.decode("01" + hash160));
        Script script = scriptBuilder.build();
        req.tx.addOutput(Coin.ZERO, script);
        return req;
    }

    /**
     * Construct a SendRequest for a CPFP (child-pays-for-parent) transaction. The resulting transaction is already
     * completed, so you should directly proceed to signing and broadcasting/committing the transaction. CPFP is
     * currently only supported by a few miners, so use with care.
     */
    public static SendRequest childPaysForParent(Wallet wallet, Transaction parentTransaction, Coin feeRaise) {
        TransactionOutput outputToSpend = null;
        for (final TransactionOutput output : parentTransaction.getOutputs()) {
            if (output.isMine(wallet) && output.isAvailableForSpending()
                    && output.getValue().isGreaterThan(feeRaise)) {
                outputToSpend = output;
                break;
            }
        }
        // TODO spend another confirmed output of own wallet if needed
        checkNotNull(outputToSpend, "Can't find adequately sized output that spends to us");

        final Transaction tx = new Transaction(parentTransaction.getParams());
        tx.addInput(outputToSpend);
        tx.addOutput(outputToSpend.getValue().subtract(feeRaise), wallet.freshAddress(KeyPurpose.CHANGE));
        tx.setPurpose(Transaction.Purpose.RAISE_FEE);
        final SendRequest req = forTx(tx);
        req.completed = true;
        return req;
    }

    public static SendRequest memoAction(Wallet wallet, MemoOpReturnOutput memoAction) throws InsufficientMoneyException {
        SendRequest req = new SendRequest();
        Coin valueNeeded = Coin.ZERO;
        Coin valueMissing = Coin.ZERO;
        req.tx = new Transaction(wallet.params);
        req.tx.addOutput(Coin.ZERO, memoAction.getScript());
        req.preferredChangeAddress = wallet.getMemoAccountAddress();
        List<TransactionOutput> candidates = wallet.calculateSpendCandidatesForAddress(wallet.getMemoAccountAddress(), true, true);
        CoinSelection selection = wallet.getCoinSelector().select(valueNeeded, new LinkedList<TransactionOutput>(candidates));
        // Can we afford this?
        if (selection.valueGathered.compareTo(valueNeeded) < 0) {
            valueMissing = valueNeeded.subtract(selection.valueGathered);
        }

        for(TransactionOutput output : selection.gathered) {
            req.tx.addInput(output);
        }

        if(valueMissing != Coin.ZERO) {
            throw new InsufficientMoneyException(valueMissing);
        }

        return req;
    }

    public static Pair<Transaction, String> createFlipstarterPledge(Wallet wallet, String invoicePayloadBase64) throws InsufficientMoneyException {
        byte[] payloadBytes = Base64.decode(invoicePayloadBase64);
        String invoiceJson = new String(payloadBytes, StandardCharsets.UTF_16LE);
        FlipstarterInvoicePayload invoicePayload = new Gson().fromJson(invoiceJson, FlipstarterInvoicePayload.class);
        SendRequest pledgeInputReq = new SendRequest();
        pledgeInputReq.feePerKb = Coin.valueOf(1000L);
        pledgeInputReq.shuffleOutputs = false;
        pledgeInputReq.allowUnconfirmed();
        pledgeInputReq.tx = new Transaction(wallet.getParams());
        pledgeInputReq.tx.addOutput(Coin.valueOf(invoicePayload.donation.amount), wallet.freshReceiveAddress());
        pledgeInputReq.memo = "flipstarter_pledge";
        wallet.completeTx(pledgeInputReq);
        wallet.commitTx(pledgeInputReq.tx);

        Transaction flipstarterTx = new Transaction(wallet.getParams());
        flipstarterTx.setVersion(Transaction.CURRENT_VERSION);
        for(FlipstarterInvoicePayload.Output output : invoicePayload.outputs) {
            flipstarterTx.addOutput(Coin.valueOf(output.value), AddressFactory.create().getAddress(wallet.getParams(), output.address));
        }

        TransactionOutput output = pledgeInputReq.tx.getOutput(0);
        TransactionInput txIn = flipstarterTx.addInput(output);
        Script scriptPubKey = output.getScriptPubKey();
        RedeemData redeemData = txIn.getConnectedRedeemData(wallet);
        checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
        txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript));
        ECKey key = redeemData.getFullKey();
        byte[] script = redeemData.redeemScript.getProgram();

        TransactionSignature signature = flipstarterTx.calculateWitnessSignature(txIn.getIndex(), key, script, output.getValue(), Transaction.SigHash.ALL, true);
        int sigIndex = 0;
        Script inputScript = txIn.getScriptSig();
        inputScript = scriptPubKey.getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), sigIndex);
        txIn.setScriptSig(inputScript);

        TransactionOutPoint txInOutpoint = txIn.getOutpoint();
        String unlockingScript = Hex.toHexString(txIn.getScriptSig().getProgram());

        FlipstarterPledgePayload.Input input = new FlipstarterPledgePayload.Input(txInOutpoint.getHash().toString(), txInOutpoint.getIndex(), txIn.getSequenceNumber(), unlockingScript);
        ArrayList<FlipstarterPledgePayload.Input> payloadInputs = new ArrayList<>();
        payloadInputs.add(input);
        String alias = "";
        if(invoicePayload.data.alias != null) {
            alias = invoicePayload.data.alias;
        }
        String comment = "";
        if(invoicePayload.data.comment != null) {
            comment = invoicePayload.data.comment;
        }
        FlipstarterPledgePayload pledgePayload = new FlipstarterPledgePayload(payloadInputs, new FlipstarterPledgePayload.Data(alias, comment), null);
        GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        Gson gson = builder.create();
        String json = gson.toJson(pledgePayload);
        String base64Payload = Base64.toBase64String(json.getBytes());

        txIn.verify(output);
        output.setFrozen(true);
        wallet.saveNow();
        return new MutablePair<>(pledgeInputReq.tx, base64Payload);
    }

    public static SendRequest cancelFlipstarterPledge(Wallet wallet, TransactionOutput pledgeUtxo) throws InsufficientMoneyException {
        SendRequest req = new SendRequest();
        req.tx = new Transaction(wallet.getParams());
        ArrayList<TransactionOutput> pledgeUtxos = new ArrayList<>();
        pledgeUtxos.add(pledgeUtxo);
        req.utxos = pledgeUtxos;
        req.emptyWallet = true;
        req.feePerKb = Coin.valueOf(1000L);
        req.ensureMinRequiredFee = true;
        req.tx.addOutput(Coin.ZERO, wallet.freshReceiveAddress());
        return req;
    }

    /**
     * Copy data from payment request.
     */
    public SendRequest fromPaymentDetails(PaymentDetails paymentDetails) {
        if (paymentDetails.hasMemo())
            this.memo = paymentDetails.getMemo();
        return this;
    }

    /**
     * Use Version 2 Transactions with forkid signatures
     **/
    private boolean useForkId = false;

    public void setUseForkId(boolean useForkId) {
        this.useForkId = useForkId;
        if (tx != null)
            tx.setVersion(Transaction.CURRENT_VERSION);
    }

    public boolean getUseForkId() {
        return useForkId;
    }

    @Override
    public String toString() {
        // print only the user-settable fields
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("emptyWallet", emptyWallet);
        helper.add("changeAddress", changeAddress);
        helper.add("feePerKb", feePerKb);
        helper.add("ensureMinRequiredFee", ensureMinRequiredFee);
        helper.add("signInputs", signInputs);
        helper.add("aesKey", aesKey != null ? "set" : null); // careful to not leak the key
        helper.add("coinSelector", coinSelector);
        helper.add("shuffleOutputs", shuffleOutputs);
        helper.add("recipientsPayFees", recipientsPayFees);
        return helper.toString();
    }
}