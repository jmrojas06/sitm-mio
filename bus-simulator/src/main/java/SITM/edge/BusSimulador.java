package SITM.edge;

import SITM.Datagram;
import SITM.DatagramReceiverPrx;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Componente del Tier 1 — Edge, nodo N1: Computador Embebido del Bus.
 *
 * En producción, esta lógica correría en el computador embebido de cada bus,
 * leyendo los valores de los ~40 sensores físicos (C1.1 Recolector de Sensores)
 * y empaquetándolos en datagramas (C1.3 Empaquetador y Sincronizador) para
 * transmitirlos por GPRS al Data Center (C1.4 Transmisor GPRS).
 *
 * En el piloto, esta clase simula ese comportamiento leyendo un archivo CSV
 * de datagramas históricos del SITM-MIO, recreando el flujo de transmisión
 * con un intervalo de 500ms entre envíos para no saturar el Event Processor.
 *
 * La separación de esta lógica en el paquete SITM.edge permite identificar
 * claramente en el código qué pertenece al Tier 1 (Edge) del diagrama.
 */
public class BusSimulador {

    private final DatagramReceiverPrx receptor;
    private final String archivoCsv;

    /**
     * @param receptor  proxy Ice al Event Processor (N3) que recibe los datagramas
     * @param archivoCsv ruta al archivo CSV con los datagramas a simular
     */
    public BusSimulador(DatagramReceiverPrx receptor, String archivoCsv) {
        this.receptor = receptor;
        this.archivoCsv = archivoCsv;
    }

    /**
     * Inicia la simulación de transmisión de datagramas.
     *
     * Lee el CSV línea por línea y envía cada datagrama al Event Processor
     * con un intervalo de 500ms, simulando la frecuencia de transmisión GPRS
     * del computador embebido. Los campos con valor -1 en el CSV representan
     * lecturas de sensor no disponibles, que se envían tal cual para que el
     * Event Processor los filtre según sus reglas de validación.
     */
    public void iniciarSimulacion() {
        System.out.println("Iniciando simulación de flota desde: " + archivoCsv);

        try (BufferedReader lector = new BufferedReader(new FileReader(archivoCsv))) {
            String linea;
            int totalEnviados = 0;

            while ((linea = lector.readLine()) != null) {
                String[] campos = linea.split(",");
                if (campos.length < 12) continue;

                try {
                    Datagram d = parsearLinea(campos);
                    receptor.postDatagram(d);
                    totalEnviados++;

                    if (totalEnviados % 100 == 0) {
                        System.out.println("Simulador: " + totalEnviados + " datagramas enviados.");
                    }

                    // Pausa entre datagramas. En el sistema real cada bus transmite
                    // cada 30 segundos. Con 50ms el factor de aceleración es 600x,
                    // lo que hace el movimiento de los buses visible en la demo.
                    Thread.sleep(50);

                } catch (Exception e) {
                    System.err.println("Línea ignorada por formato inválido: " + linea);
                }
            }

            System.out.println("Simulación completada. Total enviados: " + totalEnviados);

        } catch (Exception e) {
            System.err.println("Error leyendo el CSV de simulación: " + e.getMessage());
        }
    }

    /**
     * Convierte una línea CSV en un objeto Datagram listo para transmitir.
     *
     * El campo unknown1 (posición 9) puede venir en notación científica en algunos
     * registros del CSV fuente, por lo que se parsea como Double y luego se convierte
     * a int para ajustarse al tipo definido en el contrato Ice.
     */
    private Datagram parsearLinea(String[] campos) {
        Datagram d = new Datagram();
        d.eventType    = Integer.parseInt(campos[0].trim());
        d.registerDate = campos[1].trim();
        d.stopId       = Integer.parseInt(campos[2].trim());
        d.odometer     = Integer.parseInt(campos[3].trim());
        d.latitude     = Integer.parseInt(campos[4].trim());
        d.longitude    = Integer.parseInt(campos[5].trim());
        d.taskId       = Integer.parseInt(campos[6].trim());
        d.lineId       = Integer.parseInt(campos[7].trim());
        d.tripId       = Integer.parseInt(campos[8].trim());
        d.unknown1     = (int) Double.parseDouble(campos[9].trim());
        d.datagramDate = campos[10].trim();
        d.busId        = Integer.parseInt(campos[11].trim());
        return d;
    }
}
