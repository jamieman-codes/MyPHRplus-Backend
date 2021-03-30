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
import com.google.firestore.v1.Document;
import com.jws1g18.myphrplus.DTOS.User;

import org.slf4j.Logger;

public class GCPFireBase {
    Firestore db;
    FirebaseAuth auth;
    Logger logger;
    Random rand;
    Helpers helper;

    public GCPFireBase(Logger logger, Helpers helper) {
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
        this.helper = helper;
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
    QuerySnapshot queryUsers(String field, String value) throws InterruptedException, ExecutionException {
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
    public FunctionResponse addPatient(String uid, User user, ArrayList<String> attributes) {
        // Randomly assignmdoctor for patient.
        DocumentReference docRef = this.db.collection("users").document(user.parent);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document;
        try {
            document = future.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Couldn't get DP when adding Patient", e);
            return new FunctionResponse(false, "Couldn't get DP");
        }
        User dp = document.toObject(User.class);
        ArrayList<String> drList = dp.dataRequesters;
        String dr = drList.get(rand.nextInt(drList.size())); // Get random DR

        // Create firestore data
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.name);
        data.put("email", user.email);
        data.put("role", "Patient");
        data.put("nhsnum", user.nhsnum);
        data.put("bucketName", dp.bucketName);
        data.put("parent", dr);
        data.put("attributes", attributes);
        data.put("files", new ArrayList<String>());

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
            logger.error("Could not find user " + uid);
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
    ArrayList<String> getUids(List<String> accessPolicy, String customAccessPolicy, String uid, User patient){
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
                } catch(InterruptedException | ExecutionException ex){
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
        } catch (InterruptedException | ExecutionException ex){
            logger.error("Could not get parent", ex);
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
                logger.error("User " + uid + " could not be found");
                return new FunctionResponse(false, "User not found in database");
            }
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Get failed user failed", ex);
            return new FunctionResponse(false, "Could not get user");
        }
    }

