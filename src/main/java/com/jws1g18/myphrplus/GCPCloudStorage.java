package com.jws1g18.myphrplus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;

public class GCPCloudStorage {
    Storage storage;
    String projectID = "myphrplus-backend";

    public GCPCloudStorage(){
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    public static void main(String[] args) {
        GCPCloudStorage cs = new GCPCloudStorage();
        //cs.createBucket("southampton-general-hospital");
        //cs.uploadObject("southampton-general-hospital-myphrplus-backend", "test/liverpool", "C://Users/jamie/Downloads/liverpool-gettyimages-12578371241.jpeg");
        cs.downloadObject("southampton-general-hospital-myphrplus-backend", "test/liverpool", "C://Users/jamie/OneDrive/Documents/liverpool.jpeg");
    }

    /**
     * Creates a bucket given a bucket name
     * @param bucketName
     * @return The created bucket
     */
    public Bucket createBucket(String bucketName){
        bucketName += "-myphrplus-backend";

        StorageClass storageClass = StorageClass.STANDARD;
        String location = "EUROPE-WEST2"; //London 

        Bucket bucket = 
            this.storage.create(
                BucketInfo.newBuilder(bucketName)
                    .setStorageClass(storageClass)
                    .setLocation(location)
                    .build());
        
                    
        //System.out.println("Created bucket "+ bucket.getName());

        return bucket;
    }

    /**
     * FUNCTION WILL NEED CHANGING. THIS WILL ONLY WORK WITH LOCAL FILES!!!
     * Uploads an object to google cloud storage
     * @param bucketName 
     * @param objectName ID of the GCP object
     * @param filePath path to file on the system 
     */
    public void uploadObject(String bucketName, String objectName, String filePath){
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        try{
            this.storage.create(blobInfo, Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException ex){
            return;
        }
    }

    public void downloadObject(String bucketName, String objectName, String destFilePath){
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        blob.downloadTo(Paths.get(destFilePath));

    }
}
