package org.bitcoinj.core;

public class SlpUTXO {
    private String tokenId;
    private long tokenAmount;
    private TransactionOutput txUtxo;

    public SlpUTXO(String tokenId, long tokenAmount, TransactionOutput txUtxo) {
        this.tokenId = tokenId;
        this.tokenAmount = tokenAmount;
        this.txUtxo = txUtxo;
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public long getTokenAmount() {
        return this.tokenAmount;
    }

    public TransactionOutput getTxUtxo() {
        return this.txUtxo;
    }
}
