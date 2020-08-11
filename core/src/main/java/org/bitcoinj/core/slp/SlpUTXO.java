package org.bitcoinj.core.slp;

import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.kits.SlpAppKit;

public class SlpUTXO {
    public enum SlpUtxoType {
        NORMAL,
        MINT_BATON
    }

    private String tokenId;
    private double tokenAmount;
    private TransactionOutput txUtxo;
    private SlpUtxoType slpUtxoType;

    public SlpUTXO(String tokenId, double tokenAmount, TransactionOutput txUtxo, SlpUtxoType slpUtxoType) {
        this.tokenId = tokenId;
        this.tokenAmount = tokenAmount;
        this.txUtxo = txUtxo;
        this.slpUtxoType = slpUtxoType;
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

    public SlpUtxoType getSlpUtxoType() {
        return this.slpUtxoType;
    }
}
