# Refinamiento de Requerimientos: Piloto de Monitoreo y Análisis SITM-MIO

## Resumen Ejecutivo
El sistema tiene como propósito modernizar la supervisión del Sistema Integrado de Transporte Masivo de Occidente (SITM-MIO) mediante una plataforma distribuida que permita el monitoreo en tiempo real de la flota de buses y el análisis histórico de su desempeño. El piloto se centrará en la visualización geoespacial de la operación actual y el cálculo de la velocidad promedio por ruta basada en datos históricos de un año, con el fin de mejorar la percepción de confiabilidad del servicio para el usuario final.

## Requerimientos Funcionales Refinados

1. **Monitoreo Geoespacial en Tiempo Real (RF-01)**:
   - El sistema debe procesar datagramas recibidos vía GPRS cada 20-30 segundos y actualizar la posición (latitud/longitud) de hasta 1000 buses concurrentes en un mapa digital de Cali.
   - La latencia máxima permitida desde la recepción del datagrama en el centro de datos hasta su visualización en el cliente de monitoreo debe ser inferior a 2 segundos.
   - El mapa debe mostrar capas informativas con la ubicación de estaciones y paradas vinculadas a las rutas activas.

2. **Análisis Histórico de Velocidad Promedio (RF-02)**:
   - El sistema debe calcular la velocidad promedio de desplazamiento por ruta (\`lineId\`) con una granularidad mensual.
   - El cálculo se basará en la diferencia de distancia (\`odometer\`) y tiempo (\`datagramDate\`) entre registros consecutivos de un mismo viaje (\`tripId\`).
   - El sistema debe ser capaz de procesar un volumen histórico de aproximadamente 1,000 millones de registros (estimado anual de 3M eventos/día) en un tiempo de ejecución eficiente para reportes mensuales.

3. **Gestión de Eventos Operativos (RF-03)**:
   - El sistema debe clasificar y filtrar los datagramas según el \`eventType\` para priorizar alertas críticas (accidentes, fallas mecánicas) sobre actualizaciones de posición rutinarias.

## Restricciones Técnicas

- **Arquitectura**: Basada en ZeroC Ice para la comunicación RPC entre el Centro de Datos y las terminales de monitoreo.
- **Lenguaje de Programación**: Java 11 o superior.
- **Gestión de Dependencias**: Gradle para la automatización de la construcción y gestión de subproyectos.
- **Formato de Datos**: Procesamiento de archivos CSV para la carga inicial de datos históricos y simulación de datagramas en tiempo real.
- **Coordenadas**: Las latitudes y longitudes en los datagramas (ej. 34761183, -764873683) deben ser normalizadas a grados decimales (ej. 3.4761, -76.4873) para su visualización en mapas estándar.

## Entidades de Dominio (Ice Slice)

- **Bus**: Representa el vehículo con su \`busId\` y estado actual.
- **Datagrama**: Objeto de transporte de datos que contiene GPS, odómetro, sellos de tiempo y estado de sensores.
- **Ruta/Línea**: Definición de la trayectoria (\`lineId\`, \`shortName\`, \`description\`) y sus paradas asociadas.
- **Evento**: Notificación específica vinculada a un bus y un instante de tiempo.

## Criterios de Aceptación

1. **CA-01 (Visualización)**: Dado un flujo constante de datagramas simulados, el cliente de mapa debe mostrar el movimiento fluido de los buses sin perder más del 1% de los paquetes de ubicación.
2. **CA-02 (Velocidad)**: El reporte de velocidad promedio mensual por ruta debe coincidir con un cálculo manual de validación sobre una muestra de 1000 datagramas con un margen de error inferior al 0.5%.

## Alcance (Out of Scope para el Piloto)
- Aplicación móvil para usuarios finales (el piloto es para el Centro de Control).
- Integración directa con hardware embebido en buses (se usará simulación basada en los CSV provistos).
- Gestión de nómina o mantenimiento de concesionarios.
