//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.bitcoinj.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.PBEParametersGenerator;
import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.BlockCipherPadding;
import org.spongycastle.crypto.paddings.ISO10126d2Padding;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;

public class AESUtil {
    public static final int PinPbkdf2Iterations = 5000;
    public static final int QrCodePbkdf2Iterations = 10;

    public AESUtil() {
    }

    private static byte[] copyOfRange(byte[] source, int from, int to) {
        byte[] range = new byte[to - from];
        System.arraycopy(source, from, range, 0, range.length);
        return range;
    }

    public static String decrypt(String ciphertext, char[] password, int iterations) {
        boolean AESBlockSize = true;
        byte[] cipherdata = Base64.getDecoder().decode(ciphertext.getBytes());
        byte[] iv = copyOfRange(cipherdata, 0, 16);
        byte[] input = copyOfRange(cipherdata, 16, cipherdata.length);
        PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
        generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password), iv, iterations);
        KeyParameter keyParam = (KeyParameter)generator.generateDerivedParameters(256);
        CipherParameters params = new ParametersWithIV(keyParam, iv);
        BlockCipherPadding padding = new ISO10126d2Padding();
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding);
        cipher.reset();
        cipher.init(false, params);
        byte[] buf = new byte[cipher.getOutputSize(input.length)];
        int len = cipher.processBytes(input, 0, input.length, buf, 0);

        try {
            len += cipher.doFinal(buf, len);
        } catch (InvalidCipherTextException var18) {
            return null;
        }

        byte[] out = new byte[len];
        System.arraycopy(buf, 0, out, 0, len);
        String ret = null;

        ret = new String(out, StandardCharsets.UTF_8);
        return ret;
    }

    public static String encrypt(String cleartext, char[] password, int iterations) {
        boolean AESBlockSize = true;
        if (password == null) {
            return null;
        } else {
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            Object var6 = null;

            byte[] clearbytes;
            clearbytes = cleartext.getBytes(StandardCharsets.UTF_8);

            PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
            generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password), iv, iterations);
            KeyParameter keyParam = (KeyParameter)generator.generateDerivedParameters(256);
            CipherParameters params = new ParametersWithIV(keyParam, iv);
            BlockCipherPadding padding = new ISO10126d2Padding();
            BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding);
            cipher.reset();
            cipher.init(true, params);
            byte[] outBuf = cipherData(cipher, clearbytes);
            int len1 = iv.length;
            int len2 = outBuf.length;
            byte[] ivAppended = new byte[len1 + len2];
            System.arraycopy(iv, 0, ivAppended, 0, len1);
            System.arraycopy(outBuf, 0, ivAppended, len1, len2);
            byte[] raw = Base64.getEncoder().encode(ivAppended);
            String ret = new String(raw);
            return ret;
        }
    }

    private static byte[] cipherData(BufferedBlockCipher cipher, byte[] data) {
        int minSize = cipher.getOutputSize(data.length);
        byte[] outBuf = new byte[minSize];
        int len1 = cipher.processBytes(data, 0, data.length, outBuf, 0);
        int len2 = -1;

        try {
            len2 = cipher.doFinal(outBuf, len1);
        } catch (InvalidCipherTextException var8) {
            var8.printStackTrace();
        }

        int actualLength = len1 + len2;
        byte[] result = new byte[actualLength];
        System.arraycopy(outBuf, 0, result, 0, result.length);
        return result;
    }
}
