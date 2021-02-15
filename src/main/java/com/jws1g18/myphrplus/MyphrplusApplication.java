package com.jws1g18.myphrplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class MyphrplusApplication {
	GCPFireStore fireStore = new GCPFireStore();
	GCPCloudStorage cloudStorage = new GCPCloudStorage();

	public static void main(String[] args) {
		SpringApplication.run(MyphrplusApplication.class, args);
	}

	@GetMapping("/hello")
	public String hello(@RequestParam(value = "name", defaultValue = "World") String name) {
		return String.format("Hello %s!", name);
	}

	@RequestMapping(method = RequestMethod.POST, path = "/registerPatient")
	public String registerPatient(@RequestBody Patient patient) {
		return fireStore.addUser(patient.uid, patient.name, patient.email, "patient");
	}

}