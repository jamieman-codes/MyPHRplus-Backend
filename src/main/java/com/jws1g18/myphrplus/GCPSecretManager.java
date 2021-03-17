package com.jws1g18.myphrplus;

import java.io.IOException;

import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.protobuf.ByteString;

public class GCPSecretManager {
    static final ProjectName projectName = ProjectName.of("myphrplus-backend");

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

    public static byte[] getKeys(String secretId) throws IOException{
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectName.getProject(), secretId, "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            byte[] res = response.getPayload().getData().toByteArray();
            client.close();
            return res;
        }
    }

}
