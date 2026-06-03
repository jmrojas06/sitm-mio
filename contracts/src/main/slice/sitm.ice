module SITM {

    struct Location {
        double latitude;
        double longitude;
    };

    // Niveles de severidad definidos según los criterios operacionales del CCO.
    // El enunciado establece que los controladores deben atender primero los eventos
    // que afectan más adversamente el servicio, por eso este enum es el eje central
    // del sistema de priorización.
    // NORMAL    → operación rutinaria, sin intervención requerida.
    // ALERTADO  → anomalía que requiere seguimiento del controlador.
    // CRITICO   → emergencia que exige atención inmediata (avería, accidente, etc.).
    enum EventSeverity { NORMAL, ALERTADO, CRITICO };

    // Datagram representa el paquete de datos crudo tal como llega del computador
    // embebido de cada bus a través del canal GPRS. Las coordenadas vienen como
    // enteros multiplicados por 10 millones para evitar punto flotante en el protocolo.
    struct Datagram {
        int eventType;
        string registerDate;
        int stopId;
        int odometer;
        int latitude;
        int longitude;
        int taskId;
        int lineId;
        int tripId;
        int unknown1;
        string datagramDate;
        int busId;
    };

    // BusUpdate es el objeto enriquecido que viaja del Procesador de Tiempo Real (N3)
    // al Portal Operativo del CCO (N4). A diferencia del Datagram crudo, este objeto
    // ya lleva las coordenadas normalizadas a grados decimales y la severidad clasificada,
    // para que el visualizador pueda reaccionar sin hacer transformaciones adicionales.
    struct BusUpdate {
        int busId;
        Location pos;
        int lineId;
        string timestamp;
        EventSeverity severity;
        int eventType;
    };

    sequence<BusUpdate> BusUpdateSeq;

    // MonitoringSubscriber es la interfaz de callback que implementa el Portal del CCO.
    // El patrón Observador permite que cualquier número de visualizadores se registren
    // y reciban actualizaciones sin que el procesador los conozca en tiempo de compilación.
    interface MonitoringSubscriber {
        void updateLocation(BusUpdate update);
        void updateLocations(BusUpdateSeq updates);
    };

    // DatagramReceiver es el punto de entrada del sistema. Los buses (o el simulador)
    // envían sus datagramas aquí, y los visualizadores se suscriben a través de subscribe().
    interface DatagramReceiver {
        void postDatagram(Datagram data);
        void subscribe(MonitoringSubscriber* sub);
    };

    // ArchiveService usa el modificador "ami" (Asynchronous Method Invocation) para
    // que el Event Processor no se bloquee esperando confirmación del Data Center.
    // Esto garantiza que la latencia de escritura en disco no afecte el tiempo real.
    interface ArchiveService {
        ["ami"] void archiveDatagram(Datagram data);
    };

    struct SpeedReport {
        int lineId;
        int month;
        int year;
        double averageSpeed;
    };

    sequence<SpeedReport> SpeedReportSeq;

    // ReportProvider expone los resultados del Motor de Análisis Histórico (N5)
    // hacia el Portal del CCO y la API ciudadana para consultas de velocidad promedio.
    interface ReportProvider {
        SpeedReport getAverageSpeed(int lineId, int month, int year);
        SpeedReportSeq getMonthlyReports(int year);
    };

    // Secuencia de identificadores de ruta usada en la distribución Master-Worker (V3).
    // El Master divide el conjunto total de rutas activas en subconjuntos y envía
    // cada uno a un Worker distinto para procesamiento paralelo distribuido.
    sequence<int> LineIdSeq;

    // Interfaz del Worker en el patrón Master-Worker distribuido (C5.4 del diagrama).
    // Cada Worker es un proceso Ice independiente que calcula velocidades solo para
    // las rutas que el Master le asignó, sin conocer las demás particiones.
    // Esto permite escalar horizontalmente: más datos → más Workers.
    interface SpeedWorker {
        SpeedReportSeq processRoutes(LineIdSeq lineIds, string csvPath);
    };

};
