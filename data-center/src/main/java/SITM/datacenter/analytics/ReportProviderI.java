package SITM.datacenter.analytics;

import SITM.ReportProvider;
import SITM.SpeedReport;
import SITM.datacenter.analytics.modelo.ResultadoVelocidad;
import SITM.datacenter.analytics.v1.CalculadorVelocidadV1;
import SITM.datacenter.analytics.v3.SpeedMaster;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Componente C5.2 del diagrama de deployment: Motor de Cálculo de Velocidad.
 * Pertenece al Tier 2 — Data Center, nodo N5, capa de analítica histórica.
 *
 * Estrategia de cálculo adaptativa según disponibilidad de Workers:
 *
 *   Con workers (sitm.workers configurado): usa SpeedMaster (V3) para distribuir
 *   el cálculo entre los Workers disponibles. Cada Worker procesa un subconjunto
 *   de rutas en paralelo, reduciendo el tiempo total proporcionalmente.
 *
 *   Sin workers: usa CalculadorVelocidadV1 (streaming, O(B) memoria). Correcto
 *   para cualquier volumen sin riesgo de OutOfMemoryError.
 *
 * Los resultados se cachean en memoria para que las consultas del Portal CCO
 * sean instantáneas sin recalcular sobre el CSV.
 */
public class ReportProviderI implements ReportProvider {

    private final Map<String, SpeedReport> reportesCacheados = new HashMap<>();

    /**
     * @param archivoDatagramas ruta al CSV de datagramas históricos
     * @param rutasActivas      conjunto de lineIds del piloto
     * @param nombreRutas       mapa lineId → shortName para el reporte
     */
    public ReportProviderI(String archivoDatagramas,
                            Set<Integer> rutasActivas,
                            Map<Integer, String> nombreRutas) {

        System.out.println("C5.2 — Iniciando cálculo de velocidades promedio por ruta...");

        List<ResultadoVelocidad> resultados;
        String workersConfig = System.getProperty("sitm.workers", "").trim();

        if (!workersConfig.isEmpty()) {
            // V3 distribuida: el Master divide las rutas entre los Workers.
            // Cada Worker lee el CSV localmente y calcula su partición en paralelo.
            // Requiere que los Workers estén activos antes de arrancar el Data Center.
            List<String> endpoints = Arrays.asList(workersConfig.split(","));
            System.out.printf("C5.2 — Usando V3 con %d workers: %s%n", endpoints.size(), endpoints);
            resultados = calcularConWorkers(endpoints, archivoDatagramas, rutasActivas, nombreRutas);
        } else {
            // V1 streaming: lee línea a línea sin cargar todo en memoria.
            // O(B) donde B = buses activos (~900). Seguro para cualquier volumen.
            System.out.println("C5.2 — Usando V1 (streaming). Configura sitm.workers para usar V3.");
            resultados = calcularConV1(archivoDatagramas, rutasActivas, nombreRutas);
        }

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

    private List<ResultadoVelocidad> calcularConWorkers(List<String> endpoints,
                                                         String archivoDatagramas,
                                                         Set<Integer> rutasActivas,
                                                         Map<Integer, String> nombreRutas) {
        try (Communicator communicator = Util.initialize()) {
            SpeedMaster master = new SpeedMaster(endpoints, archivoDatagramas);
            return master.calcular(rutasActivas, nombreRutas, communicator);
        } catch (Throwable e) {
            System.err.println("C5.2 — V3 falló (" + e.getMessage() + "), cayendo a V1...");
            return calcularConV1(archivoDatagramas, rutasActivas, nombreRutas);
        }
    }

    private List<ResultadoVelocidad> calcularConV1(String archivoDatagramas,
                                                    Set<Integer> rutasActivas,
                                                    Map<Integer, String> nombreRutas) {
        try {
            return new CalculadorVelocidadV1().calcular(archivoDatagramas, rutasActivas, nombreRutas);
        } catch (Throwable e) {
            System.err.println("C5.2 — Error en V1: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public SpeedReport getAverageSpeed(int lineId, int month, int year, Current current) {
        String mes   = String.format("%d-%02d", year, month);
        String clave = lineId + "_" + mes;
        SpeedReport reporte = reportesCacheados.get(clave);
        if (reporte == null) {
            reporte = new SpeedReport();
            reporte.lineId       = lineId;
            reporte.month        = month;
            reporte.year         = year;
            reporte.averageSpeed = 0.0;
        }
        return reporte;
    }

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
