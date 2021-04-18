package com.jws1g18.myphrplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;

import co.junwei.bswabe.BswabeMsk;
import co.junwei.bswabe.BswabePrv;
import co.junwei.bswabe.BswabePub;

class MyphrplusApplicationTests {

	static GCPFireBase firebaseInterface;
	static GCPCloudStorage cloudStorageInterface;
	static Logger logger;
	static byte[] testImg = null;

	@BeforeAll
	static void initAll() {
		logger = LoggerFactory.getLogger(MyphrplusApplicationTests.class);
		Helpers helper = new Helpers();
		firebaseInterface = new GCPFireBase(logger, helper);
		cloudStorageInterface = new GCPCloudStorage(logger);

		//Load test image from resources
		try {
			testImg = Thread.currentThread().getContextClassLoader().getResourceAsStream("testImg.jpg").readAllBytes();
		} catch (IOException e) {
			fail("Could not load image, ensure there is a testImg.jpg in resources folder");
		}
	}

	@Test
	void abeTests() {
		logger.info("===ABE TESTS===");
		logger.info("Setup");
		Object[] setup = ABE.setup();

		logger.info("Check setup keys are of correct form");
		BswabePub pub = null;
		BswabeMsk msk = null;
		if (setup[0] instanceof BswabePub) {
			pub = (BswabePub) setup[0];
		} else {
			fail("Public parameters returned from setup function could not be cast");
		}
		if (setup[1] instanceof BswabeMsk) {
			msk = (BswabeMsk) setup[1];
		} else {
			fail("Master key returned from setup function could not be cast");
		}

		logger.info("Generate private keys");
		String[] attr1 = new String[] {"Foo", "Bar"};
		String[] attr2 = new String[] {"Baz", "Qux"};
		String[] longAtr = new String[] {"Foo", "Bar", "Baz", "Qux", "quux", "quuz", "corge", "grault", "garply", "waldo", "fred", "plugh", "xyzzy", "thud"};
		String[] empty = new String[] {};
		BswabePrv prv1 = ABE.genPrivKey(pub, msk, attr1);
		assertNotNull(prv1, "Private key 1 could not be generated");
		BswabePrv prv2 = ABE.genPrivKey(pub, msk, attr2);
		assertNotNull(prv2, "Private key 2 could not be generated");
		BswabePrv prvLong = ABE.genPrivKey(pub, msk, longAtr);
		assertNotNull(prvLong, "Long Private key could not be generated");
		BswabePrv prvEmpty = ABE.genPrivKey(pub, msk, empty);
		assertNull(prvEmpty, "Empty Private key was generated");

		logger.info("Delegate private keys");
		String[] validSubset = new String[] {"Foo", "Bar", "Baz"};
		String[] invalidSubset = new String[] {"Mike", "John", "Oliver"};
		BswabePrv validDelPriv = ABE.delegatePrivKey(pub, prvLong, validSubset);
		BswabePrv invalidDelPriv = ABE.delegatePrivKey(pub, prvLong, invalidSubset);
		assertNotNull(validDelPriv, "Valid key delegation failed");
		assertNull(invalidDelPriv, "Invalid key delegation failed");

		logger.info("Encrypt file");

		String validPolicy = "Foo Bar 2of2";
		String validPolicy2 = "Mike John 2of2 Qux quux quuz 1of3 1of2";
		String invalidPolicy = "Baz Quz 2of1";
		String emptyPolicy = "";
		
		byte[] encFile1 = ABE.encrypt(pub, validPolicy, testImg);
		assertNotNull(encFile1, "Error occured while encrypting with validPolicy");
		byte[] encFile2 = ABE.encrypt(pub, validPolicy2, testImg);
		assertNotNull(encFile2, "Error occured while encrypting with validPolicy2");
		byte[] encFile3 = ABE.encrypt(pub, invalidPolicy, testImg);
		assertNull(encFile3, "File was encrypted by invalid policy");
		byte[] encFile4 = ABE.encrypt(pub, emptyPolicy, testImg);
		assertNull(encFile4, "File was encrypted by empty policy");

		assertNotEquals(encFile1, testImg, "File was not encrypted with validPolicy");
		assertNotEquals(encFile2, testImg, "File was not encrypted with validPolicy2");
		assertNotEquals(encFile4, testImg, "File was not encrypted with emptyPolicy");

		logger.info("Decrypt file");

		//Decrypt function takes in inputstream so must convert byte[] to inputstream
		byte[] decFile1 = ABE.decrypt(pub, prv1, new ByteArrayInputStream(encFile1));
		assertNotNull(decFile1, "Error occured while decrypting encFile1 with private key 1");
		assertArrayEquals(decFile1, testImg, "encFile1 was not decrypted correctly");
		byte[] decFile2 = ABE.decrypt(pub, prv2, new ByteArrayInputStream(encFile1));
		assertNull(decFile2, "Private key with incorrect attributes was able to decrypt encFile1");
		byte[] decFile3 = ABE.decrypt(pub, prvLong, new ByteArrayInputStream(encFile2));
		assertNotNull(decFile3, "Error occured while decrypting encFile2 with private key long");
		assertArrayEquals(decFile3, testImg, "encFile2 was not decrypted correctly");
		byte[] decFile4 = ABE.decrypt(pub, prv1, new ByteArrayInputStream(encFile2));
		assertNull(decFile4, "Private key with incorrect attributes was able to decrypt encFile2");

		logger.info("===ABE TESTS PASSED===\n");
	}

