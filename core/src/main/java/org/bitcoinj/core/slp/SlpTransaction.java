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
    private static final String slpProtocolId = "534c5000";
    private static final String tokenTypeId = "01";
    private static final String genesisTxTypeId = "47454e45534953";
    private static final String mintTxTypeId = "4d494e54";
    private static final String sendTxTypeId = "53454e44";
    private static final int opReturnLocation = 0;
    private static final int protocolChunkLocation = 1;
    private static final int slpTokenTypeChunkLocation = 2;
    private static final int slpTxTypeChunkLocation = 3;
    private static final int tokenIdChunkLocation = 4;
    private static final int mintBatonVoutGenesisChunkLocation = 9;
    private static final int mintBatonVoutMintChunkLocation = 5;
    private static final int tokenOutputsStartLocation = 5;

    private enum SlpTxType {
        GENESIS,
        MINT,
        SEND
    }

    private String tokenId;
    private Transaction tx;
    private SlpTxType slpTxType;
    private ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();
    private boolean hasMintingBaton = false;
    private TransactionOutput mintingBatonUtxo = null;
    private Wallet wallet;

    public SlpTransaction(Transaction tx, Wallet wallet) {
        this.wallet = wallet;
        this.tx = tx;
        Script opReturn = tx.getOutput(opReturnLocation).getScriptPubKey();

        if(ScriptPattern.isOpReturn(opReturn)) {
            this.setSlpTxType(tx, opReturn);
            this.setTokenId(opReturn);
            this.collectSlpUtxos(tx.getOutputs(), opReturn);
            this.findMintingBaton(tx.getOutputs(), opReturn);
        } else {
            throw new NullPointerException("Not an SLP transaction.");
        }
    }

    public static boolean isSlpTx(Transaction tx) {
        List<TransactionOutput> outputs = tx.getOutputs();
        TransactionOutput opReturnUtxo = outputs.get(0);
        Script opReturn = opReturnUtxo.getScriptPubKey();
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if(protocolId.equals(slpProtocolId)) {
                    ScriptChunk tokenTypeChunk = opReturn.getChunks().get(slpTokenTypeChunkLocation);
                    if (tokenTypeChunk != null && tokenTypeChunk.data != null) {
                        String tokenType = new String(Hex.encode(tokenTypeChunk.data), StandardCharsets.UTF_8);
                        return tokenType.equals(tokenTypeId);
                    }
                }
            }
        }

        return false;
    }

    private void setTokenId(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            if(this.getSlpTxType() == SlpTxType.SEND) {
                ScriptChunk tokenIdChunk = opReturn.getChunks().get(tokenIdChunkLocation);
                assert tokenIdChunk.data != null;
                this.tokenId = new String(Hex.encode(tokenIdChunk.data));
            } else if(this.getSlpTxType() == SlpTxType.GENESIS) {
                this.tokenId = this.tx.getTxId().toString();
            }
        }
    }

    private void setSlpTxType(Transaction tx, Script opReturn) {
        if (isSlpTx(tx)) {
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

    public SlpTxType getSlpTxType() {
        return this.slpTxType;
    }

    private void collectSlpUtxos(List<TransactionOutput> utxos, Script opReturn) {
        int chunkOffset = 0;
        switch(this.slpTxType) {
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
                SlpUTXO slpUtxo = new SlpUTXO(this.tokenId, tokenAmountRaw, utxo, SlpUTXO.SlpUtxoType.NORMAL);
                slpUtxos.add(slpUtxo);
            }
        }
    }

    private void findMintingBaton(List<TransactionOutput> utxos, Script opReturn) {
        byte[] mintingBatonVoutData = null;
        if(this.getSlpTxType() == SlpTxType.GENESIS) {
            mintingBatonVoutData = opReturn.getChunks().get(mintBatonVoutGenesisChunkLocation).data;
        } else if(this.getSlpTxType() == SlpTxType.MINT) {
            mintingBatonVoutData = opReturn.getChunks().get(mintBatonVoutMintChunkLocation).data;
        }

        if(mintingBatonVoutData != null) {
            String voutHex = new String(Hex.encode(mintingBatonVoutData), StandardCharsets.UTF_8);
            if(!voutHex.equals("")) {
                int vout = Integer.parseInt(voutHex, 16);
                TransactionOutput utxo = utxos.get(vout);
                if(utxoIsMintBaton(opReturn, utxo)) {
                    this.hasMintingBaton = true;
                    this.mintingBatonUtxo = utxo;
                }
            }
        }
    }

    private boolean utxoIsMintBaton(Script opReturn, TransactionOutput utxo) {
        int mintingBatonVout = 0;
        int utxoVout = utxo.getIndex();
        int voutChunkLocation = 0;

        switch(this.slpTxType) {
            case GENESIS:
                voutChunkLocation = mintBatonVoutGenesisChunkLocation;
                break;
            case MINT:
                voutChunkLocation = mintBatonVoutMintChunkLocation;
                break;
        }

        ScriptChunk mintBatonVoutChunk = opReturn.getChunks().get(voutChunkLocation);
        if (mintBatonVoutChunk != null && mintBatonVoutChunk.data != null) {
            String voutHex = new String(Hex.encode(mintBatonVoutChunk.data), StandardCharsets.UTF_8);

            if(!voutHex.equals("")) {
                mintingBatonVout = Integer.parseInt(voutHex, 16);
            } else {
                return false;
            }
        }

        return mintingBatonVout == utxoVout;
    }

    public String getTokenId() {
        return this.tokenId;
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

    public boolean hasMintingBaton() {
        return this.hasMintingBaton;
    }

    public TransactionOutput getMintingBatonUtxo() {
        return this.mintingBatonUtxo;
    }
}
