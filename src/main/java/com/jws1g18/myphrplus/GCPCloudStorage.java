package com.jws1g18.myphrplus;

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;

import org.slf4j.Logger;
import org.springframework.core.io.ByteArrayResource;

public class GCPCloudStorage {
    Storage storage;
    String projectID = "myphrplus-backend";
    Logger logger;
    ABE abeController = new ABE();

    public GCPCloudStorage(Logger logger) {
        this.storage = StorageOptions.getDefaultInstance().getService();

        this.logger = logger;
    }

    /**
     * Creates a bucket given a bucket name
     * 
     * @param bucketName
     * @return The created bucket
     */
    public String createBucket(String bucketName) {
        bucketName += "-myphrplus-backend";
        logger.info("Creating bucket " + bucketName);

        StorageClass storageClass = StorageClass.STANDARD;
        String location = "EUROPE-WEST2"; // London

        Bucket bucket = this.storage
                .create(BucketInfo.newBuilder(bucketName).setStorageClass(storageClass).setLocation(location).build());

        // System.out.println("Created bucket "+ bucket.getName());

        return bucket.getName();
    }

    /**
     * Uploads an a Multi Part File to google cloud storage
     * 
     * @param bucketName
     * @param objectName ID of the GCP object
     * @param filePath   path to file on the system
     */
    public FunctionResponse uploadFile(String bucketName, String objectName, byte[] file, String type) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("Content-Type", type);

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setMetadata(metadata).build();

        this.storage.create(blobInfo, file);
        return new FunctionResponse(true, "Upload successful");
    }

    public ByteArrayResource downloadObject(String bucketName, String objectName) {
        BlobId blob = BlobId.of(bucketName, objectName);
        return new ByteArrayResource(storage.readAllBytes(blob));
    }
}
