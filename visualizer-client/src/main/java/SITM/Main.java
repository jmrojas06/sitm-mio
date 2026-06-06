package SITM;

import SITM.datacenter.portal.MonitoringSubscriberI;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Punto de entrada del Tier 2 — Data Center, nodo N4: Portal Operativo del CCO.
 *
 * Conecta dos fuentes de información y las presenta en el mapa:
 *   1. Posiciones en tiempo real — del Event Processor (N3) vía callback Ice.
 *   2. Velocidades históricas    — del Data Center (N5) vía ReportProvider Ice.
 *
 * El latch paginaCargada resuelve la condición de carrera entre la carga del
 * WebEngine (asíncrona) y la ejecución de scripts JavaScript desde los hilos Ice.
 * Sin él, executeScript() falla con "Can't find variable" porque llama funciones
 * JavaScript antes de que el HTML haya terminado de cargarse.
 */
public class Main extends Application {

    // IP del Event Processor (N3). Para correr localmente: -Dsitm.host=localhost
    static final String HOST    = System.getProperty("sitm.host",    "10.147.20.72");
    // IP del Data Center (N5). Para correr localmente: -Dsitm.dc.host=localhost
    static final String DC_HOST = System.getProperty("sitm.dc.host", "10.147.20.67");

    private Communicator communicator;
    private WebEngine webEngine;

    // El latch se libera cuando el WebEngine señala Worker.State.SUCCEEDED,
    // indicando que map.html y todos sus scripts están completamente cargados.
    private final CountDownLatch paginaCargada = new CountDownLatch(1);

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        webEngine = webView.getEngine();

        // Escuchar el ciclo de carga del WebEngine para saber exactamente cuándo
        // el HTML y el JavaScript están listos para recibir llamadas desde Java.
        webEngine.getLoadWorker().stateProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == Worker.State.SUCCEEDED) {
                paginaCargada.countDown();
                System.out.println("Portal CCO: mapa HTML cargado y listo.");
            }
        });

        URL url = getClass().getResource("/map.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        } else {
            System.err.println("No se encontró map.html. Verifique la configuración del build.");
        }

        stage.setTitle("SITM-MIO — Portal de Monitoreo CCO");
        stage.setScene(new Scene(webView, 1280, 820));
        stage.show();

        new Thread(this::inicializarIce).start();
    }

    private void inicializarIce() {
        try {
            communicator = Util.initialize(new String[]{});
            cargarVelocidadesHistoricas();
            suscribirseAlEventProcessor();
        } catch (Exception e) {
            System.err.println("Error en la inicialización Ice del Portal CCO: " + e.getMessage());
        }
    }

    /**
     * Consulta las velocidades promedio al ReportProvider del Data Center (N5)
     * y las carga en el mapa. Espera hasta 15 segundos a que la página HTML esté
     * lista antes de ejecutar cualquier script, y reintenta la conexión Ice hasta
     * 5 veces en caso de que el Data Center aún esté calculando al arrancar.
     */
    private void cargarVelocidadesHistoricas() {
        // Esperar a que el HTML esté completamente cargado antes de intentar
        // ejecutar cualquier función JavaScript en el mapa.
        try {
            boolean cargado = paginaCargada.await(15, TimeUnit.SECONDS);
            if (!cargado) {
                System.err.println("Timeout esperando carga del mapa. Continuando sin velocidades.");
                return;
            }
        } catch (InterruptedException e) {
            return;
        }

        // 30 intentos × 5s = 2.5 min de espera — suficiente para que el data-center
        // termine de calcular velocidades sobre el archivo grande del piloto real.
        int maxIntentos = 30;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                System.out.printf("Portal CCO: conectando con ReportProvider (intento %d/%d)...%n",
                        intento, maxIntentos);

                ObjectPrx base = communicator.stringToProxy(
                        "ReportProvider:default -h " + DC_HOST + " -p 10001");
                ReportProviderPrx reportProvider = ReportProviderPrx.checkedCast(base);

                if (reportProvider == null) {
                    Thread.sleep(3000);
                    continue;
                }

                SpeedReport[] reportes = reportProvider.getMonthlyReports(2019);

                // La página YA está cargada (confirmado por el latch), así que
                // Platform.runLater() encontrará las funciones JavaScript listas.
                Platform.runLater(() -> {
                    for (SpeedReport r : reportes) {
                        String script = String.format(java.util.Locale.US,
                                "registrarVelocidadRuta(%d, %.2f, %d, %d)",
                                r.lineId, r.averageSpeed, r.month, r.year);
                        webEngine.executeScript(script);
                    }
                    System.out.printf("Portal CCO: %d velocidades cargadas en el mapa.%n",
                            reportes.length);
                });
                return;

            } catch (Exception e) {
                System.out.printf("Intento %d fallido: %s%n", intento, e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println("ReportProvider no disponible — el mapa operará sin velocidades históricas.");
    }

    private void suscribirseAlEventProcessor() {
        try {
            ObjectPrx base = communicator.stringToProxy(
                    "DatagramReceiver:default -h " + HOST + " -p 10000");
            DatagramReceiverPrx receiver = DatagramReceiverPrx.checkedCast(base);

            if (receiver == null) {
                System.err.println("Proxy inválido al Event Processor. Verifique IP y puerto.");
                return;
            }

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "VisualizerCallbackAdapter", "default -h *");

            MonitoringSubscriberI servant = new MonitoringSubscriberI(update -> {
                Platform.runLater(() -> {
                    String script = String.format(java.util.Locale.US,
                            "updateBus(%d, %f, %f, %d, '%s', '%s')",
                            update.busId,
                            update.pos.latitude,
                            update.pos.longitude,
                            update.lineId,
                            update.timestamp,
                            update.severity.toString());
                    webEngine.executeScript(script);
                });
            });

            ObjectPrx proxy = adapter.add(servant, new Identity("VisualizerCallback", ""));
            adapter.activate();

            MonitoringSubscriberPrx subPrx = MonitoringSubscriberPrx.uncheckedCast(proxy);
            receiver.subscribe(subPrx);

            System.out.println("Portal CCO (N4) suscrito al Event Processor. Recibiendo posiciones en tiempo real.");
            communicator.waitForShutdown();

        } catch (Exception e) {
            System.err.println("Error conectando al Event Processor: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (communicator != null) {
            communicator.destroy();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
