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

    public SlpUTXO getSlpUtxo(TransactionOutput utxo) {
        String outpoint = utxo.getOutPointFor().toString();
        for(SlpUTXO slpUTXO : this.getSlpUtxos()) {
            String oOutpoint = slpUTXO.getTxUtxo().getOutPointFor().toString();
            if(outpoint.equals(oOutpoint)) {
                return slpUTXO;
            }
        }

        return null;
    }

    /**
     * Calculates the sum of the outputs that are sending coins to a key in the wallet.
     */
    public long getTokensSentToMe(TransactionBag transactionBag) {
        long v = 0;
        for (SlpUTXO o : this.getSlpUtxos()) {
            if (o.getTxUtxo().isMineOrWatched(transactionBag))
                v += o.getTokenAmountRaw();
        }
        return v;
    }

    /**
     * Calculates the sum of the inputs that are spending coins with keys in the wallet. This requires the
     * transactions sending coins to those keys to be in the wallet. This method will not attempt to download the
     * blocks containing the input transactions if the key is in the wallet but the transactions are not.
     *
     * @return sum of the inputs that are spending coins with keys in the wallet
     */
    public long getTokensSentFromMe(TransactionBag transactionBag) throws ScriptException {
        long v = 0;
        for (SlpUTXO o : this.getSlpUtxos()) {
            if (!o.getTxUtxo().isMineOrWatched(transactionBag))
                v += o.getTokenAmountRaw();
        }
        return v;
    }

    public long getValue(TransactionBag wallet) throws ScriptException {
        return getTokensSentFromMe(wallet) - getTokensSentToMe(wallet);
    }
}
