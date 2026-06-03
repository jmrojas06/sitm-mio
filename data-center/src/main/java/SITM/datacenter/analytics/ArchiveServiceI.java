package SITM.datacenter.analytics;

import SITM.ArchiveService;
import SITM.Datagram;

import com.zeroc.Ice.Current;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Componente C5.1 del diagrama de deployment: Persistidor Histórico.
 * Pertenece al Tier 2 — Data Center, capa de persistencia de datos.
 *
 * Recibe cada datagrama procesado por el Event Processor (N3) y lo guarda
 * en disco para que el Motor de Cálculo de Velocidad (C5.2) pueda calcular
 * las velocidades promedio por ruta y mes requeridas por el enunciado.
 *
 * La comunicación llega de forma asíncrona (patrón AMI de Ice), lo que garantiza
 * que la latencia de escritura en disco no introduzca retardos en el pipeline
 * de tiempo real del Event Processor.
 *
 * El formato CSV fue elegido porque es compatible con los archivos fuente del
 * piloto (datagrams-MiniPilot.csv), facilitando la inspección y la interoperabilidad
 * con el Motor de Velocidades sin transformaciones adicionales.
 */
public class ArchiveServiceI implements ArchiveService {

    private static final String DIRECTORIO_DATOS = "data";
    private static final String ARCHIVO_HISTORICO = DIRECTORIO_DATOS + "/datagramas-archivados.csv";

    private final PrintWriter escritor;
    private int totalArchivados = 0;

    /**
     * Inicializa el persistidor creando o abriendo el archivo histórico.
     *
     * El modo append=true garantiza que los datos no se pierdan si el Data Center
     * se reinicia durante una sesión de operación. El BufferedWriter agrupa
     * múltiples escrituras en el buffer antes de ir al disco, mejorando el
     * rendimiento cuando llegan muchos datagramas en ráfaga.
     *
     * @throws IOException si el sistema no puede crear o acceder al archivo de persistencia
     */
    public ArchiveServiceI() throws IOException {
        Files.createDirectories(Paths.get(DIRECTORIO_DATOS));
        this.escritor = new PrintWriter(new BufferedWriter(new FileWriter(ARCHIVO_HISTORICO, true)));
        System.out.println("Persistidor histórico listo. Archivo: " + ARCHIVO_HISTORICO);
    }

    /**
     * Persiste un datagrama en el archivo histórico del sistema.
     *
     * El método es synchronized porque el patrón AMI de Ice puede invocar este
     * método desde múltiples hilos simultáneos, uno por cada datagrama en tránsito.
     * La sincronización protege el escritor de escrituras entrelazadas.
     *
     * Las 12 columnas escritas replican exactamente el formato de los CSV fuente
     * del piloto para que los datos archivados sean consumibles por el Motor de
     * Velocidades (C5.2) sin ningún preprocesamiento adicional.
     */
    @Override
    public synchronized void archiveDatagram(Datagram data, Current current) {
        escritor.printf("%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d%n",
                data.eventType,
                data.registerDate,
                data.stopId,
                data.odometer,
                data.latitude,
                data.longitude,
                data.taskId,
                data.lineId,
                data.tripId,
                data.unknown1,
                data.datagramDate,
                data.busId);

        // flush() garantiza escritura inmediata al disco en lugar de esperar
        // a que el buffer se llene, evitando pérdida de datos ante fallos del proceso.
        escritor.flush();

        totalArchivados++;
        if (totalArchivados % 1000 == 0) {
            System.out.println("Data Center: " + totalArchivados + " datagramas archivados.");
        }
    }
}
