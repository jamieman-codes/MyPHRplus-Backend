package com.jws1g18.myphrplus;

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
        cs.createBucket("southampton-general-hospital");
    }

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
}
