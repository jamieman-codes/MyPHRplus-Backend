package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import co.junwei.bswabe.BswabeMsk;
import co.junwei.bswabe.BswabePrv;
import co.junwei.bswabe.BswabePub;
import co.junwei.bswabe.SerializeUtils;

public class Helpers {
    private Tika tika = new Tika();

    private static final List<String> validExtensions = Arrays
            .asList(new String[] { "pdf", "png", "jpg", "jpeg", "mp3" });
    private static final List<String> validTypes = Arrays
            .asList(new String[] { "audio/mpeg", "image/jpeg", "image/png", "application/pdf" });

    public FunctionResponse detectFileType(MultipartFile file) {
        // Check extension
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (!validExtensions.contains(extension)) {
            return new FunctionResponse(false, "Invalid File Extension");
        }
        // Check type
        String detectedType;
        try {
            detectedType = tika.detect(file.getBytes());
        } catch (IOException ex) {
            return new FunctionResponse(false, "Invalid File Type");
        }
        if (validTypes.contains(detectedType)) {
            return new FunctionResponse(true, extension + " " + detectedType);
        }
        return new FunctionResponse(false, "Invalid File Type");
    }

    public FunctionResponse genAndStorePrivKeys(String bucketName, String[] attributes, String uid){
        //Get public and master Keys
		byte[] mskByte;
		byte[] pubByte;
		try {
			mskByte = GCPSecretManager.getKeys(bucketName + "-master");
			pubByte = GCPSecretManager.getKeys(bucketName + "-public");
		} catch (IOException e) {
			return new FunctionResponse(false, "Couldn't access keys");
		}
		BswabePub pub = SerializeUtils.unserializeBswabePub(pubByte);
		BswabeMsk msk = SerializeUtils.unserializeBswabeMsk(pub, mskByte);

		// Gen private key
		BswabePrv prv = ABE.genPrivKey(pub, msk, attributes);

		//Store private key
		try {
			GCPSecretManager.storeKey(uid, SerializeUtils.serializeBswabePrv(prv));
            return new FunctionResponse(true, "Keys generated");
		} catch (IOException e) {
			return new FunctionResponse(false, "Couldn't store key");
		}
    }
}
