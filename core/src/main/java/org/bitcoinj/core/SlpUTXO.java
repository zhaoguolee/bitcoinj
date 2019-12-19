package org.bitcoinj.core;

public class SlpUTXO {
    private String tokenId;
    private double tokenAmount;
    private TransactionOutput txUtxo;

    public SlpUTXO(String tokenId, double tokenAmount, TransactionOutput txUtxo) {
        this.tokenId = tokenId;
        this.tokenAmount = tokenAmount;
        this.txUtxo = txUtxo;
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public double getTokenAmount() {
        return this.tokenAmount;
    }

    public TransactionOutput getTxUtxo() {
        return this.txUtxo;
    }
}
