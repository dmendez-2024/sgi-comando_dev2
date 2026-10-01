package com.cajamarca.sgi.comando.storage;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ImageSnifferTest {
  static byte[] fixture(String n) throws Exception { return Files.readAllBytes(Path.of("src/test/resources/fixtures/"+n)); }
  @Test void detectsJpegAndPng() throws Exception {
    assertEquals("image/jpeg", ImageSniffer.detect(fixture("sample.jpg")));
    assertEquals("image/png", ImageSniffer.detect(fixture("sample.png")));
  }
  @Test void detectsWebp() {
    byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    assertEquals("image/webp", ImageSniffer.detect(webp));
  }
  @Test void rejectsTextAndTiny() throws Exception {
    assertNull(ImageSniffer.detect(fixture("not-an-image.txt")));
    assertNull(ImageSniffer.detect(new byte[]{(byte)0xFF}));
  }
  @Test void extensions() { assertEquals("jpg", ImageSniffer.extension("image/jpeg")); assertEquals("webp", ImageSniffer.extension("image/webp")); }
  @Test void sha256IsLowerHex() { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Digests.sha256Hex("abc".getBytes())); }
}
