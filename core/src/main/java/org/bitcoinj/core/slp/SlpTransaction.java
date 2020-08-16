package org.bitcoinj.core.slp;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.net.SlpDbValidTransaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SlpTransaction {
    private SlpOpReturn slpOpReturn;
    private Transaction tx;
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();
    private Wallet wallet;

    public SlpTransaction(Transaction tx, Wallet wallet) {
        this.wallet = wallet;
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
            if(utxo.isMine(this.wallet)) {
                long tokenAmountRaw = Long.parseLong(new String(Hex.encode(Objects.requireNonNull(opReturn.getChunks().get(tokenUtxoChunkLocation).data))), 16);
                SlpUTXO slpUtxo = new SlpUTXO(this.slpOpReturn.getTokenId(), tokenAmountRaw, utxo, SlpUTXO.SlpUtxoType.NORMAL);
                slpUtxos.add(slpUtxo);
            }
        }
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
}
