package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.ArrayList;
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
    /**
     * Just some helper functions
     */
    private Tika tika = new Tika();

    private static final List<String> validExtensions = Arrays
            .asList(new String[] { "pdf", "png", "jpg", "jpeg", "mp3" });
    private static final List<String> validTypes = Arrays
            .asList(new String[] { "audio/mpeg", "image/jpeg", "image/png", "application/pdf" });

    /**
     * Detects a file type of a Multipartfile
     * @param file
     * @return
     */
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

    /**
     * Generates and stores a private key
     * @param bucketName Name of bucket that holds public key
     * @param attributes string array of attributes
     * @param uid user id 
     * @return
     */
    public FunctionResponse genAndStorePrivKeys(String bucketName, String[] attributes, String uid){
        BswabePrv prv;
        try{
            prv = genPrivKey(bucketName, attributes, uid);
        }catch (IOException e) {
			return new FunctionResponse(false, "Couldn't access keys");
		}

		//Store private key
		try {
			GCPSecretManager.storeKey(uid, SerializeUtils.serializeBswabePrv(prv));
            return new FunctionResponse(true, "Keys generated");
		} catch (IOException e) {
			return new FunctionResponse(false, "Couldn't store key");
		}
    }

    /**
     * Generates and updates a private key
     * @param bucketName Name of bucket that holds public key
     * @param attributes string array of attributes
     * @param uid user ID
     * @return
     */
    public FunctionResponse genAndUpdatePrivKey(String bucketName, String[] attributes, String uid){
        BswabePrv prv;
        try{
            prv = genPrivKey(bucketName, attributes, uid);
        }catch (IOException e) {
			return new FunctionResponse(false, "Couldn't access keys");
		}

        //Add new key version
        try{
            GCPSecretManager.destroySecretVersion(uid);
			GCPSecretManager.addSecretVersion(uid, SerializeUtils.serializeBswabePrv(prv));
            return new FunctionResponse(true, "Keys generated");
        } catch (IOException e) {
			return new FunctionResponse(false, "Couldn't store key");
		}
    }

    /**
     * Generates a private key
     * @param bucketName Name of bucket that holds public key
     * @param attributes string array of attributes
     * @param uid
     * @return 
     * @throws IOException
     */
    private BswabePrv genPrivKey(String bucketName, String[] attributes, String uid) throws IOException{
        //Get public and master Keys
        byte[] mskByte = GCPSecretManager.getKeys(bucketName + "-master");
        byte[] pubByte = GCPSecretManager.getKeys(bucketName + "-public");
	
		BswabePub pub = SerializeUtils.unserializeBswabePub(pubByte);
		BswabeMsk msk = SerializeUtils.unserializeBswabeMsk(pub, mskByte);

		// Gen private key
		return ABE.genPrivKey(pub, msk, attributes);
    }

    /**
     * Parse's patient input into a valid access policy for encryption
     * @param accessPolicy Values selected for encryption
     * @param customAccessPolicy Custom policy, if selected
     * @param policyUids UIDs of users selected
     * @return access policy as a string
     */
    public String parsePatientPolicy(ArrayList<String> accessPolicy, String customAccessPolicy, ArrayList<String> policyUids){
        String policy = "";
        Boolean custom = false;
        if (accessPolicy.contains("custom")) {
            accessPolicy.remove("custom");
            custom = true;
        }
        if(accessPolicy.size() == 0 && custom){
            long count = customAccessPolicy.chars().filter(ch -> ch == ',').count() + 1;
            if(count == 1){
                policy += customAccessPolicy.replace(",", " ") + " notObtainable 1of2";
            }
            else{
                policy += customAccessPolicy.replace(",", " ") + " " + count + "of" + count;
            }
        }
        else if(accessPolicy.size() == 1 && !custom){
            for (String uid : policyUids) {
                policy += "uid_" + uid + " ";
            }
            policy += "notObtainable 1of2";
        }
        else if(accessPolicy.size() == 1 && custom){
            for (String uid : policyUids) {
                policy += "uid_" + uid + " ";
            }
            policy += "notObtainable 1of2 ";
            long count = customAccessPolicy.chars().filter(ch -> ch == ',').count() + 1;
            if(count == 1){
                policy += customAccessPolicy.replace(",", " ") + " notObtainable 1of2 1of2";
            }
            else{
                policy += customAccessPolicy.replace(",", " ") + " " + count + "of" + count + " 1of2";
            }
        }
        else{
            int x = 0;
            for (String uid : policyUids) {
                x++;
                policy += "uid_" + uid + " ";
            }
            policy += "1of" + x + " ";
            if (custom) {
                long count = customAccessPolicy.chars().filter(ch -> ch == ',').count() + 1;
                if(count == 1){
                    policy += customAccessPolicy.replace(",", " ") + " notObtainable 1of2 1of2";
                }
                else{
                    policy += customAccessPolicy.replace(",", " ") + " " + count + "of" + count + " 1of2";
                }
            }
        }
        return policy;
    }
}
