package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.cloud.FirestoreClient;

import org.slf4j.Logger;

public class GCPFireBase {
    Firestore db;
    FirebaseAuth auth;
    Logger logger;

    public GCPFireBase(Logger logger) {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException ex) {
            System.out.println("IOException");
            return;
        }
        FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(credentials)
                .setProjectId("myphrplus-backend").build();
        FirebaseApp.initializeApp(options);

        this.db = FirestoreClient.getFirestore();
        this.auth = FirebaseAuth.getInstance();

        this.logger = logger;
    }

    public Firestore getDB() {
        return this.db;
    }

    /**
     * Adds a user to firestore Returns the user ID
     */
    public FunctionResponse addUser(String uid, String name, String email, String role) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("email", email);
        data.put("role", role);

        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<WriteResult> result = docRef.set(data);

        try {
            String res = result.get().getUpdateTime().toString();
            return new FunctionResponse(true, "Add successful at " + res);
        } catch (InterruptedException ex) {
            logger.error("Adding user " + name + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Adding user " + name + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        }
    }

    /**
     * Gets user information from firestore
     */
    public FunctionResponse getUser(String uid) {
        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        try {
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                User user = document.toObject(User.class);
                return new FunctionResponse(true, user.convertToJson());
            } else {
                return new FunctionResponse(false, "User not found in database");
            }
        } catch (InterruptedException ex) {
            logger.error("Get failed", ex);
            return new FunctionResponse(false, "Get failed with " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Get failed", ex);
            return new FunctionResponse(false, "Get failed with" + ex.getMessage());
        }
    }

    /**
     * Deletes a user from the firestore given their user ID
     * 
     * @param userID
     * @return True if successful, False if failed
     */
    public FunctionResponse deleteUser(String userID) {
        ApiFuture<WriteResult> writeResult = this.db.collection("users").document(userID).delete();
        try {
            writeResult.get();
            return new FunctionResponse(true, "User Deleted");
        } catch (InterruptedException ex) {
            logger.error("Deletion failed", ex);
            return new FunctionResponse(false, "Deletion failed with " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Deletion failed", ex);
            return new FunctionResponse(false, "Deletion failed with " + ex.getMessage());
        }
    }

    /**
     * Updates a users set of attributes
     * 
     * @param userID     Users ID
     * @param attributes Array of attributes
     * @return True if successful
     */
    public Boolean updateAttributes(String userID, ArrayList<String> attributes) {
        DocumentReference docRef = this.db.collection("users").document(userID);
        ApiFuture<WriteResult> future = docRef.update("attributes", attributes);
        try {
            future.get();
        } catch (InterruptedException ex) {
            return false;
        } catch (ExecutionException ex) {
            return false;
        }
        return true;
    }

    /***
     * Verifys if the user token sent with a request is a valid firebase token.
     * 
     * @param uidToken Firebase id token
     * @return Boolean and message detailed if the action was successful
     */
    public FunctionResponse verifyUidToken(String uidToken) {
        try {
            FirebaseToken decodedToken = this.auth.verifyIdToken(uidToken);
            return new FunctionResponse(true, decodedToken.getUid());
        } catch (FirebaseAuthException e) {
            logger.error("Authentication failed with error code: " + e.getErrorCode(), e);
            return new FunctionResponse(false, "Failed with error code: " + e.getErrorCode());
        }
    }

    /***
     * Gets the role of a user given their user ID
     * 
     * @param uid User ID
     * @return Users role, or error message
     */
    public FunctionResponse getRole(String uid) {
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        try {
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                User user = document.toObject(User.class);
                return new FunctionResponse(true, user.role);
            }
            return new FunctionResponse(false, "User not found");
        } catch (InterruptedException ex) {
            logger.error("Get failed", ex);
            return new FunctionResponse(false, "Get failed with " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Get failed", ex);
            return new FunctionResponse(false, "Get failed with " + ex.getMessage());
        }
    }

    public FunctionResponse addDP(String dpName, String email, String password, String bucketName) {
        // Create User with firebase auth
        CreateRequest request = new CreateRequest().setDisplayName(dpName).setEmail(email).setPassword(password);
        UserRecord userRecord;
        try {
            userRecord = auth.createUser(request);
            logger.info("User created with Firebase");
        } catch (FirebaseAuthException ex) {
            logger.error("Add failed", ex);
            return new FunctionResponse(false, "Get failed with " + ex.getMessage());
        }
        // Add user info to firestore
        Map<String, Object> data = new HashMap<>();
        data.put("name", dpName);
        data.put("email", email);
        data.put("role", "DP");
        data.put("bucketName", bucketName);

        // Create attributes array
        ArrayList<String> attributes = new ArrayList<>();
        attributes.add("DP");
        attributes.add(userRecord.getUid());
        data.put("attributes", attributes);

        DocumentReference docRef = this.db.collection("users").document(userRecord.getUid());
        ApiFuture<WriteResult> result = docRef.set(data);
        try {
            String res = result.get().getUpdateTime().toString();
            return new FunctionResponse(true, "Add successful at " + res);
        } catch (InterruptedException ex) {
            logger.error("Adding user " + dpName + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Adding user " + dpName + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        }
    }
}