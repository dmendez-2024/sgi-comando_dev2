package com.cajamarca.sgi.comando.security;

import java.util.regex.Pattern;

/** Rutas de SGI: Comando que consume la app SGI Operador (interconexiones SGI_OPR_SGI_COM_*). */
public final class OperatorRoutes {
    private static final Pattern SUPERVISOR_ROUTE = Pattern.compile("/?api/coordination/posts/[^/]+/route/?");
    private static final Pattern SUPERVISOR_POSTS = Pattern.compile("/?api/post-configurations/?");

    private OperatorRoutes() {}

    /** /api/v1/operator/**: API del operador (la web tambien lee algunas, con su propio usuario). */
    public static boolean isOperatorApi(String path) {
        return path != null && (path.startsWith("/api/v1/operator") || path.startsWith("api/v1/operator"));
    }

    /** Rutas que puede llamar el token de SGI Operador: su API y las dos lecturas del Supervisor (ruta y puestos del punto). */
    public static boolean allowsOperatorApp(String method, String path) {
        if (isOperatorApi(path)) return true;
        return "GET".equalsIgnoreCase(method) && path != null
                && (SUPERVISOR_ROUTE.matcher(path).matches() || SUPERVISOR_POSTS.matcher(path).matches());
    }
}
