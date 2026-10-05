package com.cajamarca.sgi.comando.visint;

import jakarta.ws.rs.BadRequestException;
import java.math.*;

/** Umbral de coincidencia de VISINT (matchThreshold): 0.00 a 1.00, dos decimales. null = predeterminado de SGI. */
public final class MatchThreshold {
    /** Hasta este valor el umbral es bajo: VISINT podría aceptar fotos poco parecidas a las estándar. */
    public static final double LOW = 0.40;
    private MatchThreshold() {}

    /** Valida y redondea a dos decimales el umbral que llega de la web. */
    public static Double normalize(Double value) {
        if (value == null) return null;
        if (value.isNaN() || value < 0 || value > 1) throw new BadRequestException("El umbral de coincidencia debe estar entre 0.00 y 1.00");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Texto que se envía a VISINT: siempre con dos decimales y punto ("0.80"). */
    public static String format(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
