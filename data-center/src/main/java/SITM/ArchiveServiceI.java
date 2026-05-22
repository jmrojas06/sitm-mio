package SITM;

import com.zeroc.Ice.Current;

public class ArchiveServiceI implements ArchiveService {
    @Override
    public void archiveDatagram(Datagram data, Current current) {
        // En una fase posterior, esto guardaría en una BD o archivo.
        System.out.println("Datagrama archivado: " + data.busId);
    }
}
