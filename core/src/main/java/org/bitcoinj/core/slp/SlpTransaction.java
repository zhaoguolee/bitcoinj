package org.bitcoinj.core.slp;

import org.bitcoinj.core.*;
import org.bitcoinj.net.SlpDbValidTransaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SlpTransaction {
    private SlpOpReturn slpOpReturn;
    private Transaction tx;
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();

    public SlpTransaction(Transaction tx) {
        this.tx = tx;
        this.slpOpReturn = new SlpOpReturn(tx);

        if(SlpOpReturn.isSlpTx(tx)) {
            this.collectSlpUtxos(tx.getOutputs(), this.slpOpReturn.getOpReturn());
        } else {
            throw new NullPointerException("Not an SLP transaction.");
        }
    }

    private void collectSlpUtxos(List<TransactionOutput> utxos, Script opReturn) {
        int chunkOffset = 0;
        switch(this.slpOpReturn.getSlpTxType()) {
            case GENESIS:
                chunkOffset = 10;
                break;
            case MINT:
                chunkOffset = 6;
                break;
            case SEND:
                chunkOffset = 5;
                break;
        }

        int tokenUtxosCount = opReturn.getChunks().size() - chunkOffset;

        for(int x = 0; x < tokenUtxosCount; x++) {
            int tokenUtxoChunkLocation = x + chunkOffset;
            TransactionOutput utxo = utxos.get(x + 1);
            long tokenAmountRaw = Long.parseLong(new String(Hex.encode(Objects.requireNonNull(opReturn.getChunks().get(tokenUtxoChunkLocation).data))), 16);
            SlpUTXO slpUtxo = new SlpUTXO(this.slpOpReturn.getTokenId(), tokenAmountRaw, utxo, SlpUTXO.SlpUtxoType.NORMAL);
            slpUtxos.add(slpUtxo);
        }
    }

    public SlpOpReturn getSlpOpReturn() {
        return this.slpOpReturn;
    }

    public String getTokenId() {
        return this.slpOpReturn.getTokenId();
    }

    public Transaction getTx() {
        return this.tx;
    }

    public Sha256Hash getTxId() {
        return this.tx.getTxId();
    }

    public String getTxIdAsString() {
        return this.getTxId().toString();
    }

    public List<SlpUTXO> getSlpUtxos() {
        return this.slpUtxos;
    }

    public BigDecimal getRawTokensSentFromMe(SlpTransaction slpTransaction, Wallet wallet) throws ScriptException {
        // This is tested in WalletTest.
        long value = 0L;
        for (TransactionInput input : slpTransaction.tx.getInputs()) {
            // This input is taking value from a transaction in our wallet. To discover the value,
            // we must find the connected transaction.
            TransactionOutput connected = input.getConnectedOutput(wallet.getTransactionPool(WalletTransaction.Pool.UNSPENT));
            if (connected == null)
                connected = input.getConnectedOutput(wallet.getTransactionPool(WalletTransaction.Pool.SPENT));
            if (connected == null)
                connected = input.getConnectedOutput(wallet.getTransactionPool(WalletTransaction.Pool.PENDING));
            if (connected == null)
                continue;
            // The connected output may be the change to the sender of a previous input sent to this wallet. In this
            // case we ignore it.
            if (!connected.isMineOrWatched(wallet))
                continue;

            Transaction parentTransaction = connected.getParentTransaction();
            if(parentTransaction != null && SlpOpReturn.isSlpTx(parentTransaction)) {
                SlpTransaction parentSlpTransaction = new SlpTransaction(parentTransaction);
                for(SlpUTXO slpUTXO : parentSlpTransaction.getSlpUtxos()) {
                    if(slpUTXO.getTxUtxo() == connected) {
                        value += slpUTXO.getTokenAmountRaw();
                    }
                }
            }
        }

        return BigDecimal.valueOf(value);
    }

    public BigDecimal getRawTokensSentToMe(SlpTransaction slpTransaction, Wallet wallet) {
        // This is tested in WalletTest.
        long value = 0L;
        for (SlpUTXO slpUTXO : slpTransaction.getSlpUtxos()) {
            if (!slpUTXO.getTxUtxo().isMineOrWatched(wallet)) continue;
            value += slpUTXO.getTokenAmountRaw();
        }
        return BigDecimal.valueOf(value);
    }

    public BigDecimal getRawValue(SlpTransaction slpTransaction, Wallet wallet) throws ScriptException {
        return getRawTokensSentToMe(slpTransaction, wallet).subtract(getRawTokensSentFromMe(slpTransaction, wallet));
    }
}
