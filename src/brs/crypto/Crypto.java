package brs.crypto;

import atmcash.kit.crypto.AtmCrypto;
import atmcash.kit.entity.AtmID;
import org.bouncycastle.jcajce.provider.digest.MD5;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Crypto {
  static final AtmCrypto atmCrypto = AtmCrypto.getInstance();

  private Crypto() {
  } //never

  public static MessageDigest sha256() {
    return atmCrypto.getSha256();
  }

  public static MessageDigest shabal256() {
    return atmCrypto.getShabal256();
  }

  public static MessageDigest ripemd160() {
    return atmCrypto.getRipeMD160();
  }

  public static MessageDigest md5() {// TODO unit test
    try {
      return MessageDigest.getInstance("MD5"); // TODO atmkit4j integration
    } catch (NoSuchAlgorithmException e) {
      return new MD5.Digest();
    }
  }

  public static byte[] getPublicKey(String secretPhrase) {
    return atmCrypto.getPublicKey(secretPhrase);
  }

  public static byte[] getPrivateKey(String secretPhrase) {
    return atmCrypto.getPrivateKey(secretPhrase);
  }

  public static byte[] sign(byte[] message, String secretPhrase) {
      return atmCrypto.sign(message, secretPhrase);
  }

  public static boolean verify(byte[] signature, byte[] message, byte[] publicKey, boolean enforceCanonical) {
      return atmCrypto.verify(signature, message, publicKey, enforceCanonical);
  }

  public static byte[] aesEncrypt(byte[] plaintext, byte[] myPrivateKey, byte[] theirPublicKey) {
    return atmCrypto.aesSharedEncrypt(plaintext, myPrivateKey, theirPublicKey);
  }

  public static byte[] aesEncrypt(byte[] plaintext, byte[] myPrivateKey, byte[] theirPublicKey, byte[] nonce) {
    return atmCrypto.aesSharedEncrypt(plaintext, myPrivateKey, theirPublicKey, nonce);
  }

  public static byte[] aesDecrypt(byte[] ivCiphertext, byte[] myPrivateKey, byte[] theirPublicKey) {
    return atmCrypto.aesSharedDecrypt(ivCiphertext, myPrivateKey, theirPublicKey);
  }

  public static byte[] aesDecrypt(byte[] ivCiphertext, byte[] myPrivateKey, byte[] theirPublicKey, byte[] nonce) {
    return atmCrypto.aesSharedDecrypt(ivCiphertext, myPrivateKey, theirPublicKey, nonce);
  }

  public static byte[] getSharedSecret(byte[] myPrivateKey, byte[] theirPublicKey) {
    return atmCrypto.getSharedSecret(myPrivateKey, theirPublicKey);
  }

  public static String rsEncode(long id) {
    return atmCrypto.rsEncode(AtmID.fromLong(id));
  }

  public static long rsDecode(String rsString) {
    return atmCrypto.rsDecode(rsString).getSignedLongId();
  }
}
