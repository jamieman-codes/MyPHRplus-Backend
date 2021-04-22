# MyPHRplus-Backend

Backend for myPHRplus a secure Personal Health Record web application built upon Google Cloud

Install local jars:
```
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file -Dfile="lib/jpbc-api-1.2.1.jar"
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file -Dfile="lib/jpbc-plaf-1.2.1.jar"
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file -Dfile="lib/cpabe-api-1.0.2.jar"
```

Run locally with: 
```
mvn spring-boot:run
```

Deploy to cloud with:
```
gcloud config configurations activate backend
mvn package appengine:deploy
```