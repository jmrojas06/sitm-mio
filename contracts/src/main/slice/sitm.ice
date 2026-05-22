module SITM {

    struct Location {
        double latitude;
        double longitude;
    };

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

    struct BusUpdate {
        int busId;
        Location pos;
        int lineId;
        string timestamp;
    };

    sequence<BusUpdate> BusUpdateSeq;

    interface MonitoringSubscriber {
        void updateLocation(BusUpdate update);
        void updateLocations(BusUpdateSeq updates);
    };

    interface DatagramReceiver {
        void postDatagram(Datagram data);
        void subscribe(MonitoringSubscriber* sub);
    };

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

    interface ReportProvider {
        SpeedReport getAverageSpeed(int lineId, int month, int year);
        SpeedReportSeq getMonthlyReports(int year);
    };

};
