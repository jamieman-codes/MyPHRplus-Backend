package com.jws1g18.myphrplus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.commons.lang3.SerializationUtils;

import co.junwei.bswabe.Bswabe;
import co.junwei.bswabe.BswabeCph;
import co.junwei.bswabe.BswabeCphKey;
import co.junwei.bswabe.BswabeElementBoolean;
import co.junwei.bswabe.BswabeMsk;
import co.junwei.bswabe.BswabePrv;
import co.junwei.bswabe.BswabePub;
import co.junwei.bswabe.SerializeUtils;
import co.junwei.cpabe.Common;
import it.unisa.dia.gas.jpbc.Element;

public class ABE {

    public static void main(String[] args) throws Exception{

        //Generate a public key and corresponding master secret key.
        Object[] setup = setup();
        BswabePub pub = (BswabePub) setup[0];
        BswabeMsk msk = (BswabeMsk) setup[1];
        
        //Generate private key for user
        BswabePrv prv = genPrivKey(pub, msk, attr);

        BswabePrv del_prv = Bswabe.delegate(pub, prv, new String[] {"foo"});

        //Encrypt
        byte[] encFile = encrypt(pub, policy, Common.suckFile(inputfile));

        //Decrypt
        byte[] plt = decrypt(pub, prv, new ByteArrayInputStream(encFile));
        if(plt != null){
		    Common.spitFile(decfile, plt);
        }
    }

    /**
     * Generates public key and master key
     * @return Object array in the form {Public key, Master Key}
     */
    public static Object[] setup(){
        BswabePub pub = new BswabePub();
		BswabeMsk msk = new BswabeMsk();
        Bswabe.setup(pub, msk);
        return new Object[]{pub, msk};
    }

    /**
     * Generates a users private key based upon their attributes
     * @param pub Public Key
     * @param msk Master Key
     * @param attr Attribute array
     * @return Private key 
     */
    public static BswabePrv genPrivKey(BswabePub pub, BswabeMsk msk, String[] attr){
        try {
            return Bswabe.keygen(pub, msk, attr);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Delegates a new private key given a private key and a subset of its attributes
     * @param pub Public Key
     * @param prv Private key
     * @param attr Attribute subset
     * @return New private key
     */
    public static BswabePrv delegatePrivKey(BswabePub pub, BswabePrv prv, String[] attr){
        try {
            return Bswabe.delegate(pub, prv, attr);
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Encrypts a file based upon the policy specifed.
     * Example policy: foo bar fim 2of3 baf 1of2
     * This is read as (((foo bar fim) 2of3) baf 1of2)
     * This means a user either needs 2 of foo, bar, fim OR baf as attributes in order to decrypt 
     * @param pub Public key used to encrypt
     * @param policy string with policy
     * @param file Byte array containing file
     * @return Byte array of encrypted file
     * @throws Exception 
     */
    public static byte[] encrypt(BswabePub pub, String policy, byte[] file) throws Exception{
        //Encrypt the file, returns the cipher text and the key
        BswabeCphKey cphKey = Bswabe.enc(pub, policy);
        BswabeCph cph = cphKey.cph;
        Element key = cphKey.key;

        //Returns null if an error occured during encryption 
        if(cph == null){
            throw new Exception();
        }

        //Use ciphertext as symmetric key for hybrid encryption (ABE only encrypts one group element)
        byte[] aesBuf = aes(key, file, Cipher.ENCRYPT_MODE); 

        //Store 
        byte[] cphBuf = SerializeUtils.bswabeCphSerialize(cph);
        byte[] encFile = writeEncFile(aesBuf, cphBuf);
        return encFile;
    }

    /**
     * Decrypts a file given a private key that satifiys the encryption policy
     * @param pub Public key 
     * @param prv Private Key
     * @param file Encrypted file
     * @return Byte array of decrypted file, or Null if decryption failed 
     * @throws Exception Returns an exception if decryption fails
     */
    public static byte[] decrypt(BswabePub pub, BswabePrv prv, InputStream file) throws Exception{
        //Read file
        EncFile tmp = readEncFile(file);
        byte[] aesBuf = tmp.aesBuf;
		byte[] cphBuf = tmp.cphBuf;

        //Read cipher text
        BswabeCph cph = SerializeUtils.bswabeCphUnserialize(pub, cphBuf);
        //Decrypt cipher text
        BswabeElementBoolean beb = Bswabe.dec(pub, prv, cph);
        if (beb.b) {
            //Decrypt file using cipher text as symmetric key 
			return aes(beb.e, aesBuf, Cipher.DECRYPT_MODE); 
		} else {
			return null;
		}
    }
    
    /**
     * Encrypts or Decrypts a file using AES, generating an AES Key using a ABE Ciphertext as a seed
     * @param key ABE Ciphertext
     * @param file File to be encrypted
     * @param aesMode Whether to encrypt or decrypt 
     * @return Encrypted/Decrypted File  
     * @throws Exception
     */
    private static byte[] aes(Element key, byte[] file, int aesMode) throws Exception{
        // Generate key
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(key.toBytes());
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128, secureRandom); 
        SecretKey secretKey = keyGen.generateKey();

        // Encrypt/Decrypt 
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(aesMode, secretKey);
        return cipher.doFinal(file);
    }

    private static byte[] writeEncFile(byte[] aesBuf, byte[] cphBuf){
        EncFile file = new EncFile(aesBuf, cphBuf);
        return SerializationUtils.serialize(file);
    }

    private static EncFile readEncFile(InputStream iStream){
        try {
            return (EncFile) SerializationUtils.deserialize(iStream.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }
}
