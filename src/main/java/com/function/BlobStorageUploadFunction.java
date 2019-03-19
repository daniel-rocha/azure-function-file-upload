package com.function;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.rest.v2.http.HttpPipeline;

import org.apache.commons.fileupload.MultipartStream;

import io.reactivex.Flowable;

import com.microsoft.azure.functions.*;

/**
 * Azure Functions with HTTP Trigger.
 */
public class BlobStorageUploadFunction {

  @FunctionName("blobStorageUpload")
  public HttpResponseMessage run(
      @HttpTrigger(name = "req", 
        methods = { HttpMethod.POST }, 
        authLevel = AuthorizationLevel.ANONYMOUS,
        dataType = "binary") 
      HttpRequestMessage<Optional<byte[]>> request,
      final ExecutionContext context) throws Exception {
    
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
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ) {
          context.getLogger().info("Saving file " + fileName);
          multipartStream.readBodyData(baos);
          upload(baos.toByteArray(), fileName);
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

  public static void upload(byte[] content, String fileName) throws InvalidKeyException, MalformedURLException {
      // From the Azure portal, get your Storage account's name and account key.
      String accountName = System.getenv("Storage_Account_Name");
      String accountKey = System.getenv("Storage_Account_Key");
      String containerName = System.getenv("Storage_Container_Name");

      // Use your Storage account's name and key to create a credential object; this is used to access your account.
      SharedKeyCredentials credential = new SharedKeyCredentials(accountName, accountKey);

      /*
      Create a request pipeline that is used to process HTTP(S) requests and responses. It requires your accont
      credentials. In more advanced scenarios, you can configure telemetry, retry policies, logging, and other
      options. Also you can configure multiple pipelines for different scenarios.
       */
      HttpPipeline pipeline = StorageURL.createPipeline(credential, new PipelineOptions());

      /*
      From the Azure portal, get your Storage account blob service URL endpoint.
      The URL typically looks like this:
       */
      URL u = new URL(String.format(Locale.ROOT, "https://%s.blob.core.windows.net", accountName));

      // Create a ServiceURL objet that wraps the service URL and a request pipeline.
      ServiceURL serviceURL = new ServiceURL(u, pipeline);

      // Now you can use the ServiceURL to perform various container and blob operations.

      // This example shows several common operations just to get you started.

      /*
      Create a URL that references a to-be-created container in your Azure Storage account. This returns a
      ContainerURL object that wraps the container's URL and a request pipeline (inherited from serviceURL).
      Note that container names require lowercase.
       */
      ContainerURL containerURL = serviceURL.createContainerURL(containerName);

      /*
      Create a URL that references a to-be-created blob in your Azure Storage account's container.
      This returns a BlockBlobURL object that wraps the blob's URl and a request pipeline
      (inherited from containerURL). Note that blob names can be mixed case.
       */
      BlockBlobURL blobURL = containerURL.createBlockBlobURL(fileName);

      blobURL.upload(Flowable.just(ByteBuffer.wrap(content)), content.length,
        null, null, null, null).blockingGet();
  }
}
