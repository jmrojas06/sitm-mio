package SITM;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class Main {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args)) {
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("ArchiveServiceAdapter", "default -h 192.168.131.42 -p 10001");
            ArchiveServiceI servant = new ArchiveServiceI();
            adapter.add(servant, Util.stringToIdentity("ArchiveService"));
            adapter.activate();
            System.out.println("Data Center iniciado en el puerto 10001...");
            communicator.waitForShutdown();
        }
    }
}
