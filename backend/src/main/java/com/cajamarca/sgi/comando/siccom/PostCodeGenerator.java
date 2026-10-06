package com.cajamarca.sgi.comando.siccom;

import java.text.Normalizer;
import java.util.Locale;

final class PostCodeGenerator {
  private PostCodeGenerator() {}

  static String baseCode(String province, String city, String clientName, String postName) {
    return initial(province, "province")
        + initial(city, "city")
        + initial(clientName, "client.name")
        + initial(postName, "posts.name");
  }

  static String sourceSignature(String province, String city, String clientName, String postName) {
    return signaturePart(province) + "|" + signaturePart(city) + "|"
        + signaturePart(clientName) + "|" + signaturePart(postName);
  }

  /** index 0 has no discriminator; 1..26 are A..Z; 27 is AA. */
  static String discriminator(int index) {
    if (index < 0) throw new IllegalArgumentException("El índice de discriminador no puede ser negativo.");
    if (index == 0) return "";
    StringBuilder value = new StringBuilder();
    for (int current = index; current > 0; current = (current - 1) / 26) {
      value.append((char) ('A' + ((current - 1) % 26)));
    }
    return value.reverse().toString();
  }

  private static String initial(String value, String field) {
    String ascii = ascii(value);
    for (int i = 0; i < ascii.length(); i++) {
      char c = ascii.charAt(i);
      if (c >= 'A' && c <= 'Z') return String.valueOf(c);
    }
    throw new IllegalArgumentException(field + " debe contener al menos una letra de A a Z para generar el código del Puesto.");
  }

  private static String signaturePart(String value) {
    return ascii(value).replaceAll("[^A-Z0-9]+", "");
  }

  private static String ascii(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toUpperCase(Locale.ROOT);
  }
}
