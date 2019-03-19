package com.function;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
//import java.io.File;
import java.io.IOException;

public class BlobResizeFunction {

  private BufferedImage watermarkImage = null;

  @FunctionName("blobResizeFunction")
  @StorageAccount("Storage_Account_Setting")
  // @StorageAccount("AzureWebJobsStorage")
  public void run(@BlobTrigger(name = "file", dataType = "binary", path = "inputimg/{name}") byte[] arInputImage,
      @BindingName("name") String filename,
      @BlobInput(name = "watermark", dataType = "binary", path = "utilimg/Doopedia_watermark_400.png") byte[] arWatermark,
      @BlobOutput(name = "target1", dataType = "binary", path = "outimg-px400/{name}") OutputBinding<byte[]> outimgpx400,
      @BlobOutput(name = "target2", dataType = "binary", path = "outimg-px1024/{name}") OutputBinding<byte[]> outimgpx1024,
      @BlobOutput(name = "target3", dataType = "binary", path = "outimg-px1920/{name}") OutputBinding<byte[]> outimgpx1920,
      final ExecutionContext context) {

    long startTime = System.currentTimeMillis();

    context.getLogger().info("Java Blob trigger processed a request.");
    context.getLogger().info("Name: " + filename + " Size: " + arInputImage.length + " bytes");

    try {
      // load watermark
      this.loadWatermark(arWatermark);
      context.getLogger()
          .info(String.format("timeElapsed load watermark: %f", (System.currentTimeMillis() - startTime) / 1000.0));

      // load inputImage
      InputStream streamInputImage = new ByteArrayInputStream(arInputImage);
      BufferedImage bufInputImage = ImageIO.read(streamInputImage);
      context.getLogger()
          .info(String.format("timeElapsed load inputimg: %f", (System.currentTimeMillis() - startTime) / 1000.0));

      // resize images
      BufferedImage bufResultImage1920 = resize(bufInputImage, 1920);
      BufferedImage bufResultImage1024 = resize(bufResultImage1920, 1024);
      BufferedImage bufResultImage400 = resize(bufResultImage1024, 400);
      context.getLogger()
          .info(String.format("timeElapsed resize: %f", (System.currentTimeMillis() - startTime) / 1000.0));

      // add watermark to images
      bufResultImage400 = addWatermark(bufResultImage400, watermarkImage);
      bufResultImage1024 = addWatermark(bufResultImage1024, watermarkImage);
      bufResultImage1920 = addWatermark(bufResultImage1920, watermarkImage);
      context.getLogger()
          .info(String.format("timeElapsed add watermark: %f", (System.currentTimeMillis() - startTime) / 1000.0));

      // save outputImage
      ByteArrayOutputStream streamOutImage = new ByteArrayOutputStream();
      ImageIO.write(bufResultImage400, "jpg", streamOutImage);
      outimgpx400.setValue(streamOutImage.toByteArray());
      streamOutImage.reset();
      ImageIO.write(bufResultImage1024, "jpg", streamOutImage);
      outimgpx1024.setValue(streamOutImage.toByteArray());
      streamOutImage.reset();
      ImageIO.write(bufResultImage1920, "jpg", streamOutImage);
      outimgpx1920.setValue(streamOutImage.toByteArray());

    } catch (IOException e1) {
      context.getLogger().warning("exception error!!!!!!!!!!!!!!:" + e1.getMessage());
      e1.printStackTrace();
    }

    context.getLogger()
        .info(String.format("timeElapsed finish: %f", (System.currentTimeMillis() - startTime) / 1000.0));
  }

  private static BufferedImage resize(BufferedImage bufResizingImage, int width) {
    // adjust size
    double w_ratio = (double) bufResizingImage.getWidth() / (double) width;
    int height = (int) (bufResizingImage.getHeight() / w_ratio);

    // resizing
    Image image = bufResizingImage.getScaledInstance(width, height, Image.SCALE_FAST);
    BufferedImage resized = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
    // Graphics2D g2d = resized.createGraphics();
    Graphics2D g2d = (Graphics2D) resized.getGraphics();
    g2d.drawImage(image, 0, 0, null);
    g2d.dispose();

    System.out.println("resized.");
    return resized;
  }

  private static BufferedImage addWatermark(BufferedImage sourceImage, BufferedImage watermarkImage) {

    Graphics2D g2d = (Graphics2D) sourceImage.getGraphics();
    AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f); // transparent
    g2d.setComposite(alphaChannel);
    g2d.drawImage(watermarkImage, 0, 0, null);
    g2d.dispose();

    System.out.println("added watermark.");
    return sourceImage;
  }

  private void loadWatermark(byte[] arWatermark) throws IOException {
    // load watermark
    if (this.watermarkImage == null) {
      this.watermarkImage = ImageIO.read(new ByteArrayInputStream(arWatermark));
    }
  }
}
