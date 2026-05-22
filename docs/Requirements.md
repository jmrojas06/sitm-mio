# Arquitectura de Software: Modelamiento de Operación del SITM-MIO

El Sistema Integrado de Transporte Masivo de Occidente (SITM-MIO) es el sistema de
transporte masivo de la ciudad de Cali, diseñado para movilizar de manera eficiente a cientos de
miles de usuarios diariamente a través de una red estructurada de rutas y estaciones. Su operación
es supervisada por el Centro de Control de Operación (CCO) de Metrocali, entidad encargada de
coordinar, gestionar y garantizar el cumplimiento del Plan de Servicios de Operación (PSO) por
parte de los concesionarios de los buses, así como de monitorear en tiempo real el funcionamiento
de estos y las condiciones del servicio en toda la ciudad.
El SITM-MIO opera con una infraestructura compuesta por aproximadamente 1000 buses (con
proyección de crecimiento hasta 2500), que atienden cerca de 100 rutas principales y movilizan
alrededor de 450.000 pasajeros diariamente. Cada uno de estos buses está equipado con cerca
de 40 sensores conectados a un computador embebido, el cual se encarga de recolectar
información operativa, agruparlos en un registro denominado datagrama, conteniendo información
clave como la ubicación GPS, el estado de las puertas y otros indicadores relevantes del vehículo.
Este computador transmite los datagramas cada 20 o 30 segundos mediante protocolo GPRS hacia
un centro de datos.
En cuanto a su operación, el sistema del CCO debe ser capaz de gestionar tanto eventos rutinarios
como situaciones excepcionales. Entre estos eventos se encuentran actualizaciones de posición o
estado del bus, mientras que también se deben atender eventos críticos como fallas mecánicas,
accidentes, congestión vial o incidentes de seguridad, algunos de los cuales pueden ser reportados
directamente por el conductor mediante una interfaz especial en el vehículo. Todos estos eventos se
registran en los datagramas, mediante un código para cada tipo de evento. Al Centro de Control de
Operación llegan diariamente entre 2,5 y 3 millones de eventos, lo que implica la necesidad de
contar con mecanismos eficientes de procesamiento, almacenamiento y análisis de grandes
volúmenes de datos, tanto en tiempo real como de forma histórica.
El objetivo principal del sistema es maximizar la confiabilidad y eficiencia en el aseguramiento del
cumplimiento del plan operativo por parte de los buses, y minimizar los costos operativos,
aprovechando la información recolectada para generar valor a partir de los datos recopilados de la
operación, tras años de funcionamiento. Sin embargo, la organización quiere iniciar con un piloto
con los datos de un año, por ejemplo, para brindar un servicio a la comunidad que indique la
velocidad promedio de desplazamiento por ruta, dado que una de las quejas más frecuentes de los
usuarios del SITM-MIO es que es poco confiable en los tiempos de llegada de los buses de una ruta
a los paraderos o estaciones de la misma.

Requerimientos:

1. El sistema debe permitir visualizar cómo se van moviendo y en dónde están todos los buses de la operación del SITM-MIO en tiempo real en el mapa de Cali, a través de los datagramas recibidos, así como las estaciones o paradas de las rutas.

2. El sistema debe permitir estimar la velocidad promedio de todas las rutas por mes, usando los datos históricos de los eventos de posición GPS que han enviado los buses en el año seleccionado para el proyecto piloto

La estructura de la Linea/ Ruta

|LINEID |"PLANVERSIONID |"SHORTNAME |"DESCRIPTION |"PLANVERSIONID |"ACTIVATIONDATE |"CREATIONDATE|
|-------|---------------|-----------|-------------|---------------|----------------|-------------|
131|241|T31|Terminal Paso del Comercio - Universidades|241|2018-05-15 00:00:00.000|2018-05-14 22:5

La estructura del datagrama es:

| Variable| Tipo| Descripción |Rango Min |Rango Max |
| --------|-----|-------------|----------|-----------|
| eventType| Integer| Tipo de Evento|0 |10000|
| registerdate| Date |Fecha y hora en la que se registró el datagram en el log del NetPeerManager| 31-may-18| 31-may-19|
| stopId |Integer| Identificador de la última parada por la que pasó el bus en el IVU| -1| 26840744|
| odometer| Integer| La distancia en metros recorridos por el bus desde la última parada hasta la ubicación |actual| -1 | 121600820|
| latitude| Integer latitud (y) de la posición del bus en el sistema de coordenadas geográfico del mundo|  -1288197850 |1934980197|
| longitude| Integer longitud (x) de la posición del bus en el sistema de coordenadas geográfico del mundo| -133761367| 845963520|
| taskId| Integer| Identificador de la tarea que tiene asignada el bus en el IVU |-1| 98517645|
| lineId| Integer| Identificador de la línea que tiene asignada el bus en el IVU |-1| 4272|
| tripId| Integer| Identificador del viaje que tiene asignado el bus en el IVU |-1 |687964161|
| unknown1| Integer| ? Número que representa el tipo de evento, manteniendo la tipificación registrada en los mensajes. |0| 103|
| datagramDate| Date| Fecha y hora en la que ocurrió el datagram en el bus| 31-MAY-18 12.00.00.000000 AM| 30-MAY-19 11.59.59.000000 PM|
| busId| Identificador| del bus en el sistema IVU| 1 |8502|

los datos de ejemplo estan en la carpeta data/