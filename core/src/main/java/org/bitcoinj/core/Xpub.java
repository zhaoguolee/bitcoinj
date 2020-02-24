package org.bitcoinj.core;

import java.nio.ByteBuffer;

public class Xpub {
    public boolean isValid(String xpub) {
        try {
            byte[] xpubBytes = Base58.decodeChecked(xpub);
            ByteBuffer byteBuffer = ByteBuffer.wrap(xpubBytes);
            if (byteBuffer.getInt() != 76067358) {
                throw new AddressFormatException("invalid version: " + xpub);
            } else {
                byte[] chain = new byte[32];
                byte[] pub = new byte[33];
                byteBuffer.get();
                byteBuffer.getInt();
                byteBuffer.getInt();
                byteBuffer.get(chain);
                byteBuffer.get(pub);
                ByteBuffer pubBytes = ByteBuffer.wrap(pub);
                int firstByte = pubBytes.get();
                if (firstByte != 2 && firstByte != 3) {
                    throw new AddressFormatException("invalid format: " + xpub);
                } else {
                    return true;
                }
            }
        } catch (Exception var8) {
            return false;
        }
    }
}
