package SITM.datacenter.analytics.v2;

import SITM.datacenter.analytics.modelo.ResultadoVelocidad;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Versión 2 — Solución Concurrente del Motor de Cálculo de Velocidad (C5.2).
 *
 * Esta versión divide el trabajo de cálculo entre múltiples hilos Java,
 * uno por cada ruta activa del piloto. Cada hilo procesa de forma independiente
 * todas las lecturas de una ruta, calculando su velocidad promedio mensual
 * sin interferir con los cálculos de las demás rutas.
 *
 * La estrategia de paralelización es por RUTA (lineId):
 *   Fase 1 — Agrupación (un solo hilo): leer el CSV completo y agrupar todas
 *             las lecturas de cada bus por su ruta. Esta fase es secuencial
 *             porque la lectura de disco es inherentemente secuencial.
 *   Fase 2 — Cálculo (N hilos en paralelo): para cada ruta, un hilo del pool
 *             calcula los deltas de velocidad entre lecturas consecutivas
 *             del mismo bus dentro de esa ruta.
 *   Fase 3 — Consolidación (un solo hilo): combinar los resultados parciales
 *             de todos los hilos en la lista final de resultados.
 *
 * Por qué paralelizar por ruta y no por bus:
 *   El número de rutas activas (~96) es manejable como unidad de paralelización.
 *   Paralelizar por bus (~900–1000 buses) generaría demasiadas tareas pequeñas
 *   y el overhead del scheduling del ExecutorService superaría el beneficio.
 *
 * Trade-off de memoria respecto a V1:
 *   V1 usa O(B) memoria donde B = número de buses activos (muy eficiente).
 *   V2 usa O(N) memoria donde N = total de lecturas del CSV (~8 millones),
 *   porque debe cargar todos los datos antes de poder paralelizar.
 *   Este es el costo típico de convertir un algoritmo streaming en uno paralelo.
 */
public class CalculadorVelocidadV2 {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int DELTA_T_MIN_SEG  = 10;
    private static final int DELTA_T_MAX_SEG  = 300;
    private static final double VELOCIDAD_MAX_KMH = 120.0;

    // Número de hilos del pool. Idealmente igual a los núcleos disponibles
    // para maximizar la utilización de CPU sin crear contención.
    private final int numHilos;

    public CalculadorVelocidadV2(int numHilos) {
        this.numHilos = numHilos;
    }

    /**
     * Ejecuta el cálculo concurrente y retorna los resultados por ruta y mes.
     *
     * @param archivoDatagramas ruta al CSV de datagramas
     * @param rutasActivas      conjunto de lineIds válidos
     * @param nombreRutas       mapa de lineId → shortName para el reporte
     * @return lista de resultados de velocidad, ordenada por lineId
     */
    public List<ResultadoVelocidad> calcular(String archivoDatagramas,
                                              Set<Integer> rutasActivas,
                                              Map<Integer, String> nombreRutas) throws Exception {
        // Fase 1: Agrupar todas las lecturas por ruta.
        // La estructura es: lineId → Map<busId, List<LecturaBus>>
        // porque para calcular velocidad necesitamos las lecturas ordenadas por bus.
        System.out.println("V2 — Fase 1: Cargando y agrupando lecturas por ruta...");
        Map<Integer, Map<Integer, List<LecturaBus>>> lecturasPorRuta = cargarYAgrupar(
                archivoDatagramas, rutasActivas);

        System.out.printf("V2 — Fase 1 completada. Rutas con datos: %d%n", lecturasPorRuta.size());

        // Fase 2: Calcular velocidades en paralelo, una tarea por ruta.
        System.out.printf("V2 — Fase 2: Calculando velocidades con %d hilos...%n", numHilos);
        ExecutorService pool = Executors.newFixedThreadPool(numHilos);

        // ConcurrentHashMap para que los hilos puedan escribir resultados parciales
        // de forma segura sin necesidad de synchronized explícito.
        ConcurrentHashMap<String, double[]> acumuladores = new ConcurrentHashMap<>();

        List<Future<Void>> tareas = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, List<LecturaBus>>> entrada : lecturasPorRuta.entrySet()) {
            int lineId = entrada.getKey();
            Map<Integer, List<LecturaBus>> lecturasPorBus = entrada.getValue();

            // Cada tarea calcula las velocidades de UNA ruta completa de forma independiente.
            // Las rutas no comparten estado, por eso la paralelización es segura.
            Callable<Void> tarea = () -> {
                calcularVelocidadesPorRuta(lineId, lecturasPorBus, acumuladores);
                return null;
            };
            tareas.add(pool.submit(tarea));
        }

        // Esperar a que todos los hilos terminen antes de consolidar
        for (Future<Void> tarea : tareas) {
            tarea.get();
        }
        pool.shutdown();

        System.out.println("V2 — Fase 2 completada. Consolidando resultados...");

