package org.bitcoinj.core.slp;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SlpTransaction {
    private final String slpProtocolId = "534c5000";
    private final String genesisTxTypeId = "47454e45534953";
    private final String mintTxTypeId = "4d494e54";
    private final String sendTxTypeId = "53454e44";
    private final int opReturnLocation = 0;
    private final int protocolChunkLocation = 1;
    private final int slpTokenTypeChunkLocation = 2;
    private final int slpTxTypeChunkLocation = 3;
    private final int tokenIdChunkLocation = 4;
    private final int tokenOutputsStartLocation = 5;

    private enum SlpTxType {
        GENESIS,
        MINT,
        SEND
    }

    private String tokenId;
    private Transaction tx;
    private SlpTxType slpTxType;
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();

    public SlpTransaction(Transaction tx) {
        this.tx = tx;
        Script opReturn = tx.getOutput(opReturnLocation).getScriptPubKey();
        this.setTokenId(opReturn);
        this.setSlpTxType(opReturn);
        this.collectSlpUtxos(tx.getOutputs(), opReturn);
    }

    private void setTokenId(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk tokenIdChunk = opReturn.getChunks().get(tokenIdChunkLocation);
            assert tokenIdChunk.data != null;
            this.tokenId = new String(Hex.encode(tokenIdChunk.data));
            System.out.println(this.tokenId);
        }
    }

    private void setSlpTxType(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk slpTxTypeChunk = opReturn.getChunks().get(slpTxTypeChunkLocation);
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

    private void collectSlpUtxos(List<TransactionOutput> utxos, Script opReturn) {
        int tokenUtxosCount = opReturn.getChunks().size() - 5;
        for(int x = 0; x < tokenUtxosCount; x++) {
            int tokenUtxoChunkLocation = x + 5;
            TransactionOutput utxo = utxos.get(x + 1);
            long tokenAmountRaw = Long.parseLong(new String(Hex.encode(opReturn.getChunks().get(tokenUtxoChunkLocation).data)), 16);
            SlpUTXO slpUtxo = new SlpUTXO(this.tokenId, tokenAmountRaw, utxo, SlpUTXO.SlpUtxoType.NORMAL);
            slpUtxos.add(slpUtxo);
        }
    }
}
