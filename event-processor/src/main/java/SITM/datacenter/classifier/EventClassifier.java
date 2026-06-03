package SITM.datacenter.classifier;

import SITM.EventSeverity;

/**
 * Componente C3.2 del diagrama de deployment: Clasificador de Eventos.
 * Pertenece al Tier 2 — Data Center, capa de lógica de negocio.
 *
 * Recibe el código numérico de evento (eventType) que transmite el computador
 * embebido de cada bus y lo convierte en un nivel de severidad que el CCO
 * puede usar para priorizar la atención de las eventualidades.
 *
 * El enunciado establece que los controladores deben atender primero los eventos
 * que afectan más adversamente el servicio. Esta clase es la que implementa
 * ese criterio de priorización en el sistema.
 */
public class EventClassifier {

    /**
     * Clasifica un evento según su impacto en la operación del SITM-MIO.
     *
     * @param eventType código numérico del evento transmitido por el bus
     * @return nivel de severidad que determina la prioridad de atención en el CCO
     */
    public static EventSeverity clasificar(int eventType) {
        switch (eventType) {
            case 0:
                // Actualización periódica de posición GPS, el evento más frecuente.
                // Ocurre cada 30 segundos por bus y representa el flujo normal del sistema.
                return EventSeverity.NORMAL;

            case 1:
                // Apertura o cierre de puertas en parada.
                // Confirma el cumplimiento del recorrido sin anomalías.
                return EventSeverity.NORMAL;

            case 2:
                // Desvío de ruta detectado. El bus se alejó del trayecto programado
                // en el PSO. Requiere seguimiento pero no es emergencia inmediata.
                return EventSeverity.ALERTADO;

            case 3:
                // Retraso significativo respecto al Plan de Servicios de Operación.
                // Afecta la frecuencia percibida por los pasajeros.
                return EventSeverity.ALERTADO;

            case 4:
                // Avería mecánica. El bus puede quedar fuera de servicio y debe
                // ser reemplazado para no afectar la cobertura de la ruta.
                return EventSeverity.CRITICO;

            case 5:
                // Ingreso forzado de pasajeros. Compromete la seguridad a bordo
                // y puede escalar a una situación de orden público.
                return EventSeverity.CRITICO;

            case 6:
                // Choque o accidente. Emergencia que requiere atención inmediata
                // del CCO, cuerpos de socorro y posible cierre de corredor vial.
                return EventSeverity.CRITICO;

            default:
                // Evento no catalogado. Se trata con precaución para no ignorar
                // incidentes no previstos en el catálogo actual de eventTypes.
                return EventSeverity.ALERTADO;
        }
    }
}
