# Guía de Despliegue — SITM-MIO
**Grupo 03 | Juan Felipe Nieto, Daniel Escobar, Pierre Cuevas, Jose Manuel Rojas**

---

## Nodos del sistema

| Nodo | IP | Hostname | Componente | Datos |
|------|----|----------|-----------|-------|
| m67  | 10.147.20.67 | x205m07 | Data Center (N5) | `/home/swarch/datagrams4pilot.csv` |
| m12  | 10.147.20.72 | x205m12 | Event Processor (N3) | — |
| m68  | 10.147.20.68 | x205m08 | Bus Simulator (N1) | `/home/swarch/datagrams4pilot.csv` |
| m61  | 10.147.20.61 | x205m01 | Worker V3 (puerto 20001) | `/home/swarch/datagrams4pilot.csv` |
| m62  | 10.147.20.62 | x205m02 | Worker V3 (puerto 20002) | `/home/swarch/datagrams4pilot.csv` |
| Windows | — | PC desarrollo | Visualizador CCO (N4) | — |

---

## FASE 1 — Compilar JARs (desde Windows, raíz del proyecto)

```powershell
./gradlew shadowJar
```

JARs generados:
- `data-center/build/libs/sitm-data-center.jar`
- `event-processor/build/libs/sitm-event-processor.jar`
- `bus-simulator/build/libs/sitm-bus-simulator.jar`
- `visualizer-client/build/libs/sitm-visualizer.jar`

---

## FASE 2 — Crear directorios en los nodos

```powershell
ssh swarch@10.147.20.67 "mkdir -p /opt/swarch/sitm-mio"
ssh swarch@10.147.20.72 "mkdir -p /opt/swarch/sitm-mio"
ssh swarch@10.147.20.68 "mkdir -p /opt/swarch/sitm-mio"
ssh swarch@10.147.20.61 "mkdir -p /opt/swarch/sitm-mio"
ssh swarch@10.147.20.62 "mkdir -p /opt/swarch/sitm-mio"
```

---

## FASE 3 — Enviar JARs (pscp con contraseña: swarch)

```powershell
# Data Center → m67
pscp -pw swarch data-center\build\libs\sitm-data-center.jar swarch@10.147.20.67:/opt/swarch/sitm-mio/

# Event Processor → m12
pscp -pw swarch event-processor\build\libs\sitm-event-processor.jar swarch@10.147.20.72:/opt/swarch/sitm-mio/

# Bus Simulator → m68
pscp -pw swarch bus-simulator\build\libs\sitm-bus-simulator.jar swarch@10.147.20.68:/opt/swarch/sitm-mio/

# Workers → m61 y m62
pscp -pw swarch data-center\build\libs\sitm-data-center.jar swarch@10.147.20.61:/opt/swarch/sitm-mio/
pscp -pw swarch data-center\build\libs\sitm-data-center.jar swarch@10.147.20.62:/opt/swarch/sitm-mio/
```

---

## FASE 4 — Enviar datos

Los nodos m67, m68, m61 y m62 deben tener el CSV en `/home/swarch/datagrams4pilot.csv`.

Si los archivos llegan como ZIP, descomprimir y renombrar en cada nodo:
```powershell
plink -pw swarch swarch@10.147.20.67 "cd /home/swarch && unzip datagrams4Pilot.zip && mv datagrams4Pilot.csv datagrams4pilot.csv"
plink -pw swarch swarch@10.147.20.68 "cd /home/swarch && unzip datagrams4Pilot.zip && mv datagrams4Pilot.csv datagrams4pilot.csv"
plink -pw swarch swarch@10.147.20.61 "cd /home/swarch && unzip datagrams4Pilot.zip && mv datagrams4Pilot.csv datagrams4pilot.csv"
plink -pw swarch swarch@10.147.20.62 "cd /home/swarch && unzip datagrams4Pilot.zip && mv datagrams4Pilot.csv datagrams4pilot.csv"
```

---

## FASE 5 — Limpiar puertos ocupados (si hubo ejecución previa)

```powershell
plink -pw swarch swarch@10.147.20.67 "fuser -k 10001/tcp 2>/dev/null; echo ok"
plink -pw swarch swarch@10.147.20.72 "fuser -k 10000/tcp 2>/dev/null; echo ok"
plink -pw swarch swarch@10.147.20.61 "fuser -k 20001/tcp 2>/dev/null; echo ok"
plink -pw swarch swarch@10.147.20.62 "fuser -k 20002/tcp 2>/dev/null; echo ok"
```

---

## FASE 6 — Arrancar en orden (una terminal por comando)

### Terminal 1 — Data Center en m67
```powershell
plink -pw swarch swarch@10.147.20.67 "java -Dsitm.data=/home/swarch -jar /opt/swarch/sitm-mio/sitm-data-center.jar"
```
Esperar: `Data Center (N5) listo en puerto 10001.`

### Terminal 2 — Event Processor en m12
```powershell
plink -pw swarch swarch@10.147.20.72 "java -jar /opt/swarch/sitm-mio/sitm-event-processor.jar"
```
Esperar: `Event Processor (N3) iniciado en puerto 10000.`

### Terminal 3 — Visualizador en Windows
```powershell
java -jar visualizer-client\build\libs\sitm-visualizer.jar
```
Esperar: mapa abierto con buses en tiempo real.

### Terminal 4 — Bus Simulator en m68
```powershell
plink -pw swarch swarch@10.147.20.68 "java -Dsitm.data=/home/swarch -jar /opt/swarch/sitm-mio/sitm-bus-simulator.jar"
```
Esperar: `Simulador: 100 datagramas enviados.`

### Terminal 5 — Worker V3 en m61
```powershell
plink -pw swarch swarch@10.147.20.61 "java -Dsitm.data=/home/swarch -jar /opt/swarch/sitm-mio/sitm-data-center.jar worker 20001"
```
Esperar: `Worker (V3) activo en puerto 20001.`

### Terminal 6 — Worker V3 en m62
```powershell
plink -pw swarch swarch@10.147.20.62 "java -Dsitm.data=/home/swarch -jar /opt/swarch/sitm-mio/sitm-data-center.jar worker 20002"
```
Esperar: `Worker (V3) activo en puerto 20002.`

---

## Correr experimento V3 Master-Worker (desde Windows)

Con los workers activos, ejecutar el master para demostrar escalabilidad distribuida:

```powershell
plink -pw swarch swarch@10.147.20.67 "java -Dsitm.data=/home/swarch -jar /opt/swarch/sitm-mio/sitm-data-center.jar master 10.147.20.61:20001 10.147.20.62:20002"
```

Para comparar V1 vs V2 vs V3:
```powershell
plink -pw swarch swarch@10.147.20.67 "java -Dsitm.data=/home/swarch -jar /opt/swarch/sitm-mio/sitm-data-center.jar comparar 10.147.20.61:20001 10.147.20.62:20002"
```

---

## Notas importantes

- **Contraseña SSH**: `swarch` en todos los nodos
- **Java requerido**: Java 11 en los nodos Linux
- **ZeroTier**: debe estar activo en todos los PCs para conectividad
- **Orden de arranque**: Data Center → Event Processor → Visualizador → Bus Simulator → Workers
- **Si un puerto está ocupado**: usar `fuser -k PUERTO/tcp` en el nodo correspondiente