	@Test
	void secretManagerTests(){
		logger.info("===SECRET MANAGER TESTS===");
		String stringToBeStored = "This is a simple test string that will be stored in GCP Secret manager";
		String updateString = "This is updated simple test string";
		String secretName = "TEST-" + RandomStringUtils.randomAlphabetic(40);

		logger.info("Store secret");
		try {
			Boolean res = GCPSecretManager.storeKey(secretName, stringToBeStored.getBytes());
			assertTrue(res);
		} catch (IOException e) {
			logger.error("Error occured while storing secret", e);
			fail("Secret could not be stored");
		}

		logger.info("Retreve secret");
		try {
			byte[] res = GCPSecretManager.getKeys(secretName);
			String resString = new String(res, StandardCharsets.UTF_8);
			assertEquals(stringToBeStored, resString, "Retreved string is not same as stored");
		} catch (IOException e) {
			logger.error("Error occured while getting secret", e);
			fail("Secret could not be retreved");
		}

		logger.info("Destroy secret");
		try {
			Boolean res = GCPSecretManager.destroySecretVersion(secretName);
			assertTrue(res);
		} catch (IOException e) {
			logger.error("Error occured while destroying secret", e);
			fail("Secret could not be destroyed");
		}
		try { //Should no longer be able to retreve data
			GCPSecretManager.getKeys(secretName);
			fail("Secret should be in destroyed state");
		} catch (IOException e) {
			logger.error("Error occured while getting secret", e);
			fail("Secret could not be retreved");
		} catch(FailedPreconditionException e){
			//This should fail :)
			//FailedPreconditionException is thrown when the secret you are trying to access is in a destoryed state
		}

		logger.info("Update secret");
		try {
			Boolean res =  GCPSecretManager.addSecretVersion(secretName, updateString.getBytes());
			assertTrue(res);
		} catch (IOException e) {
			logger.error("Error occured while updating secret", e);
			fail("Secret could not be updated");
		}
		try {
			byte[] res = GCPSecretManager.getKeys(secretName);
			String resString = new String(res, StandardCharsets.UTF_8);
			assertEquals(updateString, resString, "Retreved string is not same as stored");
		} catch (IOException e) {
			logger.error("Error occured while getting secret", e);
			fail("Secret could not be retreved");
		}

		//Cleanup
		try {
			GCPSecretManager.destroySecretVersion(secretName);
		} catch (IOException e) {
			logger.error("Secret couldn't be destroyed during cleanup", e);
		}
		logger.info("===SECRET MANAGER TESTS PASSED===\n");
	}

