package com.jws1g18.myphrplus;

public class FunctionResponse {
    private Boolean success;
    private String message;

    public FunctionResponse(Boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public Boolean successful() {
        return this.success;
    }

    public String getMessage() {
        return this.message;
    }
}
