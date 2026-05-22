package SITM;

import java.util.ArrayList;
import java.util.List;

import com.zeroc.Ice.Current;

public class DatagramReceiverI implements DatagramReceiver {
    private List<MonitoringSubscriberPrx> subscribers = new ArrayList<>();
    private ArchiveServicePrx archiveService;

    public DatagramReceiverI(ArchiveServicePrx archiveService) {
        this.archiveService = archiveService;
    }

    public void addSubscriber(MonitoringSubscriberPrx subscriber) {
        synchronized (subscribers) {
            subscribers.add(subscriber);
        }
    }

    @Override
    public void subscribe(MonitoringSubscriberPrx sub, Current current) {
        if (sub != null) {
            addSubscriber(sub);
            System.out.println("Nuevo visualizador suscrito.");
        }
    }

    @Override
    public void postDatagram(Datagram data, Current current) {
        

          if (archiveService != null) {
            try {
                archiveService.archiveDatagramAsync(data);
            } catch (com.zeroc.Ice.LocalException e) {
                System.err.println("Error al archivar datagrama: " + e.getMessage());
            }
        }

        // 1. Normalización de coordenadas
        Location loc = new Location();
        loc.latitude = data.latitude / 10000000.0;
        loc.longitude = data.longitude / 10000000.0;

        // 2. Crear actualización para el visualizador
        BusUpdate update = new BusUpdate();
        update.busId = data.busId;
        update.pos = loc;
        update.lineId = data.lineId;
        update.timestamp = data.datagramDate;
        // 3. Notificar a suscriptores (Pub-Sub)
        notify(update);

        // 4. Enviar a Data Center de forma asíncrona (si está disponible)
      

        System.out.println("Procesado Datagrama - Bus: " + data.busId + " Lat: " + loc.latitude + " Lon: " + loc.longitude);
    }

    public void notify(BusUpdate update){
        synchronized (subscribers) {
            List<MonitoringSubscriberPrx> toRemove = new ArrayList<>();
            for (MonitoringSubscriberPrx sub : subscribers) {
                try {
                    sub.updateLocation(update);
                    Thread.sleep(2000);
                } catch (com.zeroc.Ice.LocalException e) {
                    toRemove.add(sub);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            subscribers.removeAll(toRemove);
        }
    }
}
