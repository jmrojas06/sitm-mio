package SITM.datacenter.analytics.modelo;

/**
 * Modelo de resultado del cálculo de velocidad promedio por ruta y mes.
 *
 * Este objeto es producido por las tres versiones del calculador (V1, V2, V3)
 * y representa la unidad mínima de reporte que el enunciado requiere:
 * "velocidad promedio por ruta por mes".
 *
 * Se incluye el conteo de muestras para que el analista pueda evaluar
 * la confiabilidad estadística del promedio: una ruta con 50 muestras
 * tiene menos representatividad que una con 15.000.
 */
public class ResultadoVelocidad {

    public final int lineId;
    public final String shortName;
    public final String mes;
    public final double velocidadPromedioKmh;
    public final long cantidadMuestras;

    public ResultadoVelocidad(int lineId, String shortName, String mes,
                               double velocidadPromedioKmh, long cantidadMuestras) {
        this.lineId = lineId;
        this.shortName = shortName;
        this.mes = mes;
        this.velocidadPromedioKmh = velocidadPromedioKmh;
        this.cantidadMuestras = cantidadMuestras;
    }

    @Override
    public String toString() {
        return String.format("Ruta %-6s (ID %-5d) | %s | Vel. Prom: %5.2f km/h | Muestras: %,d",
                shortName, lineId, mes, velocidadPromedioKmh, cantidadMuestras);
    }
}
