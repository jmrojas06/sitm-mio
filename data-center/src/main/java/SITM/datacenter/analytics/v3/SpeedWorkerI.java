package SITM.datacenter.analytics.v3;

import SITM.SpeedReport;
import SITM.SpeedWorker;
import SITM.datacenter.analytics.modelo.ResultadoVelocidad;
import SITM.datacenter.analytics.v1.CalculadorVelocidadV1;

import com.zeroc.Ice.Current;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Componente C5.4 del diagrama de deployment: Worker del patrón Master-Worker (V3).
 * Pertenece al Tier 2 — Data Center, capa de analítica distribuida.
 *
 * El Worker es un proceso Ice independiente que recibe del Master un subconjunto
 * de rutas (lineIds) y calcula la velocidad promedio mensual SOLO para esas rutas.
 * Usa internamente el mismo algoritmo de V1 (monolítica) pero operando sobre
 * una partición del dominio del problema, no sobre el total.
 *
 * El patrón Master-Worker distribuye el trabajo en dos dimensiones:
 *   Horizontal: más Workers → menos rutas por Worker → menor tiempo total.
 *   Vertical: cada Worker usa V1 (puede reemplazarse por V2 para más paralelismo
 *             dentro de cada nodo).
 *
 * Por qué usar Ice para la comunicación:
 *   Ice provee transparencia de red: el Master llama processRoutes() exactamente
 *   igual ya sea que el Worker esté en la misma máquina o en un servidor remoto.
 *   Esto es el núcleo del patrón de distribución pedido en el enunciado.
 */
public class SpeedWorkerI implements SpeedWorker {

    private final Map<Integer, String> nombreRutas;
    private final int puerto;

    /**
     * @param nombreRutas catálogo completo de rutas (lineId → shortName)
     * @param puerto      puerto en que este Worker está escuchando (para logs)
     */
    public SpeedWorkerI(Map<Integer, String> nombreRutas, int puerto) {
        this.nombreRutas = nombreRutas;
        this.puerto = puerto;
    }

    /**
     * Calcula las velocidades promedio para el subconjunto de rutas asignado.
     *
     * El Master llama este método de forma remota vía Ice. El Worker no sabe
     * cuántos otros Workers existen ni qué rutas están calculando los demás.
     * Esta independencia total entre Workers es lo que hace el patrón seguro
     * para ejecución paralela sin sincronización entre procesos.
     *
     * @param lineIds  array de lineIds asignados a este Worker por el Master
     * @param csvPath  ruta al archivo de datagramas (accesible por todos los Workers)
     * @return array de SpeedReport con los resultados de las rutas procesadas
     */
    @Override
    public SpeedReport[] processRoutes(int[] lineIds, String csvPath, Current current) {
        System.out.printf("Worker (puerto %d): recibidas %d rutas para procesar.%n",
                puerto, lineIds.length);

        // Convertir el array de lineIds a Set para el calculador
        Set<Integer> rutasAsignadas = new HashSet<>();
        for (int id : lineIds) {
            rutasAsignadas.add(id);
        }

        // Extraer solo los nombres de las rutas asignadas a este Worker
        Map<Integer, String> nombresLocales = new HashMap<>();
        for (int id : lineIds) {
            nombresLocales.put(id, nombreRutas.getOrDefault(id, "Ruta-" + id));
        }

        // Reutilizar el algoritmo de V1: eficiente en memoria (streaming)
        // y correcto para calcular velocidades sobre el subconjunto asignado.
        CalculadorVelocidadV1 calculador = new CalculadorVelocidadV1();
        List<ResultadoVelocidad> resultados = calculador.calcular(csvPath, rutasAsignadas, nombresLocales);

        // Convertir resultados al tipo SpeedReport definido en el contrato Ice
        SpeedReport[] reportes = new SpeedReport[resultados.size()];
        for (int i = 0; i < resultados.size(); i++) {
            ResultadoVelocidad r = resultados.get(i);
            String[] partes = r.mes.split("-");
            SpeedReport rep = new SpeedReport();
            rep.lineId       = r.lineId;
            rep.year         = Integer.parseInt(partes[0]);
            rep.month        = Integer.parseInt(partes[1]);
            rep.averageSpeed = r.velocidadPromedioKmh;
            reportes[i] = rep;
        }

        System.out.printf("Worker (puerto %d): %d reportes calculados y listos.%n",
                puerto, reportes.length);
        return reportes;
    }
}