    /**
     * Deletes a user from the firestore given their user ID
     * @param userID
     * @return True if successful, False if failed
     */
    public FunctionResponse deleteUser(String userID) {
        DocumentReference userDocRef = this.db.collection("users").document(userID);
        DocumentSnapshot userDoc;
        try {
            userDoc = userDocRef.get().get();
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Getting user "+ userID +" failed", ex);
            return new FunctionResponse(false, "Failed to find user");
        }
        String role = userDoc.getString("role");
        if(role.equals("DP") || role.equals("admin")){
            return new FunctionResponse(false, "Cannot delete account due to role. Contact System Admin");
        }
        else if(role.equals("Patient")){
            String parent = userDoc.getString("parent");
            try{
                this.db.collection("users").document(parent).update("patients", FieldValue.arrayRemove(userID)).get();
                userDocRef.delete().get();
                GCPSecretManager.destroySecretVersion(userID);
                this.auth.deleteUser(userID);
                return new FunctionResponse(true, "Account Deleted");
            } catch (InterruptedException | ExecutionException ex){
                logger.error("Error whilst deleting user", ex);
                return new FunctionResponse(false, "Error whilst deleting user");
            } catch (FirebaseAuthException e) {
                logger.error("Error whilst removing user from firebase", e);
                return new FunctionResponse(false, "Error whilst deleting user");
            } catch (IOException ex){
                logger.error("Error whilst removing private key", ex);
                return new FunctionResponse(false, "Error whilst deleting user");
            }
        } else if(role.equals("DR")){
            String parent = userDoc.getString("parent");
            //Find all DRs in same bucket (need to reassign patients)
            QuerySnapshot qs;
            try {
                qs = this.db.collection("users").whereEqualTo("bucketName", userDoc.get("bucketName")).whereEqualTo("role", "DR").get().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Query to find DRs in same bucket failed", e);
                return new FunctionResponse(false, "Query to reasign patients failed");
            }
            if(qs.size() <= 1){
                return new FunctionResponse(false, "Last DR registered to DP, cannot delete account. Please contact System Admin");
            }
            //Get new DR
            List<QueryDocumentSnapshot> docs = qs.getDocuments();
            String newDR = docs.get(rand.nextInt(docs.size())).getId();
            //Assign patient to new DR and new DR to patients
            for(String patient: (ArrayList<String>) userDoc.get("patients")){
                try {
                    updateField("users", patient, "parent", newDR);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Couldnt update user:" + patient + " with new parent", e);
                }
                try {
                    updateArray(newDR, "patients", patient);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Couldnt update parent:" + newDR + " with new patient", e);
                }
            }
            try {
                this.db.collection("users").document(parent).update("dataRequesters", FieldValue.arrayRemove(userID)).get();
                userDocRef.delete().get();
                GCPSecretManager.destroySecretVersion(userID);
                this.auth.deleteUser(userID);
                return new FunctionResponse(true, "Delete successful");
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Couldn't delete user: " +userID, e);
                return new FunctionResponse(false, "Delete Failed");
            } catch (FirebaseAuthException e) {
                logger.error("Error whilst removing user from firebase", e);
                return new FunctionResponse(false, "Error whilst deleting user");
            } catch (IOException e) {
                logger.error("Error whilst removing private key", e);
                return new FunctionResponse(false, "Error whilst deleting user");
            }
        } else{
            logger.error("Request to delete with invalid role");
            return new FunctionResponse(false, "invalid role");
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

    /**
     * Updates a document field 
     * @param collection 
     * @param document
     * @param field
     * @param value
     * @return Result of the write 
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public WriteResult updateField(String collection, String document, String field, String value) throws InterruptedException, ExecutionException{
        DocumentReference docRef = this.db.collection(collection).document(document);
        ApiFuture<WriteResult> future = docRef.update(field, value);
        return future.get();
    }

    /***
     * Verifys if the user token sent with a request is a valid firebase token.
     * @param uidToken Firebase id token
     * @return Boolean and message detailed if the action was successful
     */
    public FunctionResponse verifyUidToken(String uidToken) {
        try {
            FirebaseToken decodedToken = this.auth.verifyIdToken(uidToken);
            return new FunctionResponse(true, decodedToken.getUid());
        } catch (FirebaseAuthException e) {
            logger.error("Authentication failed with error code: " + e.getErrorCode(), e);
            return new FunctionResponse(false, "Could not authenticate user");
        }
    }

    /***
     * Gets the role of a user given their user ID
     * 
     * @param uid User ID
     * @return Users role, or error message
     */
    public FunctionResponse getRole(String uid) {
        try {
            DocumentSnapshot docRef = db.collection("users").document(uid).get().get();
            return new FunctionResponse(true, docRef.getString("role")); 
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Could not get user:" + uid + " role", e);
            return new FunctionResponse(false, "Could not get user role");
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
     * @param user       DP object containing user info
     * @param bucketName Bucketname of created bucket
     * @return
     */
    public FunctionResponse addDP(User user, String bucketName, ArrayList<String> attributes, UserRecord userRecord) {
        // Create firestore data
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.name);
        data.put("email", user.email);
        data.put("role", "DP");
        data.put("bucketName", bucketName);
        data.put("attributes", attributes);
        data.put("files", new ArrayList<String>());

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
    public FunctionResponse addDR(User user, String parentUid, ArrayList<String> attributes, UserRecord userRecord) {
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
        data.put("role", "DR");
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

    /**
     * Gets a list of a users files and returns them as a JSON
     * @param uid 
     * @return JSON array of the files
     */
    public FunctionResponse getFiles(String uid){
        // Get user object
        User user;
        try{
            user = getUserObject(uid);
        } catch (InterruptedException | ExecutionException ex){
            logger.error("Could not get user :" + uid, ex);
            return new FunctionResponse(false, "Getting user object failed");
        }
        // Get file references
        ArrayList<String> files = user.files;
        
        return filesToJSON(files);
    }

    /**
     * Gets a file path from a file reference
     * @param fileRef
     * @return
     */
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
            logger.error("Couldn't find file: " + fileRef);
            return new FunctionResponse(false, "Couldn't find file");
        } catch (InterruptedException | ExecutionException ex){
            logger.error("Could get file "  + fileRef, ex);
            return new FunctionResponse(false, "Couldn't find file");
        }
    }

    /**
     * Converts an array list of file refs to a JSON of their file info
     * @param files ArrayList of file references
     * @return
     */
    private FunctionResponse filesToJSON(ArrayList<String> files){
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
                    ObjectNode fileNode = arrayNode.addObject();
                    fileNode.put("fileName", document.getString("fileName"));
                    String type = document.getString("type").split("/")[0];
                    fileNode.put("fileType", type);
                    fileNode.put("ref", fileRef);
                } 
            } catch (InterruptedException | ExecutionException ex){
                logger.error("Couldn't get file "  + fileRef, ex);   
                return new FunctionResponse(false, "Couldn't get file");
            }
        }
        try{
            return new FunctionResponse(true, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode));
        } catch (JsonProcessingException ex){
            logger.error("JSON could not be proccessed", ex);
            return new FunctionResponse(false, "Couldn't process JSON");
        }
    }

    /***
     * Returns a JSON array of all data providers, for use on registration 
     * @return
     */
    public FunctionResponse getAllDPs(){
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        // Get all DPs
        QuerySnapshot snapshot;
        try {
            snapshot = queryUsers("role", "DP");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error whilst querying database for DPs", e);
            return new FunctionResponse(false, "Could not get from FireStore");
        }
        for(QueryDocumentSnapshot doc: snapshot.getDocuments()){
            ObjectNode dpNode = arrayNode.addObject();
            dpNode.put("text", doc.getString("name"));
            dpNode.put("value", doc.getId());
        }
        try {
            return new FunctionResponse(true, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode));
        } catch (JsonProcessingException e) {
            logger.error("Could not proccess JSON", e);
            return new FunctionResponse(false, "Couldn't process JSON");
        }
    }

    /**
     *  Gets a list of all patients for a Data Requester 
     * @param uid ID of DR
     * @return
     */
    public FunctionResponse getAllPatients(String uid){
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        // Get all DPs
        ArrayList<String> patients;
        try {
            DocumentSnapshot ref = this.db.collection("users").document(uid).get().get();
            patients = (ArrayList<String>) ref.get("patients");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Could not get patients from FireStore", e);
            return new FunctionResponse(false, "Could not get patients from FireStore");
        }
        for(String patient: patients){
            try{
                DocumentSnapshot doc = this.db.collection("users").document(patient).get().get();
                ObjectNode dpNode = arrayNode.addObject();
                dpNode.put("name", doc.getString("name"));
                dpNode.put("nhsNum", doc.getString("nhsnum"));
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Could not get patient info", e);
                return new FunctionResponse(false, "Could not get patient from FireStore");
            }
        }
        try {
            return new FunctionResponse(true, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode));
        } catch (JsonProcessingException e) {
            logger.error("Could not process JSON", e);
            return new FunctionResponse(false, "Couldn't process JSON");
        }
    }

    /**
     * Gets a list of a patient files from an NHS num
     * @param nhsNum 
     * @param uid
     * @return
     */
    public FunctionResponse getPatientFiles(String nhsNum, String uid){
        // Lookup patient from NHS num
        QuerySnapshot snapshot;
        try {
            snapshot = queryUsers("nhsnum", nhsNum);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Could not find user with NHS num " + nhsNum, e);
            return new FunctionResponse(false, "Could not get from FireStore");
        }
        //Get patient files
        ArrayList<String> files = null;
        int x = 0;
        for(QueryDocumentSnapshot doc: snapshot.getDocuments()){
            files = (ArrayList<String>) doc.get("files");
            x++;
        }
        if(x>1 || files == null){
            return new FunctionResponse(false, "Failed to get patient files");
        }
        // Get requesters files
        User drObject;
        try{
            drObject = getUserObject(uid);
        }
        catch(InterruptedException | ExecutionException ex){
            logger.error("Getting DR object failed", ex);
            return new FunctionResponse(false, "Couldn't get DR object");
        }
        ArrayList<String> drFiles = drObject.files;
        //files now contains only the elements which are also contained in drFiles.
        files.retainAll(drFiles);

        return filesToJSON(files);
    }

    /**
     * Checks if an NHS number has already been registered 
     * @param nhsNum
     * @return False if no other NHS nums are found
     */
    public boolean checkNHSnum(String nhsNum){
        QuerySnapshot snapshot;
        try {
            snapshot = queryUsers("nhsnum", nhsNum);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("User query failed ", e);
            return true;
        }
        return snapshot.isEmpty();
    }

    /**
     * Gets a list of a patients attributes in JSON form from an NHS num
     * @param nhsNum
     * @return
     */
    public FunctionResponse getPatientAttributes(String nhsNum){
        // Lookup patient from NHS num
        QuerySnapshot snapshot;
        try {
            snapshot = queryUsers("nhsnum", nhsNum);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Could not find user with NHS num " + nhsNum, e);
            return new FunctionResponse(false, "Could not get from FireStore");
        }
        //Get patient attributes
        ArrayList<String> attributes = null;
        int x = 0;
        for(QueryDocumentSnapshot doc: snapshot.getDocuments()){
            attributes = (ArrayList<String>) doc.get("attributes");
            x++;
        }
        if(x>1 || attributes == null){
            return new FunctionResponse(false, "Failed to get patient attributes");
        }
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        x = 0;
        //Convert to JSON, ignore first attributes as this is the uid, make first 2 shown non removeable
        for(String attr: attributes){
            if(x>0){
                ObjectNode attrNode = arrayNode.addObject();
                attrNode.put("attribute", attr);
                if(x>2){
                    attrNode.put("remove", false);
                } else{
                    attrNode.put("remove", true);
                }
            }
            x++;
        }
        try {
            return new FunctionResponse(true, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arrayNode));
        } catch (JsonProcessingException e) {
            logger.error("Couldn't proccess JSON", e);
            return new FunctionResponse(false, "Couldn't process JSON");
        }
    }

    /**
     * Adds a new attribute to a user and updates their private key 
     * @param nhsNum
     * @param attr
     * @return
     */
    public FunctionResponse updateAttributes(String nhsNum, String attr){
        // Lookup patient from NHS num
        QuerySnapshot snapshot;
        try {
            snapshot = queryUsers("nhsnum", nhsNum);
        } catch (InterruptedException | ExecutionException e) {
            return new FunctionResponse(false, "Could not get from FireStore");
        }
        String uid = null;
        String bucketName = null;
        ArrayList<String> attributes = null;
        int x = 0;
        for(QueryDocumentSnapshot doc: snapshot.getDocuments()){
            uid = doc.getId();
            bucketName = doc.getString("bucketName");
            attributes = (ArrayList<String>) doc.get("attributes");
            x++;
        }
        if(x>1 || uid == null){
            return new FunctionResponse(false, "Failed to get patient");
        }

        // Generate & store new primary key
        attributes.add(attr);
        FunctionResponse keyRes = helper.genAndUpdatePrivKey(bucketName, attributes.toArray(new String[0]), uid);
        if(!keyRes.successful()){
            logger.error(keyRes.getMessage());
            return new FunctionResponse(false, "Key could not be updated");
        }
        

        //Add to firestore
        try {
            updateArray(uid, "attributes", attr);
            return new FunctionResponse(true, "Add successful");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Couldn't add attribute to firestore", e);
            return new FunctionResponse(false, "Couldn't add attribute");
        }
    }

    /**
     * Removes an attribute from a user and updates their private key
     * @param nhsNum
     * @param attr
     * @return
     */
    public FunctionResponse removeAttribute(String nhsNum, String attr){
         // Lookup patient from NHS num
         QuerySnapshot snapshot;
         try {
             snapshot = queryUsers("nhsnum", nhsNum);
         } catch (InterruptedException | ExecutionException e) {
             return new FunctionResponse(false, "Could not get from FireStore");
         }
         String uid = null;
         String bucketName = null;
         ArrayList<String> attributes = null;
         int x = 0;
         for(QueryDocumentSnapshot doc: snapshot.getDocuments()){
             uid = doc.getId();
             bucketName = doc.getString("bucketName");
             attributes = (ArrayList<String>) doc.get("attributes");
             x++;
         }
         if(x>1 || uid == null){
             return new FunctionResponse(false, "Failed to get patient");
        }

        // Generate & store new primary key
        attributes.remove(attr);
        FunctionResponse keyRes = helper.genAndUpdatePrivKey(bucketName, attributes.toArray(new String[0]), uid);
        if(!keyRes.successful()){
            return new FunctionResponse(false, "Key could not be updated");
        }

        DocumentReference docRef = this.db.collection("users").document(uid);
        try {
            docRef.update("attributes", FieldValue.arrayRemove(attr)).get();
            return new FunctionResponse(true, "Removed attribute");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Couldn't remove attribute from firestore", e);
            return new FunctionResponse(false, "Couldn't remove attribute from firestore");
        }
    }

    /**
     * Deletes a fileRef from a users files, and if last user with fileRef then deletes whole file
     * @param uid User ID of user to delete file from
     * @param fileRef File reference to delete
     * @return
     */
    public FunctionResponse deleteFile(String uid, String fileRef){
        //Remove file ref from user
        DocumentReference docRef = this.db.collection("users").document(uid);
        try {
            docRef.update("files", FieldValue.arrayRemove(fileRef)).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Couldn't remove file from user", e);
            return new FunctionResponse(false, "Couldn't remove file from firestore");
        }

        //Check if any others users still have file
        Boolean delete = false;
        try {
            QuerySnapshot query = this.db.collection("users").whereArrayContains("files", fileRef).get().get();
            if(query.isEmpty()){
                delete = true;
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Check if other users still have file failed", e);
            return new FunctionResponse(false, "Coulnd't perform nessasary checks");
        }
        if(delete){
            DocumentReference fileDocRef = this.db.collection("files").document(fileRef);
            try {
                String fileLocation = fileDocRef.get().get().getString("filepath");
                fileDocRef.delete().get();
                return new FunctionResponse(true, fileLocation);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Couldn't delete file: " + fileRef + " from firestore", e);
                return new FunctionResponse(false, "Couldn't delete file from firestore");
            }
        }
        return new FunctionResponse(true, "No delete needed");
    }
}