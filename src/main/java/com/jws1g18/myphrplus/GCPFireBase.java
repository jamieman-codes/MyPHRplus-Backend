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
                logger.info(user.name + " " + user.email + " " + user.role);
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
    public Boolean deleteUser(String userID) {
        ApiFuture<WriteResult> writeResult = this.db.collection("users").document(userID).delete();
        try {
            // System.out.println("Update time : " + writeResult.get().getUpdateTime());
            writeResult.get();
        } catch (InterruptedException ex) {
            return false;
        } catch (ExecutionException ex) {
            return false;
        }
        return true;
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

    public FunctionResponse verifyUidToken(String uidToken) {
        try {
            FirebaseToken decodedToken = this.auth.verifyIdToken(uidToken);
            return new FunctionResponse(true, decodedToken.getUid());
        } catch (FirebaseAuthException e) {
            logger.error("Authentication failed with error code: " + e.getErrorCode(), e);
            return new FunctionResponse(false, "Failed with error code: " + e.getErrorCode());
        }
    }
}