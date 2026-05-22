package SITM;

import java.io.BufferedReader;
import java.io.FileReader;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        String csvFile = "data/chunck.csv"; // Archivo por defecto
        if (args.length > 0) {
            csvFile = args[0];
        }

        try (Communicator communicator = Util.initialize(args)) {
            ObjectPrx base = communicator.stringToProxy("DatagramReceiver:default -h 192.168.131.42 -p 10000");
            DatagramReceiverPrx receiver = DatagramReceiverPrx.checkedCast(base);

            if (receiver == null) {
                throw new Error("Invalid proxy");
            }

            System.out.println("Iniciando simulación desde: " + csvFile);
            
            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] data = line.split(",");
                    if (data.length < 12) continue;

                    Datagram d = new Datagram();
                    try {
                        d.eventType = Integer.parseInt(data[0]);
                        d.registerDate = data[1];
                        d.stopId = Integer.parseInt(data[2]);
                        d.odometer = Integer.parseInt(data[3]);
                        d.latitude = Integer.parseInt(data[4]);
                        d.longitude = Integer.parseInt(data[5]);
                        d.taskId = Integer.parseInt(data[6]);
                        d.lineId = Integer.parseInt(data[7]);
                        d.tripId = Integer.parseInt(data[8]);
                        d.unknown1 = (int)Double.parseDouble(data[9]); // Algunos campos pueden tener notación científica
                        d.datagramDate = data[10];
                        d.busId = Integer.parseInt(data[11]);

                        receiver.postDatagram(d);
                        System.out.println("Enviado datagrama del bus: " + d.busId);
                        
                        // Simular retraso de transmisión
                        Thread.sleep(500); 
                    } catch (java.lang.Exception e) {
                        System.err.println("Error procesando línea: " + line + " - " + e.getMessage());
                    }
                }
            } catch (java.lang.Exception e) {
                System.err.println("Error leyendo CSV: " + e.getMessage());
            }
        }
    }
}