        // Fase 3: Construir la lista de resultados desde los acumuladores
        return construirResultados(acumuladores, nombreRutas);
    }

    /**
     * Fase 1: Lee el CSV completo y organiza las lecturas por ruta y bus.
     *
     * Esta fase es necesariamente secuencial porque la lectura de disco no puede
     * dividirse fácilmente entre hilos sin perder la continuidad de los datagramas.
     * El beneficio de la concurrencia se obtiene en la Fase 2, donde el cálculo
     * de velocidades por ruta sí puede hacerse en paralelo.
     */
    private Map<Integer, Map<Integer, List<LecturaBus>>> cargarYAgrupar(
            String archivoDatagramas, Set<Integer> rutasActivas) throws Exception {

        Map<Integer, Map<Integer, List<LecturaBus>>> resultado = new HashMap<>();

        try (BufferedReader lector = new BufferedReader(new FileReader(archivoDatagramas))) {
            String linea;
            long leidas = 0;
            while ((linea = lector.readLine()) != null) {
                leidas++;
                if (leidas % 1_000_000 == 0) {
                    System.out.println("  Cargando... " + leidas + " líneas leídas.");
                }

                String[] campos = linea.split(",");
                if (campos.length < 12) continue;

                int odometro = parsearEntero(campos[3]);
                int lineId   = parsearEntero(campos[7]);
                int busId    = parsearEntero(campos[11]);
                String tsStr = campos[10].trim();

                if (odometro <= 0 || lineId <= 0 || busId <= 0) continue;
                if (!rutasActivas.contains(lineId)) continue;

                LocalDateTime ts = parsearFecha(tsStr);
                if (ts == null) continue;

                resultado
                    .computeIfAbsent(lineId, k -> new HashMap<>())
                    .computeIfAbsent(busId, k -> new ArrayList<>())
                    .add(new LecturaBus(odometro, ts));
            }
        }

        return resultado;
    }

    /**
     * Fase 2: Calcula las velocidades para todas las lecturas de una ruta.
     *
     * Este método es ejecutado por un hilo del pool. Procesa todos los buses
     * de la ruta de forma secuencial internamente, pero como cada ruta tiene
     * su propia instancia de este método corriendo en un hilo distinto,
     * el trabajo total se distribuye entre los N hilos del pool.
     */
    private void calcularVelocidadesPorRuta(int lineId,
                                             Map<Integer, List<LecturaBus>> lecturasPorBus,
                                             ConcurrentHashMap<String, double[]> acumuladores) {
        for (Map.Entry<Integer, List<LecturaBus>> entrada : lecturasPorBus.entrySet()) {
            List<LecturaBus> lecturas = entrada.getValue();

            // Ordenar las lecturas de este bus por tiempo para garantizar
            // que los deltas se calculen entre lecturas consecutivas reales.
            lecturas.sort((a, b) -> a.timestamp.compareTo(b.timestamp));

            for (int i = 1; i < lecturas.size(); i++) {
                LecturaBus anterior = lecturas.get(i - 1);
                LecturaBus actual   = lecturas.get(i);

                long deltaT = ChronoUnit.SECONDS.between(anterior.timestamp, actual.timestamp);
                if (deltaT < DELTA_T_MIN_SEG || deltaT > DELTA_T_MAX_SEG) continue;

                double velocidad = (actual.odometro / (double) deltaT) * 3.6;
                if (velocidad <= 0 || velocidad >= VELOCIDAD_MAX_KMH) continue;

                String mes   = String.format("%d-%02d",
                        actual.timestamp.getYear(), actual.timestamp.getMonthValue());
                String clave = lineId + "_" + mes;

                // merge() es atómica en ConcurrentHashMap: suma la velocidad al acumulador
                // existente o crea uno nuevo si la clave no existe todavía.
                acumuladores.merge(clave, new double[]{velocidad, 1},
                        (existente, nueva) -> new double[]{existente[0] + nueva[0], existente[1] + nueva[1]});
            }
        }
    }

    private List<ResultadoVelocidad> construirResultados(ConcurrentHashMap<String, double[]> acumuladores,
                                                          Map<Integer, String> nombreRutas) {
        List<ResultadoVelocidad> resultados = new ArrayList<>();
        for (Map.Entry<String, double[]> entrada : acumuladores.entrySet()) {
            String[] partes = entrada.getKey().split("_");
            int lineId = Integer.parseInt(partes[0]);
            String mes = partes[1];
            double[] acc = entrada.getValue();
            double promedio = acc[1] > 0 ? acc[0] / acc[1] : 0.0;
            String shortName = nombreRutas.getOrDefault(lineId, "Ruta-" + lineId);
            resultados.add(new ResultadoVelocidad(lineId, shortName, mes, promedio, (long) acc[1]));
        }
        resultados.sort((a, b) -> Integer.compare(a.lineId, b.lineId));
        return resultados;
    }

    private int parsearEntero(String valor) {
        try { return Integer.parseInt(valor.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private LocalDateTime parsearFecha(String valor) {
        try { return LocalDateTime.parse(valor, FORMATO_FECHA); }
        catch (Exception e) { return null; }
    }

    // Lectura mínima para la fase de agrupación en V2.
    // Solo guarda odómetro y timestamp porque el busId y lineId ya son la clave del mapa.
    private static class LecturaBus {
        final int odometro;
        final LocalDateTime timestamp;

        LecturaBus(int odometro, LocalDateTime timestamp) {
            this.odometro  = odometro;
            this.timestamp = timestamp;
        }
    }
}
