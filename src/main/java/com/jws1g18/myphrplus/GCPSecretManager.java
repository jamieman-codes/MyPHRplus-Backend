package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.Iterator;

import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient.ListSecretVersionsPagedResponse;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.protobuf.ByteString;

public class GCPSecretManager {
    static final ProjectName projectName = ProjectName.of("myphrplus-backend");

    /**
     * Stores a key in the projects secret manager
     * @param secretId ID of secret to be stored
     * @param keys byte array of key
     * @return true if successful 
     * @throws IOException 
     */
    public static boolean storeKey(String secretId, byte[] keys) throws IOException{
        try(SecretManagerServiceClient client = SecretManagerServiceClient.create()){
            Secret secret =
                Secret.newBuilder()
                    .setReplication(
                        Replication.newBuilder()
                            .setAutomatic(Replication.Automatic.newBuilder().build())
                            .build())
                        .build();

            Secret createdSecret = client.createSecret(projectName, secretId, secret);

            SecretPayload payload = SecretPayload.newBuilder().setData(ByteString.copyFrom(keys)).build();
            client.addSecretVersion(createdSecret.getName(), payload);

            client.close();
            return true;
        }
    }

    /**
     * Gets a key from the projects secret manager
     * @param secretId ID of key to get
     * @return byte array of key
     * @throws IOException
     */
    public static byte[] getKeys(String secretId) throws IOException{
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectName.getProject(), secretId, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            byte[] res = response.getPayload().getData().toByteArray();
            client.close();
            return res;
        }
    }

    /**
     * Destroys a key, for use when a new private key is being made
     * @param secretId ID of key to be destroyed
     * @throws IOException
     */
    public static boolean destroySecretVersion(String secretId) throws IOException{
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            
            SecretName secretName = SecretName.of(projectName.getProject(), secretId);
            ListSecretVersionsPagedResponse pagedResponse = client.listSecretVersions(secretName);

            //Get lastest secret verison 
            Iterator<SecretVersion> it = pagedResponse.iterateAll().iterator();
            SecretVersion secretVersion = it.next();

            client.destroySecretVersion(secretVersion.getName());
            client.close();
            return true;
        }
    }

    /**
     * Adds a new version to a current secret
     * @param secretId ID of secret to add new version too
     * @param key byte array of key
     * @throws IOException
     */
    public static boolean addSecretVersion(String secretId, byte[] key) throws IOException{
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretName secretName = SecretName.of(projectName.getProject(), secretId);
            // Create the secret payload.
            SecretPayload payload =
            SecretPayload.newBuilder()
                .setData(ByteString.copyFrom(key))
                .build();
            client.addSecretVersion(secretName, payload);
            client.close();
            return true;
        }
    }
}
