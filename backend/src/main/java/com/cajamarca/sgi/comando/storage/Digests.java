package com.cajamarca.sgi.comando.storage;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** SHA-256 en hexadecimal (minúsculas) para fotos y archivos. */
public final class Digests {
    private Digests() {}

    public static String sha256Hex(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String sha256Hex(Path file) throws IOException {
        try (DigestInputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(in.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
