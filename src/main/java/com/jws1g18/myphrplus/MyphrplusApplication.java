package com.jws1g18.myphrplus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
import co.junwei.bswabe.BswabePub;

@SpringBootApplication
@RestController
public class MyphrplusApplication {
	Logger logger = LoggerFactory.getLogger(MyphrplusApplication.class);

	GCPFireBase fireBase = new GCPFireBase(logger);
	GCPCloudStorage cloudStorage = new GCPCloudStorage(logger);
	Helpers helper = new Helpers();

	public static void main(String[] args) {
		//Generate Public and Master keys
		SpringApplication.run(MyphrplusApplication.class, args);
	}

	/**
	 * Adds a new patient to the Firestore
	 * 
	 * @param patient JSON containing patient information
	 * @return Returns HTTP response code and message
	 */
	@RequestMapping(method = RequestMethod.POST, path = "/registerPatient")
	public ResponseEntity<?> registerPatient(@RequestBody Patient patient) {
		//Check authentication
		FunctionResponse authResponse = fireBase.verifyUidToken(patient.uid);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		//Add patient to firestore
		FunctionResponse addResponse = fireBase.addPatient(authResponse.getMessage(), patient);
		if (addResponse.successful()) {
			return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		
	}

	/**
	 * Gets user information
	 * 
	 * @param uid Firebase auth user id token
	 * @return Returns HTTP response code and user information
	 */
	@RequestMapping(method = RequestMethod.GET, path = "/getUser")
	public ResponseEntity<?> getUser(@RequestHeader("Xx-Firebase-Id-Token") String uidToken) {
		//Check authentication
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		//Get user information
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
		//Check authentication
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		//Delete user
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
				return new ResponseEntity<>("Get patient object failed with " + ex.getMessage(),
						HttpStatus.BAD_REQUEST);
			}
			// Check file type + extension
			FunctionResponse typeCheck = helper.detectFileType(file);
			if (!typeCheck.successful()) {
				return new ResponseEntity<>(typeCheck.getMessage(), HttpStatus.BAD_REQUEST);
			}
			String extension = typeCheck.getMessage().split(" ")[0];
			String type = typeCheck.getMessage().split(" ")[1];

			// Upload object
			String filepath = user.parent + "/" + authResponse.getMessage() + "/" + name + "." + extension;
			FunctionResponse uploadResponse = cloudStorage.uploadFile(user.bucketName, filepath, file, type);
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
			try{
				fileRef = fireBase.addFile(fileInfo);
			} catch (InterruptedException | ExecutionException ex){
				logger.error("Adding file reference to firestore failed", ex);
				return new ResponseEntity<>("Adding file reference to firestore failed", HttpStatus.BAD_REQUEST);
			}

			// Add refrence to users
			ArrayList<String> uids = fireBase.getUids(accessPolicy, customAccessPolicy, authResponse.getMessage(),
					user);

			for(String uid: uids){
				try{
					fireBase.updateArray(uid, "files", fileRef);
				} catch (InterruptedException | ExecutionException ex){
					logger.error("Adding file reference to user " + uid + " failed", ex);
				}
					
			}
			return new ResponseEntity<>("File Upload Successful", HttpStatus.OK);
			
		} else if(roleCheck.successful() && !roleCheck.getMessage().equals("Patient")){
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
			logger.info("Perm check successful");
			// Create bucket for DP
			String bucketName = cloudStorage.createBucket(user.name.replace(" ", "-").toLowerCase());
			
			//Generate keys for hierarchy 
			Object[] setup = ABE.setup();
			BswabePub pub = (BswabePub) setup[0];
			BswabeMsk msk = (BswabeMsk) setup[1];
			
			// Create DP profile on firebase
			FunctionResponse addResponse = fireBase.addDP(user, bucketName);
			if (addResponse.successful()) {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
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
			FunctionResponse addResponse = fireBase.addDR(user, authResponse.getMessage());
			if (addResponse.successful()) {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else if (roleCheck.successful() && !roleCheck.getMessage().equals("DP")) {
			return new ResponseEntity<>("You do not have the correct permissions", HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>(roleCheck.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value = "/getFiles", method = RequestMethod.GET)
	public ResponseEntity<?> getFiles(@RequestHeader("Xx-Firebase-Id-Token") String uidToken){
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		} 
		FunctionResponse fileResponse = fireBase.getFiles(authResponse.getMessage());
		if(fileResponse.successful()){
			return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.OK);
		} else{
			return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value = "/downloadFile", method= RequestMethod.GET)
	public ResponseEntity<?> downloadFile(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestParam("fileRef") String fileRef){
		//Check auth
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (!authResponse.successful()) {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		// Get file path + type
		FunctionResponse fileResponse = fireBase.getFilePath(fileRef);
		if(!fileResponse.successful()){
			return new ResponseEntity<>(fileResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
		String filePath = fileResponse.getMessage().split(",")[0];
		String fileType = fileResponse.getMessage().split(",")[1];

		User user;
		try{
			user = fireBase.getUserObject(authResponse.getMessage());
		} catch (InterruptedException | ExecutionException ex){
			return new ResponseEntity<>("Couldn't get user object", HttpStatus.BAD_REQUEST);
		}

		String bucketName = user.bucketName;

		ByteArrayResource file = cloudStorage.downloadObject(bucketName, filePath);
		
		return ResponseEntity.ok().contentLength(file.contentLength()).contentType(MediaType.parseMediaType(fileType)).body(file);
	}
}