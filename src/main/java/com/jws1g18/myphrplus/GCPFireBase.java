package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.cloud.FirestoreClient;

import com.jws1g18.myphrplus.DTOS.DP;
import com.jws1g18.myphrplus.DTOS.DR;
import com.jws1g18.myphrplus.DTOS.Patient;
import com.jws1g18.myphrplus.DTOS.User;

import org.slf4j.Logger;

public class GCPFireBase {
    Firestore db;
    FirebaseAuth auth;
    Logger logger;
    Random rand;

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
        this.rand = new Random();
    }

    public Firestore getDB() {
        return this.db;
    }

    /***
     * Adds a user to firestore
     * 
     * @param data The user data to be stored
     * @param uid  Firebase Auth uid of the user
     * @return Write Result of the add
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private WriteResult addUser(Map<String, Object> data, String uid) throws InterruptedException, ExecutionException {
        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<WriteResult> result = docRef.set(data);
        return result.get();
    }

    /***
     * Performs a query on the users collection
     * 
     * @param field Field to search
     * @param value Value to find in field
     * @return Query Snapshot, can be used to obtain all documents
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private QuerySnapshot queryUsers(String field, String value) throws InterruptedException, ExecutionException {
        CollectionReference user = this.db.collection("users");
        Query query = user.whereEqualTo(field, value);
        return query.get().get();
    }

    /***
     * Adds a patient to the firestore, randomly assigns them a DP and a DR
     * 
     * @param uid  Firebase auth uid
     * @param user Patient object containing user info
     * @return Function Response containing a success value and a message
     */
    public FunctionResponse addPatient(String uid, Patient user, ArrayList<String> attributes) {
        // Randomly select hospital and doctor for patient.
        QuerySnapshot query;
        try {
            query = queryUsers("role", "DP");
        } catch (InterruptedException ex) {
            logger.error("Getting DP for " + user.name + " failed", ex);
            return new FunctionResponse(false, "Getting DP failed" + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Getting DP for " + user.name + " failed", ex);
            return new FunctionResponse(false, "Getting DP failed" + ex.getMessage());
        }
        List<QueryDocumentSnapshot> dPdocs = query.getDocuments();
        if (dPdocs.isEmpty()) {
            logger.error("No DPs found");
            return new FunctionResponse(false, "No DPs found in FireStore");
        }
        QueryDocumentSnapshot dPdoc = dPdocs.get(rand.nextInt(dPdocs.size())); // Get random DP
        DP dp = dPdoc.toObject(DP.class);
        ArrayList<String> drList = dp.dataRequesters;
        String dr = drList.get(rand.nextInt(drList.size())); // Get random DR

        // Create firestore data
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.name);
        data.put("email", user.email);
        data.put("role", user.role);
        data.put("nhsnum", user.nhsnum);
        data.put("bucketName", dp.bucketName);
        data.put("parent", dr);
        data.put("attributes", attributes);

        // Update parent with child info
        try {
            updateArray(dr, "patients", uid);
        } catch (InterruptedException ex) {
            logger.error("Updating parent information for  " + user.name + " failed", ex);
            return new FunctionResponse(false, "Updating parent information failed " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Updating parent information for  " + user.name + " failed", ex);
            return new FunctionResponse(false, "Updating parent information failed " + ex.getMessage());
        }

        // Add to firestore
        try {
            addUser(data, uid);
            return new FunctionResponse(true, dp.bucketName);
        } catch (InterruptedException ex) {
            logger.error("Adding user " + user.name + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error("Adding user " + user.name + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        }
    }

    /***
     * Gets a user from the firebase
     * 
     * @param uid Firebase auth id of the user
     * @return User object
     * @throws InterruptedException
     * @throws ExecutionException
     */
    User getUserObject(String uid) throws InterruptedException, ExecutionException {
        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            User user = document.toObject(User.class);
            return user;
        } else {
            return null;
        }
    }

    /***
     * Gets a patient from firestore, can only be used if you know user is patient
     * @param uid Firebase auth id of the user
     * @return Patient object
     * @throws InterruptedException
     * @throws ExecutionException
     */
    Patient getPatient(String uid) throws InterruptedException, ExecutionException {
        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Patient user = document.toObject(Patient.class);
            return user;
        } else {
            return null;
        }
    }

    /***
     * Gets the user IDs of everyone included in the Patients access policy
     * @param accessPolicy List defining the basic access policy elements
     * @param customAccessPolicy Custom access policy string
     * @param uid User ID of the patient
     * @param patient Patient object 
     * @return ArrayList containing the user IDs
     */
    ArrayList<String> getUids(List<String> accessPolicy, String customAccessPolicy, String uid, Patient patient){
        ArrayList<String> uids = new ArrayList<>();
        for(String access: accessPolicy){
            if(access.equals("patient")){
                uids.add(uid);
            } else if(access.equals("DR")){
                uids.add(patient.parent);
            } else if(access.equals("DP")){
                uids.add(getParent(patient.parent));
            } else if(access.equals("custom")){
                List<String> customPolicyList = Arrays.asList(customAccessPolicy.split(","));
                QuerySnapshot query;
                // Get every user within the same bucket as user
                try{
                    query = queryUsers("bucketName", patient.bucketName);
                } catch(InterruptedException ex){
                    break;
                } catch(ExecutionException ex){
                    break;
                }
                // Check if the returned documents contain every attribute specifed
                for(QueryDocumentSnapshot doc: query.getDocuments()){
                    List<String> attrs = (List<String>) doc.get("attributes");
                    if(attrs.containsAll(customPolicyList)){
                        uids.add(doc.getId());
                    }
                }
            }
        }
        return uids;
    }
    
    public String addFile(Map<String, Object> file) throws InterruptedException, ExecutionException{
        ApiFuture<DocumentReference> future = this.db.collection("files").add(file);
        return future.get().getId();
    }

    /***
     * Returns the parent of a user, if one exists
     * @param uid Firebase auth ID of the user
     * @return Null if no parent found, parent uid if found
     */
    String getParent(String uid){
        DocumentReference docRef = this.db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document;
        try{
            document = future.get();
        } catch (InterruptedException ex){
            return null;
        } catch (ExecutionException ex){
            return null;
        }
        if(document.exists()){
            return document.getString("parent");
        }
        return null;
    }

    /***
     * Gets user information from firebase
     * 
     * @param uid Firebase auth id of the user
     * @return Returns JSON containing the user info if successful
     */
    public FunctionResponse getUser(String uid) {
        try {
            User user = getUserObject(uid);
            if (user != null) {
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
     * Updates a user array
     * 
     * @param userID Users ID
     * @param array  Array to be updated
     * @param value  Value to be added
     * @return True if successful
     */
    public WriteResult updateArray(String userID, String array, String value)
            throws InterruptedException, ExecutionException {
        DocumentReference docRef = this.db.collection("users").document(userID);
        ApiFuture<WriteResult> future = docRef.update(array, FieldValue.arrayUnion(value));
        return future.get();
    }

    public WriteResult updateField(String collection, String document, String field, String value) throws InterruptedException, ExecutionException{
        DocumentReference docRef = this.db.collection(collection).document(document);
        ApiFuture<WriteResult> future = docRef.update(field, value);
        return future.get();
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

    /***
     * Creates a user with firebase authentication
     * 
     * @param username
     * @param email
     * @param password
     * @return A user record object that can be used to obtain the uid
     * @throws FirebaseAuthException
     */
    UserRecord registerUser(String username, String email, String password) throws FirebaseAuthException {
        // Create User with firebase auth
        CreateRequest request = new CreateRequest().setDisplayName(username).setEmail(email).setPassword(password);
        UserRecord userRecord = auth.createUser(request);
        logger.info("User created with Firebase");
        return userRecord;
    }

    /***
     * Adds a data provider to the system
     * 
     * @param user       DP object containing user info
     * @param bucketName Bucketname of created bucket
     * @return
     */
    public FunctionResponse addDP(DP user, String bucketName, ArrayList<String> attributes, UserRecord userRecord) {
        // Create firestore data
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.name);
        data.put("email", user.email);
        data.put("role", user.role);
        data.put("bucketName", bucketName);
        data.put("attributes", attributes);

        ArrayList<String> dataRequesters = new ArrayList<>();
        data.put("dataRequesters", dataRequesters);

        // Add user info to firestore
        try {
            String res = addUser(data, userRecord.getUid()).getUpdateTime().toString();
            return new FunctionResponse(true, "Add successful at " + res);
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Adding user " + user.name + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        } 
    }

    /***
     * Adds a data requester to the system
     * 
     * @param user      DR object containing user info
     * @param parentUid Firebase auth id of the parent
     * @return
     */
    public FunctionResponse addDR(DR user, String parentUid, ArrayList<String> attributes, UserRecord userRecord) {
        // Get parent info
        User parent;
        try {
            parent = getUserObject(parentUid);
            if (parent == null) {
                return new FunctionResponse(false, "Parent not found");
            }
        } catch (InterruptedException |ExecutionException ex) {
            logger.error("Get parent failed", ex);
            return new FunctionResponse(false, "Get parent failed with " + ex.getMessage());
        }

        // Create firestore data
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.name);
        data.put("email", user.email);
        data.put("role", user.role);
        data.put("bucketName", parent.bucketName);
        data.put("parent", parentUid);
        data.put("attributes", attributes);

        ArrayList<String> patients = new ArrayList<>();
        data.put("patients", patients);

        // Update parent with child info
        try {
            updateArray(parentUid, "dataRequesters", userRecord.getUid());
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Updating parent information for  " + user.name + " failed", ex);
            return new FunctionResponse(false, "Updating parent information failed " + ex.getMessage());
        }

        // Add user info to firestore
        try {
            addUser(data, userRecord.getUid());
            return new FunctionResponse(true, parent.bucketName);
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Adding user " + user.name + " failed", ex);
            return new FunctionResponse(false, "Add failed " + ex.getMessage());
        }
    }

    public FunctionResponse getFiles(String uid){
        // Get user object
        User user;
        try{
            user = getUserObject(uid);
        } catch (InterruptedException | ExecutionException ex){
            return new FunctionResponse(false, "Getting user object failed");
        }
        // Get file references
        ArrayList<String> files = user.files;
        
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        // Get file data
        for(String fileRef :files){
            DocumentReference docRef = this.db.collection("files").document(fileRef);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document;
            try{
                document = future.get();
                if (document.exists()) {
                    ObjectNode fileNode = mapper.createObjectNode();
                    fileNode.put("fileName", document.getString("fileName"));
                    String type = document.getString("type").split("/")[0];
                    fileNode.put("fileType", type);
                    fileNode.put("opened", document.getBoolean("opened"));
                    fileNode.put("ref", fileRef);
                    arrayNode.addAll(Arrays.asList(fileNode));
                } 
            } catch (InterruptedException | ExecutionException ex){
                logger.error("Could get file "  + fileRef, ex);   
            }
        }

        try{
            return new FunctionResponse(true, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode));
        } catch (JsonProcessingException ex){
            return new FunctionResponse(false, "Couldn't process JSON");
        }
    }

    public FunctionResponse getFilePath(String fileRef){
        DocumentReference docRef = this.db.collection("files").document(fileRef);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document;
        try{
            document = future.get();
            if (document.exists()) {
                String res = document.getString("filepath") + "," + document.getString("type");
                return new FunctionResponse(true, res);
            }
            return new FunctionResponse(false, "Couldn't find file");
        } catch (InterruptedException | ExecutionException ex){
            logger.error("Could get file "  + fileRef, ex);
            return new FunctionResponse(false, "Couldn't find file");
        }
    }
}