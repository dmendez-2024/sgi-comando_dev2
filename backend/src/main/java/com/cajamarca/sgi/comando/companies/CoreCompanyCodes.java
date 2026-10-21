package com.cajamarca.sgi.comando.companies;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CORE dejó de enviar code y sourceVersion en /catalog/companies (la identidad es solo el id).
 * SGI: Comando conserva el código que ya tenía cada Compañía y, para las nuevas, genera uno local
 * con el mismo formato COM-NNN. La versión se calcula del contenido recibido para que solo cambie
 * cuando cambia la Compañía en CORE.
 */
public final class CoreCompanyCodes {
    private static final Pattern SEQUENTIAL = Pattern.compile("COM-(\\d+)");

    private CoreCompanyCodes() {}

    /** Códigos ya usados en el tenant por Compañías operacionales y por el snapshot del catálogo CORE. */
    public static Set<String> usedCodes(UUID instanceCountryId) {
        Set<String> used = new HashSet<>();
        Company.<Company>list("instanceCountryId=?1", instanceCountryId).forEach(c -> used.add(normalize(c.code)));
        CoreCompanyCatalogSnapshot.<CoreCompanyCatalogSnapshot>list("instanceCountryId=?1", instanceCountryId).forEach(c -> used.add(normalize(c.code)));
        return used;
    }

    /** Siguiente COM-NNN libre; lo reserva en {@code used} para que no se repita en la misma sincronización. */
    public static String next(Set<String> used) {
        int max = 0;
        for (String code : used) {
            Matcher m = code == null ? null : SEQUENTIAL.matcher(code);
            if (m != null && m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        String code;
        do { code = String.format(Locale.ROOT, "COM-%03d", ++max); } while (used.contains(code));
        used.add(code);
        return code;
    }

    /** Versión estable derivada del contenido CORE: igual mientras la Compañía no cambie. */
    public static String sourceVersion(UUID coreCompanyId, String name, String description, String logoUrl, String companyType, String status) {
        String content = String.join("\u001F", String.valueOf(coreCompanyId), String.valueOf(name), String.valueOf(description),
            String.valueOf(logoUrl), String.valueOf(companyType), String.valueOf(status));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return "CORE-" + HexFormat.of().formatHex(hash, 0, 8).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible.", e);
        }
    }

    private static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
