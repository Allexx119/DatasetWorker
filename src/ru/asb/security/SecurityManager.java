package ru.asb.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.Serializable;
import java.security.Key;
import java.security.SecureRandom;

public class SecurityManager {
    private static final SecurityManager securityManager = new SecurityManager();
    private final String encryptionAlgorithm = "AES";
    private final int keyBitSize = 256;
    private static final Logger log = LogManager.getLogger(SecurityManager.class);

    private SecurityManager() {
    }

    public static SecurityManager getInstance() {
        return securityManager;
    }

    public SecurityObject encrypt(byte[] original) {
        try {
            //Инициализация ключа.
            KeyGenerator keyGenerator = KeyGenerator.getInstance(encryptionAlgorithm);
            keyGenerator.init(keyBitSize, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();

            //нициализация шифра, и шифрование данных.
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] cipherByteArray = cipher.doFinal(original);

            return new SecurityObject(cipherByteArray, secretKey);
        } catch (Exception e) {
            log.error(e);
        }
        return null;
    }

    public byte[] decrypt(SecurityObject securityObject) {
        byte[] byteStr = new byte[0];
        try {
            //Инициализация алгоритма шифрования и расшифровка
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            cipher.init(Cipher.DECRYPT_MODE, securityObject.key);
            byteStr = cipher.doFinal(securityObject.cipherString);
        } catch(Exception e) {
            log.error(e);
        }
        return byteStr;
    }




    public static class SecurityObject implements Serializable {
        private final byte[] cipherString;
        private final Key key;

        SecurityObject(byte[] cipherString, Key key) {
            this.cipherString = cipherString;
            this.key = key;
        }
    }
}
