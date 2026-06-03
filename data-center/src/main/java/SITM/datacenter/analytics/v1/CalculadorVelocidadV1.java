package SITM.datacenter.analytics.v1;

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

/**
 * Versión 1 — Solución Monolítica del Motor de Cálculo de Velocidad (C5.2).
 *
 * Esta versión procesa el archivo de datagramas de forma completamente secuencial,
 * línea a línea, en un único hilo de ejecución. Es el punto de partida obligatorio
 * antes de las versiones concurrente (V2) y distribuida (V3), porque establece
 * el resultado correcto de referencia y el tiempo base para medir la mejora.
 *
 * Algoritmo de cálculo de velocidad:
 *   Para cada bus, se registra su última lectura recibida. Cuando llega una nueva
 *   lectura del mismo bus en la misma ruta, se calcula:
 *
 *     velocidad (km/h) = odómetro (metros) / Δt (segundos) × 3.6
 *
 *   El odómetro en cada datagrama representa la distancia recorrida desde la
 *   transmisión anterior (valor incremental, no acumulativo). Δt es el tiempo
 *   real entre las dos lecturas consecutivas recibidas para ese bus.
 *
 * Criterios de filtrado aplicados:
 *   - odómetro <= 0: lectura de sensor no disponible, se descarta.
 *   - lineId <= 0: bus sin ruta asignada en ese momento, se descarta.
 *   - Δt < 10 segundos: probable retransmisión o reordenamiento de paquetes,
 *     produciría velocidades irreales (ej: 36m/1s = 129 km/h).
 *   - Δt > 300 segundos: gap demasiado largo, el bus pudo haber parado,
 *     reiniciado el viaje o tener GPS intermitente.
 *   - velocidad > 120 km/h: físicamente imposible para un bus urbano, outlier.
 *
 * Uso de memoria: O(B) donde B es el número de buses activos simultáneamente,
 * porque solo se guarda la última lectura por bus. El archivo puede tener
 * millones de líneas sin que la memoria crezca proporcionalmente.
 */
public class CalculadorVelocidadV1 {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Tiempo mínimo entre lecturas para considerar válido el cálculo de velocidad
    private static final int DELTA_T_MIN_SEG = 10;

    // Tiempo máximo: gaps mayores sugieren parada prolongada o falla de GPS
    private static final int DELTA_T_MAX_SEG = 300;

    // Velocidad máxima físicamente posible para un bus urbano en Cali
    private static final double VELOCIDAD_MAX_KMH = 120.0;

