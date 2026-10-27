using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace GccSender
{
    /// <summary>AES-256-CBC + HMAC-SHA256 : "GTE1" | IV(16) | CipherText | HMAC(32). Android ॲपशी सुसंगत.</summary>
    internal static class FileCrypt
    {
        private static readonly byte[] A = { 0xB9, 0x97, 0xB8, 0x7B, 0x56, 0xD0, 0x31, 0x4F, 0xF3, 0x46, 0x6D, 0x80, 0xD2, 0xD5, 0x64, 0x96, 0x70, 0x2E, 0x65, 0x40, 0xDD, 0x9E, 0x8E, 0x8A, 0x6C, 0x05, 0xB6, 0xDC, 0xC6, 0x07, 0x12, 0xFA };
        private static readonly byte[] B = { 0x99, 0x49, 0x7F, 0x9D, 0xEF, 0xDF, 0xEE, 0xF8, 0x97, 0x88, 0xA3, 0xAC, 0x14, 0xCF, 0x9F, 0x9B, 0x48, 0x9A, 0x27, 0x71, 0xC1, 0x72, 0xD4, 0xB2, 0x8A, 0x0C, 0x9C, 0xEA, 0x15, 0x68, 0x70, 0xA7 };

        public static byte[] Decrypt(byte[] d)
        {
            var master = new byte[32];
            for (int i = 0; i < 32; i++) master[i] = (byte)(A[i] ^ B[i]);
            byte[] ek, mk;
            using (var h = new HMACSHA256(master))
            {
                ek = h.ComputeHash(Encoding.ASCII.GetBytes("gcctbc-enc"));
                mk = h.ComputeHash(Encoding.ASCII.GetBytes("gcctbc-mac"));
            }
            if (d == null || d.Length < 4 + 16 + 16 + 32) throw new InvalidDataException("bad file");
            if (d[0] != 0x47 || d[1] != 0x54 || d[2] != 0x45 || d[3] != 0x31) throw new InvalidDataException("bad magic");
            int bodyLen = d.Length - 32;
            var body = new byte[bodyLen];
            Buffer.BlockCopy(d, 0, body, 0, bodyLen);
            byte[] tag;
            using (var h = new HMACSHA256(mk)) tag = h.ComputeHash(body);
            int diff = 0;
            for (int i = 0; i < 32; i++) diff |= tag[i] ^ d[bodyLen + i];
            if (diff != 0) throw new InvalidDataException("tampered");
            var iv = new byte[16];
            Buffer.BlockCopy(d, 4, iv, 0, 16);
            var ct = new byte[bodyLen - 20];
            Buffer.BlockCopy(d, 20, ct, 0, ct.Length);
            using (var aes = Aes.Create())
            {
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;
                aes.Key = ek;
                aes.IV = iv;
                using (var dec = aes.CreateDecryptor())
                    return dec.TransformFinalBlock(ct, 0, ct.Length);
            }
        }
    }
}
