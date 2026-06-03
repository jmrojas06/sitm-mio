package SITM.datacenter.portal;

import SITM.BusUpdate;
import SITM.MonitoringSubscriber;

import com.zeroc.Ice.Current;

import java.util.function.Consumer;

/**
 * Componente C4.3 del diagrama de deployment: parte del Renderizador del Mapa.
 * Pertenece al Tier 2 — Data Center, capa de presentación del Portal CCO (N4).
 *
 * Esta clase es el receptor de callbacks Ice en el lado del visualizador.
 * Cuando el Event Processor (N3) llama a updateLocation(), este objeto recibe
 * el BusUpdate y lo pasa al Consumer que fue configurado en Main.java, quien
 * a su vez actualiza el marcador en el mapa Leaflet del navegador embebido.
 *
 * El uso del patrón Consumer permite que Main.java inyecte la lógica de
 * actualización del mapa sin que esta clase tenga que conocer JavaFX o WebEngine,
 * manteniendo la separación entre la capa de comunicación Ice y la de presentación.
 */
public class MonitoringSubscriberI implements MonitoringSubscriber {

    private final Consumer<BusUpdate> onUpdate;

    /**
     * @param onUpdate función que recibe cada BusUpdate y actualiza el mapa.
     *                 Se inyecta desde Main.java usando una lambda que llama
     *                 a webEngine.executeScript() en el hilo de JavaFX.
     */
    public MonitoringSubscriberI(Consumer<BusUpdate> onUpdate) {
        this.onUpdate = onUpdate;
    }

    /**
     * Recibe una actualización de posición individual desde el Event Processor.
     *
     * El BusUpdate ya llega con las coordenadas normalizadas y la severidad
     * clasificada, por lo que este método solo necesita pasarlo al Consumer
     * sin ningún procesamiento adicional.
     */
    @Override
    public void updateLocation(BusUpdate update, Current current) {
        if (onUpdate != null) {
            onUpdate.accept(update);
        }
    }

    /**
     * Recibe un lote de actualizaciones de posición en una sola llamada.
     *
     * Este método permite al Event Processor enviar múltiples posiciones
     * de golpe, reduciendo la latencia de red cuando hay muchos buses activos.
     * Se delega en updateLocation() para reutilizar la misma lógica de actualización.
     */
    @Override
    public void updateLocations(BusUpdate[] updates, Current current) {
        for (BusUpdate update : updates) {
            updateLocation(update, current);
        }
    }
}
