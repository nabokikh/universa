/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.*;
import com.icodici.crypto.digest.Sha3_384;
import com.icodici.crypto.digest.Sha512;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.time.ZonedDateTime;

/**
 * The extended signature signs the resource with sha512, timestamp and 32-byte key id, see {@link #keyId}. The signed
 * timestamp seals the signature creation time. The whole extended signature then is signed by the provided private key.
 * This not only provides timestamps in signatures, but also repels theoretically possible chosen-text type attacks on
 * source documents, as actual signature is calculated twice and includes timestamp.
 * <p>
 * Use {@link #keyId} to get an id of a public or private key (for a keypair, the id is the same for both private and
 * public key).
 * <p>
 * Use {@link #extractKeyId(byte[])} to get key id from a packed signature.
 */
public class ExtendedSignature {

    public Bytes getKeyId() {
        return keyId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * return keyId (see {@link #keyId} of the key that was used to sign data.
     */
    private Bytes keyId;
    private ZonedDateTime createdAt;
    private PublicKey publicKey = null;

    /**
     * Sign the data with a given key.
     *
     * @param key is {@link PrivateKey} to sign with
     * @param data to be sign with key
     *
     * @return binary signature
     */
    static public byte[] sign(PrivateKey key, byte[] data) {
        return sign(key, data, true);
    }

    /**
     * Sign the data with a given key.
     *
     * @param key is {@link PrivateKey} to sign with.
     * @param data to be sign with key.
     * @param savePublicKey if true key will stored in the {@link ExtendedSignature}.
     *
     * @return binary signature
     */
    static public byte[] sign(PrivateKey key, byte[] data, boolean savePublicKey) {
        try {
            byte[] targetSignature = ExtendedSignature.createTargetSignature(key.getPublicKey(),data,savePublicKey);

            return ExtendedSignature.of(targetSignature,
                    key.sign(targetSignature, HashType.SHA512),
                    key.sign(targetSignature, HashType.SHA3_384));

        } catch (EncryptionError e) {
            throw new RuntimeException("signature failed", e);
        }
    }



    static public byte[] createTargetSignature(PublicKey publicKey, byte[] data, boolean savePublicKey) {
        Binder targetSignatureBinder = Binder.fromKeysValues(
                "key", keyId(publicKey),
                "sha512", new Sha512().digest(data),
                "sha3_384", new Sha3_384().digest(data),
                "created_at", ZonedDateTime.now()
        );
        if (savePublicKey)
            targetSignatureBinder.put("pub_key", publicKey.pack());
        byte[] targetSignature = Boss.pack(targetSignatureBinder);
        return targetSignature;
    }

    static public byte[] of(byte[] targetSignature, byte[] sign, byte[] sign2) {
        Binder result = Binder.fromKeysValues(
                "exts", targetSignature,
                "sign", sign,
                "sign2", sign2
        );
        return Boss.pack(result);
    }

    /**
     * Calculate keyId for a given key (should be either {@link PublicKey} or {@link PrivateKey}). the keyId is the same
     * for public and private key and can be used to store/access keys in Map ({@link Bytes} instances can be used as
     * Map keys.
     * <p>
     * Use {@link #extractKeyId(byte[])} to get a keyId from a packed extended signature, find the proper key, than
     * {@link #verify(PublicKey, byte[], byte[])} the data. It uses corresponding {@link PublicKey#fingerprint()}.
     *
     * @param key key to calculate Id
     *
     * @return calculated key id
     */
    static public Bytes keyId(AbstractKey key) {
        if (key instanceof PrivateKey)
            return new Bytes(key.getPublicKey().fingerprint());
        return new Bytes(key.fingerprint());
    }

    /**
     * Get the keyId (see {@link #keyId}) from a packed binary signature. This method can be used to find proper public
     * key when signing with several keys.
     *
     * @param signature to extrack keyId from
     *
     * @return the keyId instance as {@link Bytes}
     */
    public static Bytes extractKeyId(byte[] signature) {
        Binder src = Boss.unpack(signature);
        return Boss.unpack(src.getBinaryOrThrow("exts")).getBytesOrThrow("key");
    }

    /**
     * Get the keyId (see {@link #keyId}) from a packed binary signature. This method can be used to find proper public
     * key when signing with several keys.
     *
     * @param signature to extrack keyId from
     *
     * @return the keyId instance as {@link Bytes}
     */
    public static PublicKey extractPublicKey(byte[] signature) {
        Binder src = Boss.unpack(signature);
        PublicKey publicKey = null;
        byte[] exts = src.getBinaryOrThrow("exts");
        Binder b = Boss.unpack(exts);
        try {
            byte[] publicKeyBytes = b.getBinaryOrThrow("pub_key");
            publicKey = new PublicKey(publicKeyBytes);
        } catch (EncryptionError e) {
            publicKey = null;
        } catch (IllegalArgumentException e) {
            publicKey = null;
        }

        return publicKey;
    }


    private byte[] signature;
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Unpack and the extended signature. On success, returns instance of the {@link ExtendedSignature} with a decoded
     * timestamp, {@link #getCreatedAt()}
     *
     * @param key       to verify signature with
     * @param signature the binary extended signature
     * @param data      signed data
     *
     * @return null if the signature is invalud, {@link ExtendedSignature} instance on success.
     */
    public static ExtendedSignature verify(PublicKey key, byte[] signature, byte[] data) {
        try {
            Binder src = Boss.unpack(signature);
            ExtendedSignature es = new ExtendedSignature();

            byte[] exts = src.getBinaryOrThrow("exts");
            boolean isSignValid = key.verify(exts, src.getBinaryOrThrow("sign"), HashType.SHA512);
            boolean isSign2Valid = true;
            byte[] sign2bin = null;
            try {
                sign2bin = src.getBinaryOrThrow("sign2");
            } catch (IllegalArgumentException e) {
                sign2bin = null;
            }
            if (sign2bin != null)
                isSign2Valid = key.verify(exts, sign2bin, HashType.SHA3_384);
            if (isSignValid && isSign2Valid) {
                Binder b = Boss.unpack(exts);
                es.keyId = b.getBytesOrThrow("key");
                es.createdAt = b.getZonedDateTimeOrThrow("created_at");
                es.signature = signature;
                es.publicKey = null;
                try {
                    byte[] publicKeyBytes = b.getBinaryOrThrow("pub_key");
                    es.publicKey = new PublicKey(publicKeyBytes);
                } catch (IllegalArgumentException e) {
                    es.publicKey = null;
                }
                Bytes hash = b.getBytesOrThrow("sha512");
                Bytes dataHash = new Bytes(new Sha512().digest(data));
                boolean isHashValid = hash.equals(dataHash);
                Bytes hash2 = null;
                boolean isHash2Valid = true;
                try {
                    hash2 = b.getBytesOrThrow("sha3_384");
                } catch (IllegalArgumentException e) {
                    hash2 = null;
                }
                if (hash2 != null) {
                    Bytes dataHash2 = new Bytes(new Sha3_384().digest(data));
                    isHash2Valid = hash2.equals(dataHash2);
                }
                if (isHashValid && isHash2Valid)
                    return es;
            }
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
        return null;
    }
}
