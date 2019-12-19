package org.bitcoinj.core;

public class SlpToken {
    private String tokenId;
    private String ticker;
    private int decimals;
    public SlpToken(String tokenId/*, String ticker, int decimals*/) {
        this.tokenId = tokenId;
        //this.ticker = ticker;
        //this.decimals = decimals;
    }

    public String getTokenId() {
        return this.tokenId;
    }

    @Override
    public String toString() {
        return "SLP TOKEN [" +
                this.getTokenId() +
                "]";
    }
}
