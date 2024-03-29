package com.jws1g18.myphrplus.DTOS;

import java.util.ArrayList;

import javax.json.Json;

public class User {
    public String uid = "";
    public String name;
    public String email;
    public String role;
    public String bucketName = "";
    public String password = "";
    public String parent = "";
    public String nhsnum;
    public ArrayList<String> attributes;
    public ArrayList<String> patients;
    public ArrayList<String> dataRequesters;
    public ArrayList<String> files;
    public ArrayList<String> reminders;

    public String convertToJson() {
        return Json.createObjectBuilder().add("name", name).add("email", email).add("role", role).build().toString();
    }
}
