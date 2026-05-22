# Plan de Implementación: Sistema SITM-MIO

Este documento detalla las Historias de Usuario (US), Criterios de Aceptación (CA) y el roadmap técnico para la implementación del sistema distribuido SITM-MIO.

## Fase 1: Infraestructura y Contratos (Completado/En Proceso)

### US01: Definición de Comunicación Distribuida
**Como** arquitecto, **quiero** definir los contratos de interfaz en Slice **para que** los diferentes módulos puedan comunicarse de forma tipada y eficiente.

- **Checklist**:
  - [x] Crear `sitm.ice` con las interfaces `DatagramReceiver`, `ArchiveService`, `MonitoringSubscriber` y `ReportProvider`.
  - [x] Configurar `contracts/build.gradle` con el plugin `com.zeroc.gradle.ice-builder`.
  - [x] Validar la generación de código Java en la carpeta `generated`.

## Fase 2: Flujo de Eventos en Tiempo Real (Ingesta)

### US02: Simulación de Buses
**Como** operador de pruebas, **quiero** que el `bus-simulator` lea los archivos CSV y envíe datagramas al procesador **para** simular la operación real de la flota.

- **CA**: El simulador debe ser capaz de procesar al menos 50 datagramas por segundo.
- **Checklist**:
  - [x] Implementar lector de CSV en `bus-simulator`.
  - [x] Implementar cliente Ice que obtenga el proxy de `DatagramReceiver`.
  - [x] Lógica de envío periódico (cada 20-30s por bus simulado).

### US03: Procesamiento y Normalización
**Como** analista de datos, **quiero** que el `event-processor` reciba los datagramas, normalice las coordenadas y las publique **para** que el visualizador las muestre.

- **CA**: La conversión de coordenadas enteras a decimales debe tener precisión de 6 decimales.
- **Checklist**:
  - [x] Implementar `DatagramReceiverI` (Servant) en `event-processor`.
  - [x] Lógica de normalización de coordenadas (Integer -> Double).
  - [x] Implementar el patrón Pub-Sub para notificar a los suscriptores activos del `Visualizer`.

## Fase 3: Persistencia y Análisis Histórico

### US04: Almacenamiento de Datos (Data Warehouse)
**Como** administrador del sistema, **quiero** persistir todos los datagramas en el `data-center` **para** permitir auditorías y análisis de rendimiento.

- **CA**: Todos los datagramas recibidos por el `event-processor` deben ser reenviados al `data-center` de forma asíncrona.
- **Checklist**:
  - [ ] Implementar `ArchiveServiceI` en `data-center`.
  - [ ] Configurar llamadas `AMI` (Asynchronous Method Invocation) desde el procesador al centro de datos.
  - [ ] Lógica de persistencia en archivos o base de datos embebida.

### US05: Reporte de Velocidad Promedio
**Como** gestor de calidad, **quiero** consultar la velocidad promedio mensual por ruta **para** identificar cuellos de botella en la operación.

- **CA**: El cálculo debe usar la diferencia de `odometer` dividida por la diferencia de tiempo entre el primer y último datagrama del viaje.
- **Checklist**:
  - [ ] Implementar lógica de agregación en `data-center`.
  - [ ] Implementar `ReportProviderI` para exponer los resultados vía Ice.

## Fase 4: Visualización y Monitoreo

### US06: Mapa de Monitoreo
**Como** despachador del CCO, **quiero** ver la ubicación de los buses en tiempo real **para** reaccionar ante incidentes.

- **CA**: El visualizador debe actualizar la posición del bus en menos de 2 segundos tras recibir la actualización.
- **Checklist**:
  - [x] Implementar `MonitoringSubscriberI` (Callback) en `visualizer-client`.
  - [x] Integración con biblioteca de mapas (Swing/JavaFX o consola para el piloto).

## Plan de Pruebas Integradas

1. **Prueba de Conectividad**: Iniciar `data-center` y `event-processor`, verificar que el procesador puede registrar el proxy del centro de datos.
2. **Prueba de Carga**: Ejecutar `bus-simulator` con el archivo `lines-241-ActiveGT.csv` completo y verificar la tasa de recepción en el procesador.
3. **Validación de Datos**: Comparar la velocidad promedio calculada por el sistema con un cálculo manual sobre una muestra de 10 viajes.

## Comandos de Ejecución sugeridos

```bash
# Compilar todo
./gradlew build

# Ejecutar componentes (en diferentes terminales)
./gradlew :data-center:run
./gradlew :event-processor:run
./gradlew :visualizer-client:run
./gradlew :bus-simulator:run
```
