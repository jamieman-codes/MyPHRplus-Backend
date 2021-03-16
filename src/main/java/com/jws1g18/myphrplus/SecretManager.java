package com.jws1g18.myphrplus;

import org.slf4j.Logger;

public class SecretManager {
    Logger logger;

    public SecretManager(Logger logger){
        this.logger = logger;
    }

    public void initClient(){
        SecretManagerServiceClient client = SecretManagerServiceClient.create()
    }

    public void closeClient(){}
}
