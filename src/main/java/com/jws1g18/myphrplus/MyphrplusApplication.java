package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.Http;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.rpc.context.AttributeContext.Response;
import com.jws1g18.myphrplus.DTOS.DP;
import com.jws1g18.myphrplus.DTOS.DR;
import com.jws1g18.myphrplus.DTOS.Patient;
import com.jws1g18.myphrplus.DTOS.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.junwei.bswabe.BswabeMsk;
import co.junwei.bswabe.BswabePrv;
import co.junwei.bswabe.BswabePub;
import co.junwei.bswabe.SerializeUtils;

@SpringBootApplication
@RestController
public class MyphrplusApplication {
	Logger logger = LoggerFactory.getLogger(MyphrplusApplication.class);

	Helpers helper = new Helpers();
	GCPFireBase fireBase = new GCPFireBase(logger, helper);
	GCPCloudStorage cloudStorage = new GCPCloudStorage(logger);

	public static void main(String[] args) {
		SpringApplication.run(MyphrplusApplication.class);
	}

	/**
	 * Adds a new patient to the Firestore
	 * 
	 * @param patient JSON containing patient information
	 * @return Returns HTTP response code and message
	 */
	@RequestMapping(method = RequestMethod.POST, path = "/registerPatient")
	public ResponseEntity<?> registerPatient(@RequestBody Patient patient) {
		// Check authentication
		FunctionResponse authResponse = fireBase.verifyUidToken(patient.uid);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Check NHS Num
		if(!fireBase.checkNHSnum(patient.nhsnum)){
			return new ResponseEntity<>("NHS Number has already been registered", HttpStatus.BAD_REQUEST);
		}


		// Create Attribute array
		ArrayList<String> attributes = new ArrayList<>();
		attributes.add("uid_" + authResponse.getMessage());
		attributes.add("Patient");

		// Add patient to firestore
		FunctionResponse addResponse = fireBase.addPatient(authResponse.getMessage(), patient, attributes);
		if (!addResponse.successful()) {
			return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}

		FunctionResponse keyResponse = helper.genAndStorePrivKeys(addResponse.getMessage(),
				attributes.toArray(new String[0]), authResponse.getMessage());
		if (keyResponse.successful()) {
			return new ResponseEntity<>("Patient created successfully", HttpStatus.OK);
		}
		return new ResponseEntity<>("Patient creation failed", HttpStatus.BAD_REQUEST);
	}

