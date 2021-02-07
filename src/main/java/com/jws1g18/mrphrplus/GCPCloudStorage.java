package com.jws1g18.mrphrplus;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class GCPCloudStorage {
    Storage storage;

    public GCPCloudStorage(){
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    public static void main(String[] args) {
        GCPCloudStorage cs = new GCPCloudStorage();
        cs.createBucket("MyPHRPlusBackend-testbucket");
    }

    public Boolean createBucket(String bucketName){
        Bucket bucket = storage.create(BucketInfo.of(bucketName));
        return true;
    }
}
