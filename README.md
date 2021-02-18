# MyPHRplus-Backend

Backend for myPHRplus a secure Personal Health Record web application built upon Google Cloud

Run locally with: 
```
mvn spring-boot:run
```

Deploy to cloud with:
```
gcloud config configurations activate backend
mvn -DskipTests package appengine:deploy
```