	@Test
	void fireBaseTests(){
		logger.info("===FIREBASE TESTS===");
		logger.info("Register new user with auth");

		String uid = null;
		try {
			UserRecord record = firebaseInterface.registerUser("TestUser", "test@test.com", "password");
			assertNotNull(record);
			uid = record.getUid();
		} catch (FirebaseAuthException e) {
			logger.error("Could not create user", e);
			fail("Could not create user");
		}

		logger.info("Add info to firestore");
		Map<String, Object> data = new HashMap<>();
		data.put("name", "test");
		data.put("role", "testUser");
		data.put("testArray", new ArrayList<String>());
		try {
			WriteResult writeRes = firebaseInterface.addUser(data, uid);
			assertNotNull(writeRes);
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error occured while adding user to firebase", e);
			fail("Couldn't add a new user to firebase");
		}

		logger.info("Update some info");
		try {
			WriteResult writeRes = firebaseInterface.updateField("users", uid, "name", "Sir Test");
			assertNotNull(writeRes);
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error occured while updating firestore", e);
			fail("Couldn't update field");
		}
		try {
			firebaseInterface.updateField("users", "doesntExist", "name", "Sir Test");
			fail("Updated filed on document that doesnt exist");
		} catch (InterruptedException | ExecutionException e) {} //Should fail 
		try{
			WriteResult writeRes = firebaseInterface.updateArray(uid, "testArray", "Cat");
			assertNotNull(writeRes);
		}catch (InterruptedException | ExecutionException e) {
			logger.error("Error occured while updating firestore array", e);
			fail("Couldn't update array");
		}
		try{
			firebaseInterface.updateArray("doesntExist", "testArray", "Cat");
			fail("Updated array on document that doesnt exist");
		}catch (InterruptedException | ExecutionException e) {}//Should fail

		logger.info("Query database");
		try {
			QuerySnapshot querySnapshot = firebaseInterface.queryUsers("role", "testUser");
			String docID = querySnapshot.getDocuments().get(0).getId();
			assertEquals(uid, docID, "Couldn't find correct document");
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Error occured while querying", e);
			fail("Quey failed");
		}

		logger.info("Remove firestore entry");
		FunctionResponse removeResponse = firebaseInterface.deleteUserFirestore(uid);
		assertTrue(removeResponse.successful(), "Failed when deleting user from firestore");


		logger.info("Delete user on auth");
		FunctionResponse deleteResponse = firebaseInterface.deleteUserAuth(uid);
		assertTrue(deleteResponse.successful(), "Failed when deleting user from firebase auth");
		logger.info("===FIREBASE TESTS PASSED===\n");
	}

	@Test
	void cloudStorageTests(){
		logger.info("===CLOUD STORAGE TESTS===");
		//Use bucket: test-bucket-myphrplus-backend

		logger.info("Upload");
		FunctionResponse uploadRes = cloudStorageInterface.uploadFile("test-bucket-myphrplus-backend", "test", testImg, "img/jpg");
		assertTrue(uploadRes.successful(), "File could not be uploaded");
		FunctionResponse uploadRes2 = cloudStorageInterface.uploadFile("doesnt-exist-bucket-myphrplus-backend", "test", testImg, "img/jpg");
		assertFalse(uploadRes2.successful());

		logger.info("Download");
		ByteArrayResource download = cloudStorageInterface.downloadObject("test-bucket-myphrplus-backend", "test");
		assertArrayEquals(testImg, download.getByteArray(), "File downloaded was no the same as uploaded");
		ByteArrayResource download2 = cloudStorageInterface.downloadObject("test-bucket-myphrplus-backend", "doesntExist");
		assertNull(download2);
		ByteArrayResource download3 = cloudStorageInterface.downloadObject("doesnt-exist-bucket-myphrplus-backend", "test");
		assertNull(download3);

		logger.info("Delete");
		Boolean res = cloudStorageInterface.deleteFile("test-bucket-myphrplus-backend", "test");
		assertTrue(res, "File could not be deleted");

		logger.info("===CLOUD STORAGE TESTS PASSED===\n");
	}
}
