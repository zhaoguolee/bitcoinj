package org.bitcoinj.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

public class CharSequenceX implements CharSequence {
    private char[] chars;
    private int rounds;

    private CharSequenceX(CharSequence charSequence, int start, int end) {
        this.rounds = 100;
        this.zap();
        int len = end - start;
        this.chars = new char[len];

        for(int i = start; i < end; ++i) {
            this.chars[i - start] = charSequence.charAt(i);
        }

    }

    public CharSequenceX(int len) {
        this.rounds = 100;
        this.chars = new char[len];
    }

    public CharSequenceX(CharSequence charSequence) {
        this(charSequence, 0, charSequence.length());
    }

    public CharSequenceX(char[] chars) {
        this.rounds = 100;
        this.zap();
        this.chars = chars;
    }

    public void zap() {
        if (this.chars != null) {
            for(int i = 0; i < this.rounds; ++i) {
                this.fill('0');
                this.rfill();
                this.fill('0');
            }
        }

    }

    public void setRounds(int rounds) {
        if (rounds < 100) {
            this.rounds = 100;
        } else {
            this.rounds = rounds;
        }

    }

    public char charAt(int index) {
        return this.chars != null ? this.chars[index] : '\u0000';
    }

    public int length() {
        return this.chars != null ? this.chars.length : 0;
    }

    public String toString() {
        return new String(this.chars);
    }

    public boolean equals(Object o) {
        return o instanceof CharSequenceX ? Arrays.equals(this.chars, ((CharSequenceX)o).chars) : false;
    }

    public CharSequence subSequence(int start, int end) {
        CharSequenceX s = new CharSequenceX(this, start, end);
        return s;
    }

    protected void finalize() {
        this.zap();
    }

    private void fill(char c) {
        for(int i = 0; i < this.chars.length; ++i) {
            this.chars[i] = c;
        }

    }

    private void rfill() {
        SecureRandom r = new SecureRandom();
        byte[] b = new byte[this.chars.length];
        r.nextBytes(b);

        for(int i = 0; i < this.chars.length; ++i) {
            this.chars[i] = (char)b[i];
        }

    }
}
