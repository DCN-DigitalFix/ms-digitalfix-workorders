# ms-digitalfix-workorders

CRUD de órdenes de trabajo del sistema DigitalFix. Es el origen de los dos
canales de mensajería del proyecto: publica a RabbitMQ (notificaciones y
despacho, consumidas por `ms-digitalfix-notify`) y produce a Kafka (eventos
de dominio, consumidos por `ms-digitalfix-audit` y `ms-digitalfix-report`).

---

## API

Roles: `Admin`, `Supervisor`, `Cliente`, `Auditor`. Un cliente solo ve sus
propias órdenes (filtradas por `jwt.getSubject()`); el resto ve todas.

| Método | Ruta | Roles | Scope |
|---|---|---|---|
| GET | `/api/v1/workorders` | Admin, Supervisor, Cliente, Auditor | `OT.Read` |
| GET | `/api/v1/workorders/{id}` | Admin, Supervisor, Cliente, Auditor | `OT.Read` |
| POST | `/api/v1/workorders` | Admin, Supervisor, Cliente | `OT.Create` |
| PATCH | `/api/v1/workorders/{id}/estado` | Admin, Supervisor | `OT.Create` |
| DELETE | `/api/v1/workorders/{id}` | Admin | `OT.Create` |
| GET | `/api/health` | público | — |

Estados (`EstadoOT`): `INGRESADA → ASIGNADA → EN_EJECUCION → CERRADA`, con
`ANULADA` alcanzable desde cualquier estado previo a `CERRADA`. Las
transiciones inválidas devuelven 400 (`EstadoInvalidoException`).

---

## Descuento de stock al asignar

`PATCH /api/v1/workorders/{id}/estado` acepta además `codigoRepuesto` y
`cantidadRepuesto` (los dos opcionales: no toda orden consume repuesto). Si
la transición es a `ASIGNADA` y viene un `codigoRepuesto`, `WorkOrderService`
llama de forma síncrona a `ms-digitalfix-catalog`
(`POST /api/v1/catalog/repuestos/{codigo}/descontar`) **antes** de guardar la
orden: el stock es una precondición de la asignación, no una consecuencia —
si no hay stock suficiente, la asignación completa falla con 409 y la orden
queda sin cambios.

`CatalogClient` propaga el token del usuario que llamó (vía `RestTemplate` +
un interceptor, igual que hace el BFF: workorders no tiene identidad propia)
y traduce los códigos de catalog al lenguaje de workorders:

| Respuesta de catalog | Excepción en workorders | HTTP |
|---|---|---|
| 409 (sin stock) | `StockInsuficienteException` | 409 |
| 404 (repuesto inexistente) | `NotFoundException` | 404 |
| sin respuesta (timeout, catalog caído) | `CatalogNoDisponibleException` | 503 |

**Limitación conocida, declarada a propósito:** descontar y guardar son dos
escrituras en dos bases distintas sin una transacción que las abarque. Se
descuenta primero porque el error más raro queda con más rastro: si fallara
el guardado local después de descontar, el inventario queda detectablemente
desajustado; si fuera al revés, una orden podría quedar asignada con el
stock intacto sin que nadie se entere. Resolverlo del todo requiere el
patrón Saga, que excede el alcance de este caso.

Variable de entorno: `CATALOG_URL` (por defecto `http://localhost:8082`).

---

## Kafka: productor de eventos de dominio

Cada vez que se crea una orden o cambia de estado, el servicio publica un
evento en `digitalfix.workorders.events`. Es el único productor de este
tópico; `audit` y `report` lo consumen cada uno con su propio consumer
group, sin quitarse mensajes entre sí.

### Por qué Kafka además de RabbitMQ

RabbitMQ (`WorkOrderEventPublisher`, ya existía) sirve comandos puntuales
dirigidos a un consumidor específico: enviar un correo, generar un
despacho. Kafka (`WorkOrderKafkaPublisher`) sirve el mismo hecho de negocio
como un evento de dominio que cualquier número de servicios puede leer de
forma independiente y releer desde el principio si hace falta — por eso
audit y report consumen el mismo tópico sin saber el uno del otro. Son dos
problemas distintos resueltos con la herramienta que les calza, no una
migración de uno a otro.

### Formato del evento

