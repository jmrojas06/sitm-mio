package SITM;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args)) {
            // 1. Obtener proxy del Data Center (opcional para el piloto si no está arriba)
            ArchiveServicePrx archiveService = null;
            try {
                ObjectPrx base = communicator.stringToProxy("ArchiveService:default -p -h 192.168.131.42 10001");
                archiveService = ArchiveServicePrx.checkedCast(base);
            } catch (java.lang.Exception e) {
                System.out.println("Data Center no detectado, operando en modo solo tiempo real.");
            }

            // 2. Crear Servant
            DatagramReceiverI servant = new DatagramReceiverI(archiveService);

            // 3. Configurar Adaptador
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("DatagramReceiverAdapter", "default -h 192.168.131.42 -p 10000");
            adapter.add(servant, Util.stringToIdentity("DatagramReceiver"));
            
            // 4. Permitir que los visualizadores se registren (Simplificación para el piloto)
            // En una arquitectura real, habría un servicio de registro separado.
            
            adapter.activate();
            System.out.println("Event Processor iniciado en el puerto 10000...");
            communicator.waitForShutdown();
        }
    }
}
