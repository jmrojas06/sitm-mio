package SITM;

import SITM.datacenter.analytics.ArchiveServiceI;
import SITM.datacenter.analytics.ReportProviderI;
import SITM.datacenter.analytics.v3.SpeedMaster;
import SITM.datacenter.analytics.v3.SpeedWorkerI;
import SITM.datacenter.analytics.modelo.ResultadoVelocidad;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Punto de entrada del Tier 2 — Data Center, nodo N5: Motor de Análisis Histórico.
 *
 * Este proceso puede operar en cuatro modos según el argumento recibido:
 *
 *   (sin args)  → modo "archive": levanta ArchiveService (C5.1) y ReportProvider (C5.2).
 *                 Es el modo de producción: persiste datagramas y sirve reportes de velocidad
 *                 al Portal CCO.
 *
 *   worker PORT → levanta un SpeedWorker Ice en el puerto indicado.
 *                 El Worker espera que el Master le asigne rutas para calcular.
 *                 Ejemplo: java SITM.Main worker 20001
 *
 *   master HOST:PORT [HOST:PORT ...] → activa el Master del patrón distribuido (V3).
 *                 Conecta con los Workers en los endpoints indicados, divide las rutas
 *                 y consolida los resultados. Imprime comparativa con V1 y V2.
 *                 Ejemplo: java SITM.Main master " + HOST + ":20001 " + HOST + ":20002
 *
 *   comparar    → ejecuta V1, V2 y V3 (con Workers ya activos) y muestra los tiempos
 *                 para determinar el punto de quiebre entre versiones.
 *
 * Para la sustentación, el flujo típico es:
 *   Terminal 1: java SITM.Main worker 20001
 *   Terminal 2: java SITM.Main worker 20002
 *   Terminal 3: java SITM.Main master " + HOST + ":20001 " + HOST + ":20002
 */
public class Main {

    // IP del servidor del piloto. Para correr localmente: -Dsitm.host=localhost
    static final String HOST               = System.getProperty("sitm.host", "10.147.20.72");
    // Directorio de datos. Para correr localmente: -Dsitm.data=data
    static final String DIR_DATOS          = System.getProperty("sitm.data", "/opt/swarch/sitm-mio");
    static final String ARCHIVO_DATAGRAMAS = DIR_DATOS + "/" + System.getProperty("sitm.datagram", "datagrams4pilot.csv");
    static final String ARCHIVO_RUTAS      = DIR_DATOS + "/lines-241-ActiveGT.csv";

    public static void main(String[] args) throws Exception {
        String modo = args.length > 0 ? args[0].toLowerCase() : "archive";

        // El catálogo de rutas es necesario en todos los modos
        Set<Integer> rutasActivas = new HashSet<>();
        Map<Integer, String> nombreRutas = new HashMap<>();
        try {
            cargarRutas(ARCHIVO_RUTAS, rutasActivas, nombreRutas);
        } catch (IOException e) {
            System.err.println("Advertencia: catálogo de rutas no disponible — " + e.getMessage());
        }

        switch (modo) {
            case "archive":
                modoArchive(args, rutasActivas, nombreRutas);
                break;
            case "worker":
                int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 20001;
                modoWorker(args, nombreRutas, puerto);
                break;
            case "master":
                List<String> endpoints = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                modoMaster(endpoints, rutasActivas, nombreRutas);
                break;
            case "comparar":
                List<String> endpointsComp = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                modoComparar(endpointsComp, rutasActivas, nombreRutas);
                break;
            default:
                System.err.println("Modo no reconocido: " + modo);
                System.err.println("Modos disponibles: archive | worker PUERTO | master HOST:PORT... | comparar HOST:PORT...");
        }
    }