    /**
     * Ejecuta el cálculo monolítico y retorna los resultados por ruta y mes.
     *
     * @param archivoDatagramas ruta al CSV de datagramas (datagrams-MiniPilot.csv)
     * @param rutasActivas      conjunto de lineIds válidos (de lines-241-ActiveGT.csv)
     * @param nombreRutas       mapa de lineId → shortName para el reporte legible
     * @return lista de resultados de velocidad, uno por cada par (ruta, mes) con datos
     */
    public List<ResultadoVelocidad> calcular(String archivoDatagramas,
                                              Set<Integer> rutasActivas,
                                              Map<Integer, String> nombreRutas) {
        // Guarda solo la última lectura recibida por cada bus.
        // La clave es el busId porque un bus puede cambiar de ruta durante el día.
        Map<Integer, LecturaAnterior> ultimaLectura = new HashMap<>();

        // Acumula suma de velocidades y conteo por cada par (lineId, mes).
        // La clave es "lineId_mes" (ej: "131_2019-05") para facilitar el agrupamiento.
        Map<String, double[]> acumuladores = new HashMap<>();

        long lineasLeidas = 0;
        long muestrasValidas = 0;
        long lineasDescartadas = 0;

        try (BufferedReader lector = new BufferedReader(new FileReader(archivoDatagramas))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                lineasLeidas++;

                String[] campos = linea.split(",");
                if (campos.length < 12) {
                    lineasDescartadas++;
                    continue;
                }

                // Parsear solo los campos necesarios para el cálculo de velocidad.
                // Los demás campos (stopId, taskId, coordenadas, etc.) no se necesitan aquí.
                int odometro = parsearEntero(campos[3]);
                int lineId   = parsearEntero(campos[7]);
                int busId    = parsearEntero(campos[11]);
                String timestampStr = campos[10].trim();

                // Filtrar lecturas inválidas antes de cualquier cálculo
                if (odometro <= 0 || lineId <= 0 || busId <= 0) {
                    lineasDescartadas++;
                    continue;
                }

                // Solo procesar rutas que aparecen en el archivo de rutas activas del piloto
                if (!rutasActivas.contains(lineId)) {
                    lineasDescartadas++;
                    continue;
                }

                LocalDateTime tiempoActual = parsearFecha(timestampStr);
                if (tiempoActual == null) {
                    lineasDescartadas++;
                    continue;
                }

                // Si existe una lectura anterior para este bus en la misma ruta,
                // calcular la velocidad entre las dos lecturas consecutivas.
                LecturaAnterior anterior = ultimaLectura.get(busId);
                if (anterior != null && anterior.lineId == lineId) {
                    long deltaT = ChronoUnit.SECONDS.between(anterior.timestamp, tiempoActual);

                    if (deltaT >= DELTA_T_MIN_SEG && deltaT <= DELTA_T_MAX_SEG) {
                        // La fórmula convierte metros/segundo a km/h multiplicando por 3.6.
                        // El odómetro representa los metros recorridos en este intervalo.
                        double velocidad = (odometro / (double) deltaT) * 3.6;

                        if (velocidad > 0 && velocidad < VELOCIDAD_MAX_KMH) {
                            String mes   = String.format("%d-%02d",
                                    tiempoActual.getYear(), tiempoActual.getMonthValue());
                            String clave = lineId + "_" + mes;

                            // acumuladores[clave] = [suma, conteo]
                            double[] acc = acumuladores.computeIfAbsent(clave, k -> new double[]{0.0, 0.0});
                            acc[0] += velocidad;
                            acc[1] += 1;
                            muestrasValidas++;
                        }
                    }
                }

                // Actualizar la última lectura conocida para este bus
                ultimaLectura.put(busId, new LecturaAnterior(lineId, odometro, tiempoActual));
            }

        } catch (Exception e) {
            System.err.println("Error leyendo el archivo de datagramas: " + e.getMessage());
        }

        System.out.printf("V1 — Líneas leídas: %,d | Muestras válidas: %,d | Descartadas: %,d%n",
                lineasLeidas, muestrasValidas, lineasDescartadas);

        return construirResultados(acumuladores, nombreRutas);
    }

    /**
     * Convierte el mapa de acumuladores en la lista de resultados finales.
     * Calcula el promedio dividiendo la suma acumulada entre el conteo de muestras.
     */
    private List<ResultadoVelocidad> construirResultados(Map<String, double[]> acumuladores,
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

        // Ordenar por lineId para que el reporte sea legible y consistente
        resultados.sort((a, b) -> Integer.compare(a.lineId, b.lineId));
        return resultados;
    }

    private int parsearEntero(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private LocalDateTime parsearFecha(String valor) {
        try {
            return LocalDateTime.parse(valor, FORMATO_FECHA);
        } catch (Exception e) {
            return null;
        }
    }

    // Clase interna para guardar el estado de la última lectura por bus.
    // Solo necesitamos lineId, odómetro y timestamp — no el datagrama completo.
    private static class LecturaAnterior {
        final int lineId;
        final int odometro;
        final LocalDateTime timestamp;

        LecturaAnterior(int lineId, int odometro, LocalDateTime timestamp) {
            this.lineId    = lineId;
            this.odometro  = odometro;
            this.timestamp = timestamp;
        }
    }
}
