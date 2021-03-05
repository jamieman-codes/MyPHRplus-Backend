package com.jws1g18.myphrplus;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

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
}
