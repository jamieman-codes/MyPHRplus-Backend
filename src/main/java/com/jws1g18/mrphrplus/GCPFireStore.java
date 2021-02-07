package com.jws1g18.mrphrplus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
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
import com.google.firestore.v1.Document;

public class GCPFireStore {
    Firestore db;

    public GCPFireStore() {
        FileInputStream serviceAccount;
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource("myphrplus-backend-firebase-adminsdk-3funi-c8f3b2f5ed.json");
            File file = new File(resource.toURI());
            serviceAccount = new FileInputStream(file);
        } catch (FileNotFoundException ex) {
            System.out.println("Credentials not found");
            return;
        } catch (URISyntaxException e) {
            System.out.println("URISyntaxException");
            return;
        }

        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.fromStream(serviceAccount);
        } catch (IOException ex) {
            System.out.println("IOException");
            return;
        }
        FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(credentials).build();
        FirebaseApp.initializeApp(options);
        this.db = FirestoreClient.getFirestore();
    }

    public static void main(String[] args) {
        GCPFireStore fireStore = new GCPFireStore();
        fireStore.addUser("Hanna", "Proud", "proudie@email.com", "Patient", "1-1-1");
        //fireStore.deleteUser("wq4IqMmgTDnZCmgpZgRq");
    }

    public Firestore getDB() {
        return this.db;
    }

    /**
     * Adds a user to firestore
     * Returns the user ID
     */
    public String addUser(String firstName, String lastName, String email, String role, String dob){
        Map<String, Object> data = new HashMap<>();
        data.put("firstName", firstName);
        data.put("lastName", lastName);
        data.put("e-mail", email);
        data.put("DOB", dob);
        data.put("role", role);
        
        ApiFuture<DocumentReference> addedDocRef = this.db.collection("users").add(data);

        try{
            return addedDocRef.get().getId();
        } catch(InterruptedException ex){
            return null;
        } catch(ExecutionException ex){
            return null;
        }
    }

    /**
     * Deletes a user from the firestore given their user ID
     * @param userID
     * @return True if successful, False if failed 
     */
    public Boolean deleteUser(String userID){
        ApiFuture<WriteResult> writeResult = this.db.collection("users").document(userID).delete();
        try{
            //System.out.println("Update time : " + writeResult.get().getUpdateTime());
            writeResult.get();
            } catch(InterruptedException ex){
                return false;
            } catch(ExecutionException ex){
                return false;
            }
        return true;
    }
}