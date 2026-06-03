package SITM;

import SITM.edge.BusSimulador;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

/**
 * Punto de entrada del Tier 1 — Edge, nodo N1: Computador Embebido del Bus.
 *
 * En el piloto este proceso simula la transmisión de toda la flota leyendo
 * un archivo CSV de datagramas históricos. En producción, cada bus correría
 * su propia instancia conectada a sus sensores físicos reales.
 *
 * La lógica de lectura y envío está encapsulada en SITM.edge.BusSimulador,
 * que representa los componentes C1.1 a C1.4 del diagrama de deployment.
 * Este Main.java solo configura la conexión Ice y delega la simulación.
 */
public class Main {

    // IP del servidor del piloto SITM-MIO (10.147.19.23).
    // Para correr localmente: -Dsitm.host=localhost
    static final String HOST = System.getProperty("sitm.host", "10.147.20.66");

    public static void main(String[] args) {
        String dir = System.getProperty("sitm.data", "/opt/sitm-mio");
        String archivoCsv = args.length > 0 ? args[0] : dir + "/datagrams4Pilot.csv";

        try (Communicator communicator = Util.initialize(args)) {
            ObjectPrx base = communicator.stringToProxy(
                    "DatagramReceiver:default -h " + HOST + " -p 10000");
            DatagramReceiverPrx receptor = DatagramReceiverPrx.checkedCast(base);

            if (receptor == null) {
                System.err.println("No se pudo conectar con el Event Processor. Verifique IP y puerto.");
                return;
            }

            // BusSimulador encapsula toda la lógica del Tier Edge:
            // lectura del CSV, parseo de datagramas y transmisión al Data Center.
            BusSimulador simulador = new BusSimulador(receptor, archivoCsv);
            simulador.iniciarSimulacion();
        }
    }
}
