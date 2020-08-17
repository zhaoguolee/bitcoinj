package org.bitcoinj.crypto;

public class MultisigSignature {
    private int index;
    private byte[] sig;

    public MultisigSignature(int index, byte[] sig) {
        this.index = index;
        this.sig = sig;
    }

    public int getIndex() {
        return this.index;
    }

    public byte[] getSig() {
        return sig;
    }
}
