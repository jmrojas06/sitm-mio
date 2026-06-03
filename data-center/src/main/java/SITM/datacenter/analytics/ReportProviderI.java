package SITM.datacenter.analytics;

import SITM.ReportProvider;
import SITM.SpeedReport;
import SITM.datacenter.analytics.modelo.ResultadoVelocidad;
import SITM.datacenter.analytics.v1.CalculadorVelocidadV1;

import com.zeroc.Ice.Current;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Componente C5.2 del diagrama de deployment: Motor de Cálculo de Velocidad.
 * Pertenece al Tier 2 — Data Center, nodo N5, capa de analítica histórica.
 *
 * Esta clase calcula las velocidades promedio por ruta y mes al arrancar el
 * Data Center, usando la Versión 1 (monolítica) del calculador sobre el archivo
 * de datagramas del piloto. Los resultados se cachean en memoria para responder
 * las consultas del Portal CCO (N4) de forma instantánea.
 *
 * La interfaz Ice ReportProvider es el contrato que conecta este componente
 * con el visualizador: getMonthlyReports() retorna todos los reportes de un año
 * y getAverageSpeed() permite consultar una ruta específica.
 *
 * En producción, el cálculo se haría sobre los datagramas archivados por C5.1,
 * no sobre el CSV estático del piloto, y se recalcularía periódicamente.
 */
public class ReportProviderI implements ReportProvider {

    // Caché de reportes calculados al inicio. Clave: "lineId_año-mes"
    private final Map<String, SpeedReport> reportesCacheados = new HashMap<>();

    /**
     * Calcula todos los reportes de velocidad al instanciar el componente.
     *
     * Se usa V1 (monolítica) porque el cálculo ocurre una sola vez al arrancar.
     * El resultado se guarda en el caché para que las consultas posteriores
     * del Portal CCO sean instantáneas sin recalcular sobre el CSV.
     *
     * @param archivoDatagramas ruta al CSV de datagramas históricos
     * @param rutasActivas      conjunto de lineIds del piloto
     * @param nombreRutas       mapa lineId → shortName para el reporte
     */
    public ReportProviderI(String archivoDatagramas,
                            Set<Integer> rutasActivas,
                            Map<Integer, String> nombreRutas) {

        System.out.println("C5.2 — Iniciando cálculo de velocidades promedio por ruta...");

        CalculadorVelocidadV1 calculador = new CalculadorVelocidadV1();
        List<ResultadoVelocidad> resultados = calculador.calcular(
                archivoDatagramas, rutasActivas, nombreRutas);

        for (ResultadoVelocidad r : resultados) {
            String[] partesMes = r.mes.split("-");
            int anio = Integer.parseInt(partesMes[0]);
            int mes  = Integer.parseInt(partesMes[1]);

            SpeedReport reporte = new SpeedReport();
            reporte.lineId       = r.lineId;
            reporte.year         = anio;
            reporte.month        = mes;
            reporte.averageSpeed = r.velocidadPromedioKmh;

            reportesCacheados.put(r.lineId + "_" + r.mes, reporte);
        }

        System.out.printf("C5.2 — %d reportes de velocidad calculados y listos para consulta.%n",
                reportesCacheados.size());
    }

    /**
     * Retorna el reporte de velocidad promedio de una ruta en un mes específico.
     * Es consultado por el Portal CCO cuando el controlador quiere ver
     * el desempeño histórico de una ruta en particular.
     */
    @Override
    public SpeedReport getAverageSpeed(int lineId, int month, int year, Current current) {
        String mes   = String.format("%d-%02d", year, month);
        String clave = lineId + "_" + mes;
        SpeedReport reporte = reportesCacheados.get(clave);
        if (reporte == null) {
            // Retornar reporte vacío si no hay datos para esa combinación
            reporte = new SpeedReport();
            reporte.lineId       = lineId;
            reporte.month        = month;
            reporte.year         = year;
            reporte.averageSpeed = 0.0;
        }
        return reporte;
    }

    /**
     * Retorna todos los reportes de velocidad disponibles para un año.
     * El Portal CCO llama a este método al arrancar para cargar el panel
     * de estadísticas de velocidad en el mapa de monitoreo.
     */
    @Override
    public SpeedReport[] getMonthlyReports(int year, Current current) {
        List<SpeedReport> reportesDelAnio = new ArrayList<>();
        for (SpeedReport reporte : reportesCacheados.values()) {
            if (reporte.year == year) {
                reportesDelAnio.add(reporte);
            }
        }
        return reportesDelAnio.toArray(new SpeedReport[0]);
    }
}
