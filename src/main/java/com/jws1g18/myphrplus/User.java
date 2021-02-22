package com.jws1g18.myphrplus;

import javax.json.Json;

public class User {
    public String uid = null;
    public String name;
    public String email;
    public String role;

    public String convertToJson() {
        return Json.createObjectBuilder().add("name", name).add("email", email).add("role", role).build().toString();
    }
}