    /**
     * Modo producción: levanta ArchiveService (C5.1) y ReportProvider (C5.2).
     * ArchiveService recibe datagramas del Event Processor de forma asíncrona.
     * ReportProvider pre-calcula velocidades y las sirve al Portal CCO (N4).
     */
    private static void modoArchive(String[] args, Set<Integer> rutasActivas,
                                     Map<Integer, String> nombreRutas) throws IOException {
        try (Communicator communicator = Util.initialize(args)) {
            ArchiveServiceI archiveServant = new ArchiveServiceI();
            ReportProviderI reportServant  = new ReportProviderI(
                    ARCHIVO_DATAGRAMAS, rutasActivas, nombreRutas);

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "DataCenterAdapter", "default -h * -p 10001");
            adapter.add(archiveServant, Util.stringToIdentity("ArchiveService"));
            adapter.add(reportServant,  Util.stringToIdentity("ReportProvider"));
            adapter.activate();

            System.out.println("Data Center (N5) listo en puerto 10001.");
            System.out.println("  ArchiveService  → recibiendo datagramas del Event Processor");
            System.out.println("  ReportProvider  → atendiendo consultas del Portal CCO");
            communicator.waitForShutdown();
        }
    }

    /**
     * Modo Worker del patrón Master-Worker (V3).
     * El Worker espera órdenes del Master: recibe un subconjunto de rutas,
     * calcula velocidades y devuelve los resultados vía Ice.
     */
    private static void modoWorker(String[] args, Map<Integer, String> nombreRutas,
                                    int puerto) {
        try (Communicator communicator = Util.initialize(args)) {
            SpeedWorkerI workerServant = new SpeedWorkerI(nombreRutas, puerto);

            // Cada Worker escucha en su propio puerto para que el Master
            // pueda distribuir trabajo entre múltiples instancias independientes.
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "WorkerAdapter", "default -h * -p " + puerto);
            adapter.add(workerServant, Util.stringToIdentity("SpeedWorker"));
            adapter.activate();

            System.out.println("Worker (V3) activo en puerto " + puerto + ". Esperando trabajo del Master...");
            communicator.waitForShutdown();
        }
    }

    /**
     * Modo Master del patrón Master-Worker (V3).
     * Conecta con todos los Workers, divide las rutas equitativamente,
     * llama a cada Worker en paralelo y consolida los resultados.
     */
    private static void modoMaster(List<String> endpoints, Set<Integer> rutasActivas,
                                    Map<Integer, String> nombreRutas) throws Exception {
        if (endpoints.isEmpty()) {
            System.err.println("Indique al menos un endpoint de Worker. Ejemplo:");
            System.err.println("  java SITM.Main master " + HOST + ":20001 " + HOST + ":20002");
            return;
        }

        System.out.println("=== V3 DISTRIBUIDA — MASTER-WORKER ===");
        System.out.println("Workers registrados: " + endpoints);
        System.out.println("Rutas a distribuir: " + rutasActivas.size());
        System.out.println();

        try (Communicator communicator = Util.initialize()) {
            long inicio = System.currentTimeMillis();

            SpeedMaster master = new SpeedMaster(endpoints, ARCHIVO_DATAGRAMAS);
            List<ResultadoVelocidad> resultados = master.calcular(
                    rutasActivas, nombreRutas, communicator);

            long duracion = System.currentTimeMillis() - inicio;

            System.out.println("\n=== RESULTADOS V3 ===");
            for (ResultadoVelocidad r : resultados) {
                System.out.println(r);
            }
            System.out.printf("%nTotal rutas: %d | Tiempo V3 (%d Workers): %,d ms%n",
                    resultados.size(), endpoints.size(), duracion);
        }
    }

    /**
     * Modo comparación: ejecuta V1, V2 y V3 y muestra los tiempos para
     * determinar el punto de quiebre a partir del cual vale la pena distribuir.
     *
     * La comparación responde directamente a la pregunta del enunciado:
     * "determinar el punto a partir del cual vale la pena distribuir la solución".
     */
    private static void modoComparar(List<String> endpoints, Set<Integer> rutasActivas,
                                      Map<Integer, String> nombreRutas) throws Exception {
        System.out.println("=== COMPARACIÓN V1 vs V2 vs V3 ===");
        System.out.println("Dataset: " + ARCHIVO_DATAGRAMAS);
        System.out.println();

        // V1 — Monolítica
        long t1 = System.currentTimeMillis();
        new SITM.datacenter.analytics.v1.CalculadorVelocidadV1()
                .calcular(ARCHIVO_DATAGRAMAS, rutasActivas, nombreRutas);
        long durV1 = System.currentTimeMillis() - t1;

        // V2 — Concurrente
        int nucleos = Runtime.getRuntime().availableProcessors();
        long t2 = System.currentTimeMillis();
        new SITM.datacenter.analytics.v2.CalculadorVelocidadV2(nucleos)
                .calcular(ARCHIVO_DATAGRAMAS, rutasActivas, nombreRutas);
        long durV2 = System.currentTimeMillis() - t2;

        // V3 — Distribuida (requiere Workers activos)
        long durV3 = -1;
        if (!endpoints.isEmpty()) {
            try (Communicator communicator = Util.initialize()) {
                long t3 = System.currentTimeMillis();
                new SpeedMaster(endpoints, ARCHIVO_DATAGRAMAS)
                        .calcular(rutasActivas, nombreRutas, communicator);
                durV3 = System.currentTimeMillis() - t3;
            }
        }

        System.out.println("\n=== RESULTADO DE LA COMPARACIÓN ===");
        System.out.printf("V1 Monolítica   : %,6d ms  (1 hilo, streaming)%n", durV1);
        System.out.printf("V2 Concurrente  : %,6d ms  (%d hilos, un hilo por ruta)%n", durV2, nucleos);
        if (durV3 >= 0) {
            System.out.printf("V3 Distribuida  : %,6d ms  (%d Workers Ice)%n", durV3, endpoints.size());
        } else {
            System.out.println("V3 Distribuida  : N/A — inicie Workers primero");
        }

        System.out.println();
        System.out.println("Interpretación:");
        if (durV2 < durV1) {
            System.out.printf("  V2 supera a V1 en %.1f%% con este volumen de datos.%n",
                    (durV1 - durV2) * 100.0 / durV1);
        } else {
            System.out.println("  V1 supera a V2 — el overhead de cargar datos en memoria");
            System.out.println("  supera el beneficio de la concurrencia a este volumen.");
            System.out.println("  Pruebe con datagrams4Pilot.csv para encontrar el quiebre.");
        }
        if (durV3 >= 0 && durV3 < durV2) {
            System.out.printf("  V3 supera a V2 en %.1f%% con %d Workers.%n",
                    (durV2 - durV3) * 100.0 / durV2, endpoints.size());
        } else if (durV3 >= 0) {
            System.out.println("  V3 aún no supera a V2 — el overhead de red Ice supera el");
            System.out.println("  beneficio de la distribución a este volumen.");
        }
    }

    static void cargarRutas(String archivoRutas,
                             Set<Integer> rutasActivas,
                             Map<Integer, String> nombreRutas) throws IOException {
        try (BufferedReader lector = new BufferedReader(new FileReader(archivoRutas))) {
            String linea;
            boolean primera = true;
            while ((linea = lector.readLine()) != null) {
                if (primera) { primera = false; continue; }
                String[] campos = linea.split(",");
                if (campos.length < 3) continue;
                try {
                    int lineId       = Integer.parseInt(campos[0].trim().replace("\"", ""));
                    String shortName = campos[2].trim().replace("\"", "");
                    rutasActivas.add(lineId);
                    nombreRutas.put(lineId, shortName);
                } catch (NumberFormatException ignored) { }
            }
        }
    }
}
