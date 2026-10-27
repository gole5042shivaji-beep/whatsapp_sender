package com.gcctbc.sender;

import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-CBC + HMAC-SHA256 : "GTE1" | IV(16) | CipherText | HMAC(32). Windows ॲपमधील FileCrypt शी सुसंगत. */
final class FileCrypt {
    private static final byte[] A = { (byte)0xB9, (byte)0x97, (byte)0xB8, (byte)0x7B, (byte)0x56, (byte)0xD0, (byte)0x31, (byte)0x4F, (byte)0xF3, (byte)0x46, (byte)0x6D, (byte)0x80, (byte)0xD2, (byte)0xD5, (byte)0x64, (byte)0x96, (byte)0x70, (byte)0x2E, (byte)0x65, (byte)0x40, (byte)0xDD, (byte)0x9E, (byte)0x8E, (byte)0x8A, (byte)0x6C, (byte)0x05, (byte)0xB6, (byte)0xDC, (byte)0xC6, (byte)0x07, (byte)0x12, (byte)0xFA };
    private static final byte[] B = { (byte)0x99, (byte)0x49, (byte)0x7F, (byte)0x9D, (byte)0xEF, (byte)0xDF, (byte)0xEE, (byte)0xF8, (byte)0x97, (byte)0x88, (byte)0xA3, (byte)0xAC, (byte)0x14, (byte)0xCF, (byte)0x9F, (byte)0x9B, (byte)0x48, (byte)0x9A, (byte)0x27, (byte)0x71, (byte)0xC1, (byte)0x72, (byte)0xD4, (byte)0xB2, (byte)0x8A, (byte)0x0C, (byte)0x9C, (byte)0xEA, (byte)0x15, (byte)0x68, (byte)0x70, (byte)0xA7 };
    private FileCrypt() {}

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }

    static byte[] decrypt(byte[] d) throws Exception {
        byte[] master = new byte[32];
        for (int i = 0; i < 32; i++) master[i] = (byte) (A[i] ^ B[i]);
        byte[] ek = hmac(master, "gcctbc-enc".getBytes("US-ASCII"));
        byte[] mk = hmac(master, "gcctbc-mac".getBytes("US-ASCII"));
        if (d == null || d.length < 4 + 16 + 16 + 32) throw new Exception("bad file");
        if (d[0] != 'G' || d[1] != 'T' || d[2] != 'E' || d[3] != '1') throw new Exception("bad magic");
        int bodyLen = d.length - 32;
        byte[] body = new byte[bodyLen];
        System.arraycopy(d, 0, body, 0, bodyLen);
        byte[] tag = hmac(mk, body);
        if (!MessageDigest.isEqual(tag, java.util.Arrays.copyOfRange(d, bodyLen, d.length))) throw new Exception("tampered");
        byte[] iv = java.util.Arrays.copyOfRange(d, 4, 20);
        byte[] ct = java.util.Arrays.copyOfRange(d, 20, bodyLen);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(ek, "AES"), new IvParameterSpec(iv));
        return c.doFinal(ct);
    }
}