	/**
	 * Gets user information
	 * 
	 * @param uid Firebase auth user id token
	 * @return Returns HTTP response code and user information
	 */
	@RequestMapping(method = RequestMethod.GET, path = "/getUser")
	public ResponseEntity<?> getUser(@RequestHeader("Xx-Firebase-Id-Token") String uidToken) {
		// Check authentication
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Get user information
		FunctionResponse getResponse = fireBase.getUser(authResponse.getMessage());
		if (getResponse.successful()) {
			return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.OK);
		} else {
			return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Deletes a user from the firebase and firebase auth
	 * 
	 * @param uidToken Firebase ID token
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, path = "/deleteUser")
	public ResponseEntity<?> deleteUser(@RequestHeader("Xx-Firebase-Id-Token") String uidToken) {
		// Check authentication
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Delete user
		FunctionResponse deleteResponse = fireBase.deleteUser(authResponse.getMessage());
		if (deleteResponse.successful()) {
			return new ResponseEntity<>(deleteResponse.getMessage(), HttpStatus.OK);
		} else {
			return new ResponseEntity<>(deleteResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	/***
	 * Uploads a file to the cloud storage
	 * 
	 * @param uidToken
	 * @param file
	 * @return
	 */
	@RequestMapping(value = "/uploadFilePatient", method = RequestMethod.POST, consumes = { "multipart/form-data" })
	public ResponseEntity<?> uploadFilePatient(@RequestHeader("Xx-Firebase-Id-Token") String uidToken,
			@RequestParam(name = "file") MultipartFile file, @RequestParam(name = "name") String name,
			@RequestParam(name = "customAccessPolicy") String customAccessPolicy,
			@RequestParam(name = "accessPolicy") List<String> accessPolicy) {
		// Check auth token
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Check user role
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("Patient")) {

			// Get Patient object
			Patient user;
			try {
				user = fireBase.getPatient(authResponse.getMessage());
			} catch (InterruptedException | ExecutionException ex) {
				logger.error("Get patient failed", ex);
				return new ResponseEntity<>("Get patient object failed", HttpStatus.BAD_REQUEST);
			}
			// Check file type + extension
			FunctionResponse typeCheck = helper.detectFileType(file);
			if (!typeCheck.successful()) {
				return new ResponseEntity<>(typeCheck.getMessage(), HttpStatus.BAD_REQUEST);
			}
			String extension = typeCheck.getMessage().split(" ")[0];
			String type = typeCheck.getMessage().split(" ")[1];

			// Parse access policy
			String policy = "";
			Boolean custom = false;
			if (accessPolicy.contains("custom")) {
				accessPolicy.remove("custom");
				custom = true;
			}
			if(accessPolicy.size() == 0 && custom){
				long count = customAccessPolicy.chars().filter(ch -> ch == ',').count() + 1;
				if(count == 1){
					policy += customAccessPolicy.replace(",", " ") + " notObtainable 1of2";
				}
				else{
					policy += customAccessPolicy.replace(",", " ") + " " + count + "of" + count;
				}
			}
			else if(accessPolicy.size() == 1 && !custom){
				ArrayList<String> policyUids = fireBase.getUids(accessPolicy, customAccessPolicy,
						authResponse.getMessage(), user);
				for (String uid : policyUids) {
					policy += "uid_" + uid + " ";
				}
				policy += "notObtainable 1of2";
			}
			else if(accessPolicy.size() == 1 && custom){
				ArrayList<String> policyUids = fireBase.getUids(accessPolicy, customAccessPolicy,
						authResponse.getMessage(), user);
				for (String uid : policyUids) {
					policy += "uid_" + uid + " ";
				}
				policy += "notObtainable 1of2 ";
				long count = customAccessPolicy.chars().filter(ch -> ch == ',').count() + 1;
				if(count == 1){
					policy += customAccessPolicy.replace(",", " ") + " notObtainable 1of2 1of2";
				}
				else{
					policy += customAccessPolicy.replace(",", " ") + " " + count + "of" + count + " 1of2";
				}
			}
			else{
				ArrayList<String> policyUids = fireBase.getUids(accessPolicy, customAccessPolicy,
						authResponse.getMessage(), user);
				int x = 0;
				for (String uid : policyUids) {
					x++;
					policy += "uid_" + uid + " ";
				}
				policy += "1of" + x + " ";
				if (custom) {
					long count = customAccessPolicy.chars().filter(ch -> ch == ',').count() + 1;
					if(count == 1){
						policy += customAccessPolicy.replace(",", " ") + " notObtainable 1of2 1of2";
					}
					else{
						policy += customAccessPolicy.replace(",", " ") + " " + count + "of" + count + " 1of2";
					}
				}
			}

			// Encrypt File
			byte[] pubByte;
			try {
				pubByte = GCPSecretManager.getKeys(user.bucketName + "-public");
			} catch (IOException e) {
				return new ResponseEntity<>("Could not get public key", HttpStatus.BAD_REQUEST);
			}
			BswabePub pub = SerializeUtils.unserializeBswabePub(pubByte);
			byte[] encFile;
			try {
				encFile = ABE.encrypt(pub, policy, file.getBytes());
			} catch (Exception e) {
				logger.error("Could not encrypt file", e);
				return new ResponseEntity<>("Could not encrypt file", HttpStatus.BAD_REQUEST);
			}

			// Upload object
			String filepath = user.parent + "/" + authResponse.getMessage() + "/" + name + "." + extension;
			FunctionResponse uploadResponse = cloudStorage.uploadFile(user.bucketName, filepath, encFile, type);
			if (!uploadResponse.successful()) {
				return new ResponseEntity<>(uploadResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}

			// Add refrence to firestore
			Map<String, Object> fileInfo = new HashMap<>();
			fileInfo.put("filepath", filepath);
			fileInfo.put("opened", false);
			fileInfo.put("type", type);
			fileInfo.put("extension", extension);
			fileInfo.put("uploader", authResponse.getMessage());
			fileInfo.put("fileName", name);

			String fileRef;
			try {
				fileRef = fireBase.addFile(fileInfo);
			} catch (InterruptedException | ExecutionException ex) {
				logger.error("Adding file reference to firestore failed", ex);
				return new ResponseEntity<>("Adding file reference to firestore failed", HttpStatus.BAD_REQUEST);
			}

			if(custom){
				accessPolicy.add("custom");
			}

			// Add refrence to users
			ArrayList<String> uids = fireBase.getUids(accessPolicy, customAccessPolicy, authResponse.getMessage(),
					user);

			for (String uid : uids) {
				try {
					fireBase.updateArray(uid, "files", fileRef);
				} catch (InterruptedException | ExecutionException ex) {
					logger.error("Adding file reference to user " + uid + " failed", ex);
				}

			}
			return new ResponseEntity<>("File Upload Successful", HttpStatus.OK);

		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("Patient")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Returns a users role
	 * 
	 * @param uidToken
	 * @return
	 */
	@RequestMapping(value = "/getUserRole", method = RequestMethod.GET)
	public ResponseEntity<?> getUserRole(@RequestHeader("Xx-Firebase-Id-Token") String uidToken) {
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		FunctionResponse getResponse = fireBase.getRole(authResponse.getMessage());
		if (getResponse.successful()) {
			return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.OK);
		} else {
			return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Creates a new Data Provider account, bucket, and private and master keys
	 * 
	 * @param uidToken
	 * @param user
	 * @return
	 */
	@RequestMapping(value = "/newDP", method = RequestMethod.POST)
	public ResponseEntity<?> newDP(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestBody DP user) {
		logger.info("Incoming request to add DP");
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		logger.info("Auth check successful");
		// Check user is admin
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("admin")) {
			UserRecord userRecord;
			try {
				userRecord = fireBase.registerUser(user.name, user.email, user.password);
			} catch (FirebaseAuthException ex) {
				logger.error("Add DP failed", ex);
				return new ResponseEntity<>("Creating user failed", HttpStatus.BAD_REQUEST);
			}

			logger.info("Perm check successful");
			// Create bucket for DP
			String bucketName = cloudStorage.createBucket(user.name.replace(" ", "-").toLowerCase());

			// Generate keys for hierarchy
			Object[] setup = ABE.setup();
			BswabePub pub = (BswabePub) setup[0];
			BswabeMsk msk = (BswabeMsk) setup[1];

			// Store Public keys
			try {
				GCPSecretManager.storeKey(bucketName + "-public", SerializeUtils.serializeBswabePub(pub));
			} catch (IOException ex) {
				return new ResponseEntity<>("Public key could not be stored", HttpStatus.BAD_REQUEST);
			}
			// Store Master key
			try {
				GCPSecretManager.storeKey(bucketName + "-master", SerializeUtils.serializeBswabeMsk(msk));
			} catch (IOException ex) {
				return new ResponseEntity<>("Master key could not be stored", HttpStatus.BAD_REQUEST);
			}

			// Create Attribute Array
			ArrayList<String> attributes = new ArrayList<>();
			attributes.add("uid_" + userRecord.getUid());
			attributes.add("DP");

			// Create DP profile on firebase
			FunctionResponse addResponse = fireBase.addDP(user, bucketName, attributes, userRecord);
			if (!addResponse.successful()) {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}

			FunctionResponse keyResponse = helper.genAndStorePrivKeys(bucketName, attributes.toArray(new String[0]),
					userRecord.getUid());
			if (keyResponse.successful()) {
				return new ResponseEntity<>("DP creation successful", HttpStatus.OK);
			}
			return new ResponseEntity<>("Failed to create DP", HttpStatus.BAD_REQUEST);

		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("admin")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value = "/newDR", method = RequestMethod.POST)
	public ResponseEntity<?> newDR(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestBody DR user) {
		logger.info("Incoming request to add DR");
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		logger.info("Auth check successful");
		// Check user is DP
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("DP")) {
			logger.info("Perm check successful");
			// Make user with firebase
			UserRecord userRecord;
			try {
				userRecord = fireBase.registerUser(user.name, user.email, user.password);
			} catch (FirebaseAuthException ex) {
				logger.error("Add DR failed", ex);
				return new ResponseEntity<>("Creating user failed", HttpStatus.BAD_REQUEST);
			}

			// Create Attribute Array
			ArrayList<String> attributes = new ArrayList<>();
			attributes.add("uid_" + userRecord.getUid());
			attributes.add("DR");

			FunctionResponse addResponse = fireBase.addDR(user, authResponse.getMessage(), attributes, userRecord);
			if (!addResponse.successful()) {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}

			FunctionResponse keyResponse = helper.genAndStorePrivKeys(addResponse.getMessage(),
					attributes.toArray(new String[0]), userRecord.getUid());
			if (keyResponse.successful()) {
				return new ResponseEntity<>("DR successfully created", HttpStatus.OK);
			}
			return new ResponseEntity<>("DR Creation failed", HttpStatus.BAD_REQUEST);

		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("DP")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value = "/getFiles", method = RequestMethod.GET)
	public ResponseEntity<?> getFiles(@RequestHeader("Xx-Firebase-Id-Token") String uidToken) {
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		FunctionResponse fileResponse = fireBase.getFiles(authResponse.getMessage());
		if (fileResponse.successful()) {
			return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.OK);
		} else {
			return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value = "/downloadFile", method = RequestMethod.GET)
	public ResponseEntity<?> downloadFile(@RequestHeader("Xx-Firebase-Id-Token") String uidToken,
			@RequestParam("fileRef") String fileRef) {
		// Check auth
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Get file path + type
		FunctionResponse fileResponse = fireBase.getFilePath(fileRef);
		if (!fileResponse.successful()) {
			return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		String filePath = fileResponse.getMessage().split(",")[0];
		String fileType = fileResponse.getMessage().split(",")[1];

		User user;
		try {
			user = fireBase.getUserObject(authResponse.getMessage());
		} catch (InterruptedException | ExecutionException ex) {
			return new ResponseEntity<>("Couldn't get user object", HttpStatus.BAD_REQUEST);
		}

		String bucketName = user.bucketName;

		ByteArrayResource file = cloudStorage.downloadObject(bucketName, filePath);

		byte[] pubByte;
		byte[] prvByte;
		try {
			pubByte = GCPSecretManager.getKeys(bucketName + "-public");
			prvByte = GCPSecretManager.getKeys(authResponse.getMessage());
		} catch (IOException ex) {
			return new ResponseEntity<>("Couldn't retrive keys", HttpStatus.BAD_REQUEST);
		}
		BswabePub pub = SerializeUtils.unserializeBswabePub(pubByte);
		BswabePrv prv = SerializeUtils.unserializeBswabePrv(pub, prvByte);
		byte[] decFile;
		try {
			decFile = ABE.decrypt(pub, prv, file.getInputStream());
		} catch (Exception e) {
			logger.error("File couldn't be decrytped", e);
			return new ResponseEntity<>("File could not be decrypted, May not have correct attributes",
					HttpStatus.BAD_REQUEST);
		}
		ByteArrayResource resFile = new ByteArrayResource(decFile);

		return ResponseEntity.ok().contentLength(resFile.contentLength())
				.contentType(MediaType.parseMediaType(fileType)).body(resFile);
	}

	@RequestMapping(value="/getAllDPs", method=RequestMethod.GET)
	public ResponseEntity<?> getAllDPs() {
		FunctionResponse dpResponse = fireBase.getAllDPs();
		if(dpResponse.successful()){
			return new ResponseEntity<>(dpResponse.getMessage(), HttpStatus.OK);
		}
		return new ResponseEntity<>(dpResponse.getMessage(), HttpStatus.BAD_REQUEST);
	}
	
	@RequestMapping(value="/getPatients", method=RequestMethod.GET)
	public ResponseEntity<?> getPatients(@RequestHeader("Xx-Firebase-Id-Token") String uidToken){
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		FunctionResponse pResponse = fireBase.getAllPatients();
		if(pResponse.successful()){
			return new ResponseEntity<>(pResponse.getMessage(), HttpStatus.OK);
		}
		return new ResponseEntity<>(pResponse.getMessage(), HttpStatus.BAD_REQUEST);
	}

	@RequestMapping(value="/getPatientFiles", method=RequestMethod.GET)
	public ResponseEntity<?> getPatientFiles(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestParam("nhsNum") String nhsNum){
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Check user is DR
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("DR")) {
			//Get patient files
			FunctionResponse fileResponse = fireBase.getPatientFiles(nhsNum, authResponse.getMessage());
			if(fileResponse.successful()){
				return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.OK);
			}
			else{
				return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("DP")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value="/getPatientAttributes", method=RequestMethod.GET)
	public ResponseEntity<?> getPatientAttributes(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestParam("nhsNum") String nhsNum) {
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Check user is DR
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("DR")) {
			FunctionResponse attrResponse = fireBase.getPatientAttributes(nhsNum);
			if(attrResponse.successful()){
				return new ResponseEntity<>(attrResponse.getMessage(), HttpStatus.OK);
			}else{
				return new ResponseEntity<>(attrResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("DP")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value="/addAttribute", method=RequestMethod.POST)
	public ResponseEntity<?> addAttribute(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestParam("nhsNum") String nhsNum, @RequestParam("attribute") String attribute){
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		if(attribute.equals("notObtainable")){
			return new ResponseEntity<>("Invalid attribute entered", HttpStatus.BAD_REQUEST);
		}
		// Check user is DR
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("DR")) {
			FunctionResponse addResponse = fireBase.updateAttributes(nhsNum, attribute);
			if(addResponse.successful()){
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.OK);
			}
			else{
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("DP")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value="/removeAttribute", method=RequestMethod.POST)
	public ResponseEntity<?> removeAttribute(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestParam("nhsNum") String nhsNum, @RequestParam("attribute") String attribute){
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Check user is DR
		FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
		if (roleCheck.successful() && roleCheck.getMessage().equals("DR")) {
			FunctionResponse removeResponse = fireBase.removeAttribute(nhsNum, attribute);
			if(removeResponse.successful()){
				return new ResponseEntity<>(removeResponse.getMessage(), HttpStatus.OK);
			}
			else{
				return new ResponseEntity<>(removeResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("DP")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
}