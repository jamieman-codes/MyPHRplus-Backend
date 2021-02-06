package com.jws1g18.mrphrplus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

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
        System.out.println("Hey!");
    }

    public Firestore getDB() {
        return this.db;
    }
}