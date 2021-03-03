package com.jws1g18.myphrplus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@SpringBootApplication
@RestController
public class MyphrplusApplication {
	Logger logger = LoggerFactory.getLogger(MyphrplusApplication.class);

	GCPFireBase fireBase = new GCPFireBase(logger);
	GCPCloudStorage cloudStorage = new GCPCloudStorage(logger);

	public static void main(String[] args) {
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
		FunctionResponse authResponse = fireBase.verifyUidToken(patient.uid);
		if (authResponse.successful()) {
			FunctionResponse addResponse = fireBase.addUser(authResponse.getMessage(), patient.name, patient.email,
					patient.role);
			if (addResponse.successful()) {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.CREATED);
			} else {
				return new ResponseEntity<>(addResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
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
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (authResponse.successful()) {
			FunctionResponse getResponse = fireBase.getUser(authResponse.getMessage());
			if (getResponse.successful()) {
				return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
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
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (authResponse.successful()) {
			FunctionResponse deleteResponse = fireBase.deleteUser(authResponse.getMessage());
			if (deleteResponse.successful()) {
				return new ResponseEntity<>(deleteResponse.getMessage(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(deleteResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	/***
	 * Uploads a file to the cloud storage
	 * 
	 * @param uidToken
	 * @param file
	 * @return
	 */
	@RequestMapping(value = "/uploadFile", method = RequestMethod.POST, consumes = { "multipart/form-data" })
	public ResponseEntity<?> uploadFile(@RequestHeader("Xx-Firebase-Id-Token") String uidToken,
			@RequestParam(name = "file") MultipartFile file) {
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (authResponse.successful()) {
			FunctionResponse uploadResponse = cloudStorage
					.uploadObject("southampton-general-hospital-myphrplus-backend", "testFile", file);
			if (uploadResponse.successful()) {
				return new ResponseEntity<>(uploadResponse.getMessage(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(uploadResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
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
		if (authResponse.successful()) {
			FunctionResponse getResponse = fireBase.getRole(authResponse.getMessage());
			if (getResponse.successful()) {
				return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(getResponse.getMessage(), HttpStatus.BAD_REQUEST);
			}
		} else {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Creates a new Data Provider account and bucket
	 * 
	 * @param uidToken
	 * @param user
	 * @return
	 */
	@RequestMapping(value = "/newDP", method = RequestMethod.POST)
	public ResponseEntity<?> newDP(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestBody User user) {
		logger.info("Incoming request to add DP");
		FunctionResponse authResponse = fireBase.verifyUidToken(uidToken);
		if (authResponse.successful()) {
			logger.info("Auth check successful");
			// Check user is admin
			FunctionResponse roleCheck = fireBase.getRole(authResponse.getMessage());
			if (roleCheck.successful() && roleCheck.getMessage().equals("admin")) {
				logger.info("Perm check successful");
				// Create bucket for DP
				String bucketName = cloudStorage.createBucket(user.name.replace(" ", "-").toLowerCase());
				// Create DP profile on firebase
				FunctionResponse addResponse = fireBase.addDP(user.name, user.email, user.password, bucketName);
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
		} else {
			return new ResponseEntity<>(authResponse.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(value = "/newDR",  method = RequestMethod.POST)
	public ResponseEntity<> newDR(@RequestHeader("Xx-Firebase-Id-Token") String uidToken, @RequestBody User user)
}