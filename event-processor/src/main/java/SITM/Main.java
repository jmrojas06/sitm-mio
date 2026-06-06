package SITM;

import SITM.datacenter.broker.DatagramReceiverI;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

/**
 * Punto de entrada del Tier 2 — Data Center, nodo N3: Procesador de Tiempo Real.
 *
 * Este proceso levanta el servidor Ice que recibe datagramas de los buses (N1)
 * y distribuye actualizaciones de posición al Portal CCO (N4). Orquesta los
 * componentes internos del Tier 2 sin contener lógica de negocio propia:
 *
 *   La lógica de procesamiento está en SITM.datacenter.broker.DatagramReceiverI
 *   La clasificación de eventos está en SITM.datacenter.classifier.EventClassifier
 *
 * Este Main.java actúa como la capa de configuración e infraestructura del nodo,
 * siguiendo el principio de separación de responsabilidades del patrón Layered.
 */
public class Main {

    // IP del Event Processor (este nodo). Para correr localmente: -Dsitm.host=localhost
    static final String HOST    = System.getProperty("sitm.host",    "10.147.20.72");
    // IP del Data Center (N5). Para correr localmente: -Dsitm.dc.host=localhost
    static final String DC_HOST = System.getProperty("sitm.dc.host", "10.147.20.67");

    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args)) {

            // Intentar conectar con el Data Center (N5). Si no está disponible,
            // el procesador opera en modo solo tiempo real sin archivar datagramas.
            ArchiveServicePrx archiveService = null;
            try {
                ObjectPrx base = communicator.stringToProxy(
                        "ArchiveService:default -h " + DC_HOST + " -p 10001");
                archiveService = ArchiveServicePrx.checkedCast(base);
            } catch (Exception e) {
                System.out.println("Data Center no disponible — operando en modo solo tiempo real.");
            }

            // DatagramReceiverI es el componente central del Tier 2.
            // Agrupa C3.1 (parser), C3.2 (clasificador), C3.3 (publicador) y C3.4 (archivador).
            DatagramReceiverI servant = new DatagramReceiverI(archiveService);

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "DatagramReceiverAdapter", "default -h * -p 10000");
            adapter.add(servant, Util.stringToIdentity("DatagramReceiver"));
            adapter.activate();

            System.out.println("Event Processor (N3) iniciado en puerto 10000. Esperando datagramas...");
            communicator.waitForShutdown();
        }
    }
}
