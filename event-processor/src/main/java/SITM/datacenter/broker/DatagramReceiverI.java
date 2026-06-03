package SITM.datacenter.broker;

import SITM.ArchiveServicePrx;
import SITM.BusUpdate;
import SITM.Datagram;
import SITM.DatagramReceiver;
import SITM.EventSeverity;
import SITM.Location;
import SITM.MonitoringSubscriberPrx;
import SITM.datacenter.classifier.EventClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zeroc.Ice.Current;

/**
 * Componente central del Tier 2 — Data Center, nodo N3 del diagrama de deployment.
 *
 * Esta clase agrupa los cuatro componentes de procesamiento en tiempo real:
 *
 *   C3.1 Parser de Datagramas: recibe el Datagram crudo y normaliza coordenadas.
 *   C3.2 Clasificador de Eventos: delega en EventClassifier (SITM.datacenter.classifier)
 *        para asignar un nivel de severidad y priorizar la atención en el CCO.
 *   C3.3 Publicador de Posiciones RT: distribuye el BusUpdate enriquecido a todos
 *        los visualizadores usando el patrón Publicador-Suscriptor.
 *   C3.4 Escritor al Almacén Operacional: reenvía al Data Center con AMI de Ice
 *        para no bloquear el procesamiento de tiempo real.
 *
 * El patrón Publicador-Suscriptor desacopla completamente este procesador de los
 * visualizadores: el CCO puede conectar o desconectar pantallas sin reiniciar el sistema.
 */
public class DatagramReceiverI implements DatagramReceiver {

    private final List<MonitoringSubscriberPrx> subscribers = new ArrayList<>();

    // El pool de hilos permite notificar a múltiples visualizadores de forma concurrente.
    // Antes se usaba Thread.sleep(2000) dentro del loop de suscriptores, lo que con
    // N visualizadores causaba un bloqueo de N*2 segundos por cada datagrama recibido.
    // Con el pool, cada notificación se despacha independientemente y en paralelo.
    private final ExecutorService poolNotificaciones = Executors.newFixedThreadPool(10);

    private final ArchiveServicePrx archiveService;

    public DatagramReceiverI(ArchiveServicePrx archiveService) {
        this.archiveService = archiveService;
    }

    /**
     * C3.1: Recibe el datagrama crudo del bus y orquesta su procesamiento completo.
     *
     * Las coordenadas llegan como enteros multiplicados por 10 millones porque el
     * protocolo GPRS del computador embebido no usa punto flotante (ej: 34761183 → 3.4761183°N).
     * La normalización ocurre aquí antes de construir el BusUpdate para que ninguna
     * capa posterior tenga que conocer este detalle del protocolo de transmisión.
     */
    @Override
    public void postDatagram(Datagram data, Current current) {

        Location ubicacion = new Location();
        ubicacion.latitude  = data.latitude  / 10_000_000.0;
        ubicacion.longitude = data.longitude / 10_000_000.0;

        // C3.2: clasificar la severidad antes de publicar para que los visualizadores
        // del CCO reciban el nivel de alerta ya calculado y puedan reaccionar de inmediato.
        EventSeverity severidad = EventClassifier.clasificar(data.eventType);

        // El BusUpdate es el DTO que fluye de este tier hacia el Portal CCO (N4).
        // Lleva solo los datos necesarios para la visualización, sin los campos
        // internos del protocolo GPRS que son irrelevantes para el controlador.
        BusUpdate actualizacion = new BusUpdate();
        actualizacion.busId     = data.busId;
        actualizacion.pos       = ubicacion;
        actualizacion.lineId    = data.lineId;
        actualizacion.timestamp = data.datagramDate;
        actualizacion.severity  = severidad;
        actualizacion.eventType = data.eventType;

        // C3.3: publicar a todos los suscriptores del Portal CCO
        publicarASuscriptores(actualizacion);

        // C3.4: archivar en el Data Center de forma asíncrona.
        // La anotación ["ami"] en el contrato Ice es lo que habilita archiveDatagramAsync().
        // El Event Processor no espera confirmación del disco para continuar.
        if (archiveService != null) {
            try {
                archiveService.archiveDatagramAsync(data);
            } catch (com.zeroc.Ice.LocalException e) {
                System.err.println("Advertencia: no se pudo enviar al Data Center — " + e.getMessage());
            }
        }

        System.out.printf("Bus %-5d | Ruta %-5d | %-8s | Lat: %.6f | Lon: %.6f%n",
                data.busId, data.lineId, severidad, ubicacion.latitude, ubicacion.longitude);
    }

    /**
     * Registra un nuevo visualizador del Portal CCO como suscriptor del flujo de eventos.
     *
     * El patrón Observador permite que múltiples pantallas del CCO se conecten
     * dinámicamente sin modificar ni reiniciar el procesador.
     */
    @Override
    public void subscribe(MonitoringSubscriberPrx sub, Current current) {
        if (sub != null) {
            synchronized (subscribers) {
                subscribers.add(sub);
            }
            System.out.println("Nuevo visualizador CCO suscrito. Total activos: " + subscribers.size());
        }
    }

    /**
     * C3.3: Publica el BusUpdate a todos los suscriptores registrados de forma concurrente.
     *
     * Se copia la lista de suscriptores antes de iterar para liberar el lock durante
     * las llamadas remotas Ice, que pueden tomar un tiempo variable según la red.
     * Si un suscriptor no responde, se elimina para evitar acumulación de proxies muertos.
     */
    private void publicarASuscriptores(BusUpdate actualizacion) {
        List<MonitoringSubscriberPrx> snapshot;
        synchronized (subscribers) {
            snapshot = new ArrayList<>(subscribers);
        }

        for (MonitoringSubscriberPrx suscriptor : snapshot) {
            poolNotificaciones.submit(() -> {
                try {
                    suscriptor.updateLocation(actualizacion);
                } catch (com.zeroc.Ice.LocalException e) {
                    synchronized (subscribers) {
                        subscribers.remove(suscriptor);
                    }
                    System.out.println("Visualizador desconectado y removido de la lista.");
                }
            });
        }
    }
}
