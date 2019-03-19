package com.function;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import com.microsoft.azure.functions.annotation.*;

import org.apache.commons.fileupload.MultipartStream;

import com.microsoft.azure.functions.*;

/**
 * Azure Functions with HTTP Trigger.
 */
public class FileSystemUploadFunction {

  public static final String BASEPATH = "C:\\tmp\\";

  @FunctionName("fileSystemUpload")
  public HttpResponseMessage run(
      @HttpTrigger(name = "req", 
        methods = { HttpMethod.POST }, 
        authLevel = AuthorizationLevel.ANONYMOUS,
        dataType = "binary") 
      HttpRequestMessage<Optional<byte[]>> request,
      final ExecutionContext context) throws IOException {
    
    // start file upload
    context.getLogger().info("Java HTTP file upload started with headers " + request.getHeaders());

    // parse headers
    String contentType = request.getHeaders().get("content-type"); // Get content-type header
    // here the "content-type" must be lower-case
    byte[] bs = request.getBody().get();
    InputStream in = new ByteArrayInputStream(bs); // Convert body to an input stream
    String boundary = contentType.split(";")[1].split("=")[1]; // Get boundary from content-type header
    int bufSize = 1024;
    
    MultipartStream multipartStream = new MultipartStream(in, boundary.getBytes(), bufSize, null); // Using
                                                                                                   // MultipartStream to
                                                                                                   // parse body input
                                                                                                   // stream
    // read files into the specified folder    
    boolean nextPart = multipartStream.skipPreamble();
    
    while (nextPart) {
      String header = multipartStream.readHeaders();

      if (header.contains("filename")) {
        // parse file and save to disk
        String fileName = extractFileName(header);
        context.getLogger().info("Processed part with header " + header);
        context.getLogger().info("Processed part with filename " + fileName);

        // try with resources
        try (
          FileOutputStream fos = new FileOutputStream(BASEPATH + fileName);
        ) {
          context.getLogger().info("Saving file " + BASEPATH + fileName);
          multipartStream.readBodyData(fos);
        }
      }
      else {
        // parse text field and do something with it
        context.getLogger().info("Processed text field");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        multipartStream.readBodyData(baos);
        System.err.println("Field is " + baos.toString());
      }

      nextPart = multipartStream.readBoundary();
    }

    // return response
    context.getLogger().info("Java HTTP file upload ended. Length: " + bs.length);
    return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + bs.length).build();
  }

  // extracts file name from a multipart boundary
  public static String extractFileName(String header) {
    final String FILENAME_PARAMETER = "filename=";
    final int FILENAME_INDEX = header.indexOf(FILENAME_PARAMETER);
    String name = header.substring(FILENAME_INDEX + FILENAME_PARAMETER.length(), header.lastIndexOf("\""));
    String fileName = name.replaceAll("\"" , "").replaceAll(" ", "");

    return fileName;
  }
}
