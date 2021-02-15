package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

public class GCPFireStore {
    Firestore db;

    public GCPFireStore() {
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
    }

    public static void main(String[] args) {
        GCPFireStore fireStore = new GCPFireStore();
        // fireStore.addUser("Hanna", "Proud", "proudie@email.com", "Patient", "1-1-1");
        // fireStore.deleteUser("wq4IqMmgTDnZCmgpZgRq");
        ArrayList<String> attr = new ArrayList<>();
        attr.add("Pog");
        fireStore.updateAttributes("TbK8rYYS036rPTetEa7O", attr);
    }

    public Firestore getDB() {
        return this.db;
    }

    /**
     * Adds a user to firestore Returns the user ID
     */
    public String addUser(String uid, String name, String email, String role) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("e-mail", email);
        data.put("role", role);

        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<WriteResult> result = docRef.set(data);

        try {
            String res = result.get().getUpdateTime().toString();
            return "Add successful at " + res;
        } catch (InterruptedException ex) {
            return "Add failed " + ex.getMessage();
        } catch (ExecutionException ex) {
            return "Add failed " + ex.getMessage();
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
}