```json
{
  "eventId": "7f2e...",
  "eventType": "ORDEN_CREADA",
  "occurredAt": "2026-11-22T10:00:00Z",
  "traceId": "a91c...",
  "ordenId": "OT-0001",
  "estadoAnterior": null,
  "estadoNuevo": "INGRESADA",
  "cliente": "Cliente Prueba",
  "tecnicoAsignado": null,
  "actor": "a1b2c3d4-...-subject-del-jwt"
}
```

`eventType` es `ORDEN_CREADA` o `ESTADO_CAMBIADO`. `eventId` y `traceId` se
generan con `UUID.randomUUID()` en cada publicación (no vienen de la orden).
`actor` es el `subject` del JWT de quien hizo la operación, o `"sistema"` si
no hay autenticación en el contexto (por ejemplo, en un test). La clave del
mensaje Kafka es `ordenId`: todos los eventos de una orden caen en la misma
partición y se leen en el orden en que ocurrieron.

### Garantías de entrega

`KafkaProducerConfig` configura el `ProducerFactory` con `acks=all` y
`enable.idempotence=true`: el broker no confirma hasta que todas las
réplicas sincrónicas tienen el mensaje, y un reintento del productor no
duplica el registro en el log de Kafka. `max.block.ms=3000` evita que una
publicación cuelgue la petición HTTP si Kafka no está disponible.

Publicar es **best-effort respecto del CRUD**: si falla, el fallo se
registra en el log (`.whenComplete(...)`) pero nunca se propaga ni revierte
la orden ya guardada en la base de datos. La orden es la fuente de verdad;
el evento es una notificación derivada. Esto es deliberado y asimétrico
respecto de RabbitMQ, donde sí se usan confirmaciones del publicador para
detectar mensajes perdidos.

### Serialización

El valor se serializa con `JsonSerializer` (Jackson 2, paquete
`org.springframework.kafka.support.serializer`) en vez del más nuevo
`JacksonJsonSerializer` (Jackson 3 / `tools.jackson`), para mantener el
mismo `ObjectMapper` (`com.fasterxml.jackson`, con `JavaTimeModule` y
`WRITE_DATES_AS_TIMESTAMPS` deshabilitado) que usa el resto del proyecto.
Está deprecado en Spring Kafka 4 pero sigue siendo la opción consistente
mientras `audit` y `report` tampoco migren.

### Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092,localhost:9093,localhost:9094` | Brokers del clúster |
| `KAFKA_HABILITADA` | `true` | En `false`, el publisher no intenta conectar (útil para trabajar en el CRUD sin Kafka levantado) |

---

## Probarlo

Con el clúster de Kafka levantado (ver `digitalfix-infra`) y Kafka UI en
`http://localhost:8089`:

```
./mvnw spring-boot:run
```

1. `POST /api/v1/workorders` (rol Admin/Supervisor/Cliente, scope `OT.Create`)
   crea una orden.
2. En Kafka UI, el tópico `digitalfix.workorders.events` muestra un mensaje
   nuevo con `eventType: "ORDEN_CREADA"` y clave igual al `id` de la orden.
3. `PATCH /api/v1/workorders/{id}/estado` publica un segundo mensaje con
   `eventType: "ESTADO_CAMBIADO"`, `estadoAnterior` y `estadoNuevo`
   poblados.
4. Con `audit` y `report` corriendo, sus consumer groups (`audit-timeline`,
   `report-metrics`) avanzan de forma independiente sobre el mismo tópico.

---

## Tests

- `WorkOrderKafkaPublisherTest`: con un `KafkaTemplate` mockeado, verifica
  que `publicarCreada`/`publicarCambioEstado` arman el evento correcto y
  que una publicación fallida no lanza excepción hacia el llamador.
- `WorkOrderEventSerializerTest`: confirma que el `ObjectMapper` usado por
  el serializador escribe `occurredAt` como texto ISO-8601, no como arreglo
  `[segundos, nanos]` (el comportamiento por defecto de Jackson sin
  `WRITE_DATES_AS_TIMESTAMPS` deshabilitado).

---

## Pendiente

- Despliegue en AWS: variables de entorno apuntando al broker Kafka por IP
  privada, igual que `audit` y `report`.
- `ddl-auto: update` en vez de migraciones (igual que el resto del
  proyecto).
