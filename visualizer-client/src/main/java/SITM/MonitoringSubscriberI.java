package SITM;

import com.zeroc.Ice.Current;
import java.util.function.Consumer;

public class MonitoringSubscriberI implements MonitoringSubscriber {
    private final Consumer<BusUpdate> onUpdate;

    public MonitoringSubscriberI(Consumer<BusUpdate> onUpdate) {
        this.onUpdate = onUpdate;
    }

    @Override
    public void updateLocation(BusUpdate update, Current current) {
        if (onUpdate != null) {
            onUpdate.accept(update);
        }
    }

    @Override
    public void updateLocations(BusUpdate[] updates, Current current) {
        for (BusUpdate update : updates) {
            updateLocation(update, current);
        }
    }
}
