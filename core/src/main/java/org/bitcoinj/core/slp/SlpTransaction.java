package org.bitcoinj.core.slp;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

public class SlpTransaction {
    private final String slpProtocolId = "534c5000";
    private final String genesisTxTypeId = "47454e45534953";
    private final String mintTxTypeId = "4d494e54";
    private final String sendTxTypeId = "53454e44";
    private final int opReturnLocation = 0;
    private final int protocolChunkLocation = 1;
    private enum SlpTxType {
        GENESIS,
        MINT,
        SEND
    }

    private Transaction tx;
    private SlpTxType slpTxType;
    private SlpUTXO slpUtxos;

    public SlpTransaction(Transaction tx) {
        this.tx = tx;
        Script opReturn = tx.getOutput(opReturnLocation).getScriptPubKey();
        this.setSlpTxType(opReturn);
    }

    private void setSlpTxType(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk slpTxTypeChunk = tx.getOutputs().get(0).getScriptPubKey().getChunks().get(3);
                    if (slpTxTypeChunk != null && slpTxTypeChunk.data != null) {
                        String txType = new String(Hex.encode(slpTxTypeChunk.data), StandardCharsets.UTF_8);
                        switch(txType) {
                            case genesisTxTypeId:
                                this.slpTxType = SlpTxType.GENESIS;
                                break;
                            case mintTxTypeId:
                                this.slpTxType = SlpTxType.MINT;
                                break;
                            case sendTxTypeId:
                                this.slpTxType = SlpTxType.SEND;
                                break;
                        }
                    }
                }
            }
        }
    }
}
