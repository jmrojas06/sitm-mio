package SITM.datacenter.analytics.v3;

import SITM.SpeedReport;
import SITM.SpeedWorkerPrx;
import SITM.datacenter.analytics.modelo.ResultadoVelocidad;

import com.zeroc.Ice.Communicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Componente C5.4 del diagrama de deployment: Orquestador del patrón Master-Worker (V3).
 * Pertenece al Tier 2 — Data Center, capa de analítica distribuida.
 *
 * El Master implementa el rol de coordinador del patrón de distribución:
 *
 *   1. Divide: reparte el conjunto total de rutas activas en N particiones,
 *              una por cada Worker disponible.
 *   2. Distribuye: envía cada partición a su Worker vía llamada Ice remota.
 *   3. Agrega: recolecta los resultados parciales y los consolida en el
 *              reporte final de velocidades.
 *
 * La llamada a cada Worker se ejecuta en un hilo Java del pool para que los
 * N Workers trabajen en paralelo. Sin esto, las llamadas Ice serían secuenciales
 * y V3 no tendría ventaja sobre V2.
 *
 * Punto de quiebre:
 *   Con datos pequeños (MiniPilot), el overhead de red Ice puede superar el
 *   beneficio de la distribución. Con datagrams4Pilot.csv (9x más datos), la
 *   distribución empieza a compensar porque el cómputo por Worker se reduce
 *   proporcionalmente mientras el overhead de red permanece constante.
 */
public class SpeedMaster {

    private final List<String> endpointsWorkers;
    private final String archivoDatagramas;

    /**
     * @param endpointsWorkers lista de proxies Ice de los Workers, ej:
     *                         ["10.147.19.23:20001", "10.147.19.23:20002"]
     * @param archivoDatagramas ruta al CSV compartida por todos los Workers
     */
    public SpeedMaster(List<String> endpointsWorkers, String archivoDatagramas) {
        this.endpointsWorkers  = endpointsWorkers;
        this.archivoDatagramas = archivoDatagramas;
    }

    /**
     * Ejecuta el cálculo distribuido y retorna los resultados consolidados.
     *
     * @param rutasActivas  conjunto completo de lineIds a distribuir
     * @param nombreRutas   mapa lineId → shortName para el reporte final
     * @param communicator  comunicador Ice activo para crear los proxies Worker
     * @return lista consolidada de velocidades promedio por ruta y mes
     */
    public List<ResultadoVelocidad> calcular(Set<Integer> rutasActivas,
                                              Map<Integer, String> nombreRutas,
                                              Communicator communicator) throws Exception {

        // Conectar con todos los Workers disponibles antes de dividir el trabajo
        List<SpeedWorkerPrx> workers = conectarWorkers(communicator);
        if (workers.isEmpty()) {
            throw new IllegalStateException(
                "No hay Workers disponibles. Inicie al menos un Worker antes de ejecutar el Master.");
        }

        System.out.printf("Master: %d Workers conectados. Dividiendo %d rutas...%n",
                workers.size(), rutasActivas.size());

        // Dividir el conjunto de rutas en N particiones, una por Worker.
        // La división es equitativa: si hay 96 rutas y 3 Workers, cada uno
        // recibe 32 rutas. El último Worker puede recibir el residuo.
        List<List<Integer>> particiones = dividirRutas(new ArrayList<>(rutasActivas), workers.size());

        // Llamar a cada Worker en paralelo usando un pool de hilos Java.
        // Cada hilo hace una llamada Ice síncrona a su Worker asignado.
        // Los Workers calculan simultáneamente en sus propias máquinas/procesos.
        ExecutorService pool = Executors.newFixedThreadPool(workers.size());
        List<Future<SpeedReport[]>> futuros = new ArrayList<>();

        for (int i = 0; i < workers.size(); i++) {
            final SpeedWorkerPrx worker = workers.get(i);
            final int[] lineIds = particiones.get(i).stream().mapToInt(Integer::intValue).toArray();
            final int indice = i + 1;

            Callable<SpeedReport[]> tarea = () -> {
                System.out.printf("Master: enviando %d rutas al Worker %d...%n",
                        lineIds.length, indice);
                SpeedReport[] resultado = worker.processRoutes(lineIds, archivoDatagramas);
                System.out.printf("Master: Worker %d respondió con %d reportes.%n",
                        indice, resultado.length);
                return resultado;
            };

            futuros.add(pool.submit(tarea));
        }

        // Esperar que todos los Workers terminen y consolidar resultados
        List<SpeedReport> todosLosReportes = new ArrayList<>();
        for (Future<SpeedReport[]> futuro : futuros) {
            SpeedReport[] parcial = futuro.get();
            todosLosReportes.addAll(Arrays.asList(parcial));
        }
        pool.shutdown();

        System.out.printf("Master: consolidados %d reportes de %d Workers.%n",
                todosLosReportes.size(), workers.size());

        return convertirResultados(todosLosReportes, nombreRutas);
    }

    /**
     * Divide la lista de rutas en N subconjuntos lo más equilibrados posible.
     *
     * La distribución equitativa garantiza que ningún Worker sea el cuello de
     * botella por tener desproporcionalmente más rutas que los demás.
     */
    private List<List<Integer>> dividirRutas(List<Integer> rutas, int numParticiones) {
        List<List<Integer>> particiones = new ArrayList<>();
        int total = rutas.size();
        int tamano = (int) Math.ceil((double) total / numParticiones);

        for (int i = 0; i < numParticiones; i++) {
            int inicio = i * tamano;
            int fin    = Math.min(inicio + tamano, total);
            if (inicio >= total) break;
            particiones.add(new ArrayList<>(rutas.subList(inicio, fin)));
        }
        return particiones;
    }

    /**
     * Crea los proxies Ice para conectar con cada Worker registrado.
     * Un Worker que no responde se omite con advertencia para no bloquear
     * el proceso completo si un nodo del clúster está caído.
     */
    private List<SpeedWorkerPrx> conectarWorkers(Communicator communicator) {
        List<SpeedWorkerPrx> workers = new ArrayList<>();
        for (String endpoint : endpointsWorkers) {
            try {
                String proxyStr = "SpeedWorker:default -h " + endpoint.split(":")[0]
                        + " -p " + endpoint.split(":")[1];
                SpeedWorkerPrx proxy = SpeedWorkerPrx.checkedCast(
                        communicator.stringToProxy(proxyStr));
                if (proxy != null) {
                    workers.add(proxy);
                    System.out.println("Master: Worker conectado en " + endpoint);
                }
            } catch (Exception e) {
                System.err.println("Master: no se pudo conectar al Worker en " + endpoint
                        + " — " + e.getMessage());
            }
        }
        return workers;
    }

    /**
     * Convierte los SpeedReport Ice a ResultadoVelocidad para el reporte final.
     */
    private List<ResultadoVelocidad> convertirResultados(List<SpeedReport> reportes,
                                                          Map<Integer, String> nombreRutas) {
        List<ResultadoVelocidad> resultados = new ArrayList<>();
        for (SpeedReport r : reportes) {
            String mes       = String.format("%d-%02d", r.year, r.month);
            String shortName = nombreRutas.getOrDefault(r.lineId, "Ruta-" + r.lineId);
            resultados.add(new ResultadoVelocidad(r.lineId, shortName, mes, r.averageSpeed, 0));
        }
        resultados.sort((a, b) -> Integer.compare(a.lineId, b.lineId));
        return resultados;
    }
}
