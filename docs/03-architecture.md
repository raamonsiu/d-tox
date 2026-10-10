# 03 · Arquitectura

## Stack propuesto

| Área | Elección | Motivo |
|---|---|---|
| Lenguaje | Kotlin | Estándar Android |
| UI | Jetpack Compose (+ Material 3 usado de forma mínima, tema propio) | Control total del aspecto; `HorizontalPager` para las páginas |
| Arquitectura | MVVM + flujo unidireccional (StateFlow), capas `ui` / `domain` / `data` | Simple y testeable |
| Concurrencia | Coroutines + Flow | |
| Preferencias | DataStore (o `settings.json`) | |
| Índice de biblioteca | Room (caché reconstruible) | Consultas ordenadas y rápidas |
| Archivos de usuario | JSON con `kotlinx.serialization` | Ver [02-data-formats.md](02-data-formats.md) |
| EPUB | Parser propio: zip + OPF/spine + `jsoup` para XHTML | El toolkit Readium está pensado para su propio renderizador; aquí renderizamos modelo propio |
| PDF | PdfBox-Android (Apache 2.0) para texto; `PdfRenderer` del sistema para fallback visual | Evita MuPDF (AGPL) |
| Red | `HttpURLConnection` / OkHttp; solo descarga del feed | |
| Trabajo en segundo plano | WorkManager | Persistente tras reinicio |
| Avisos | `NotificationManager` + alarmas inexactas / WorkManager | Sin permiso de alarmas exactas |
| DI | Manual (contenedor en `Application`) o Hilt; decidir al inicializar | Proyecto pequeño |
| Tests | JUnit5/JUnit4, Turbine, Compose UI tests | |

Restricción transversal: **sin dependencias propietarias** (Firebase, GMS, analítica) por privacidad y para mantener abierta la puerta a F-Droid si la licencia cambiara.

## Capas y módulos

Un único módulo Gradle `app` al inicio, con paquetes bien separados; extraer módulos solo si hay dolor real.

```
app/
 ├─ ui/            Compose: library, reader, shortreads, settings, theme
 ├─ domain/        Modelos (Book, Chapter, Block, Anchor, Annotation), casos de uso
 ├─ data/
 │   ├─ import/    EpubParser, TextParser, PdfExtractor → Book
 │   ├─ storage/   UserDataStore (JSON por libro), SettingsStore, LibraryIndex (Room)
 │   ├─ feed/      FeedClient, FeedRepository, ShortReadsCache
 │   └─ notify/    ReminderScheduler, NotificationPublisher
 └─ pagination/    Paginator (texto → páginas), PageCache
```

`domain` y `pagination` no dependen de Android salvo para medir texto, que se abstrae tras una interfaz (`TextMeasurer`) para poder testear la paginación en JVM.

## Flujo de importación

1. El usuario elige archivo con `ACTION_OPEN_DOCUMENT` (sin permisos de almacenamiento amplios).
2. Se copia el original a `files/library/` (copia propia, estable) o se conserva el URI con permiso persistente (carpetas vigiladas).
3. Se calcula `bookId` (hash).
4. El parser correspondiente produce `Book` (modelo de documento). Resultado cacheado en `cache/parsed/<bookId>` en formato binario/JSON para abrir rápido.
5. Se registra en el índice de biblioteca.

Carpetas vigiladas (v1): `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`. Se escanean al abrir la app y bajo demanda (sin servicio permanente). Android no permite rutas crudas de forma fiable con scoped storage; SAF es el camino.

### Calidad de extracción de PDF

- Heurísticas: unir líneas cortadas, deshacer guiones de fin de línea, detectar cabeceras/pies repetidos y descartarlos, detectar títulos por tamaño de fuente.
- Si el ratio de texto extraído por página es bajo (probable escaneo) → aviso y fallback a visor `PdfRenderer`.
- No se promete fidelidad; el PDF está marcado como "mejor esfuerzo" en la UI.

## Paginación

Objetivo: dividir los bloques de un capítulo en páginas que caben en el viewport con la tipografía actual, y poder mapear **ancla ↔ página** en ambos sentidos.

1. Entrada: bloques del capítulo, estilo (fuente, tamaño, interlineado, márgenes), tamaño del viewport.
2. Se mide cada bloque con `TextMeasurer`/`StaticLayout`; si un párrafo no cabe, se parte por líneas (la última línea parcial pasa a la página siguiente).
3. Salida: lista de `Page(startAnchor, endAnchor, fragments[])`.
4. Se ejecuta fuera del hilo principal, por capítulo y de forma incremental: primero el capítulo actual y adyacentes; el resto en segundo plano.
5. Caché en memoria (y opcionalmente en disco) con clave `(bookId, chapter, viewportHash, styleHash)`. Cualquier cambio de fuente/tamaño/rotación invalida y repagina; la posición se conserva por ancla.
6. La UI usa `HorizontalPager` por capítulo con páginas cargadas bajo demanda; el cambio de capítulo es transparente.

Indicador de progreso global: `percent` se aproxima por caracteres acumulados, no por número de páginas (estable ante cambios de estilo).

## Resaltados y selección

- Selección de texto en Compose sobre la página renderizada; se traduce a `(chapter, block, offset)` usando el mapa de fragmentos de la página.
- Los resaltados se pintan como subrayado o sombreado muy sutil (ver [04-design.md](04-design.md)).

## Lecturas cortas: cliente

```
WorkManager (periódico, ~1/día, requiere red, bajo consumo)
   └─ FeedClient.fetch(index.json) → comparar con caché
        └─ descargar items nuevos que encajan en las preferencias del usuario
             └─ ShortReadsCache (archivos en files/shortreads/)
ReminderScheduler
   └─ N avisos/día en horas elegidas (alarma inexacta)
        └─ NotificationPublisher: elige un item no leído que cumple categoría/duración
```

- Si no hay red o no hay items nuevos, el aviso usa el pack empaquetado/caché; si no queda nada sin leer, **no se notifica**.
- La notificación es sobria, sin sonido por defecto, sin contador, con un único canal configurable.
- Android 13+: pedir `POST_NOTIFICATIONS` en contexto, tras activar los avisos, no al primer arranque.
- Reprogramación tras reinicio mediante WorkManager (persistente).

## Backend: feed estático, sin servidor

```
repo de contenido (curated sources.yaml + scripts)
   └─ GitHub Actions (cron diario)
        └─ descarga de fuentes libres → limpieza → recorte en lecturas de 5/10/20 min
             └─ genera index.json + items/*.md
                  └─ publica en GitHub Pages (o rama `gh-pages`)
```

Ventajas: coste cero, sin FCM, sin datos de usuario en el servidor, auditable. Detalles en [05-content-sources.md](05-content-sources.md). El repo de contenido puede vivir en el mismo repo (carpeta `content/`) o aparte; ver [07-decisions.md](07-decisions.md).

## Almacenamiento y permisos

| Dato | Lugar | Permiso |
|---|---|---|
| Libros importados | `files/library/` | Ninguno (SAF) |
| Progreso/notas | `files/books/*.json` | Ninguno |
| Caché parseada | `cache/` | Ninguno |
| Lecturas cortas | `files/shortreads/` | Ninguno |
| Red | feed HTTPS | `INTERNET` |
| Avisos | | `POST_NOTIFICATIONS` (Android 13+) |

No se solicita `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `SCHEDULE_EXACT_ALARM` ni ubicación.

## Riesgos técnicos

| Riesgo | Mitigación |
|---|---|
| Paginación lenta en libros grandes | Paginación por capítulo e incremental; caché; medir en dispositivo de gama baja pronto |
| EPUB mal formados | Parser tolerante; set de EPUBs de prueba (Gutenberg, Standard Ebooks, generados por Calibre) |
| PDF con mala extracción | Marcado "mejor esfuerzo", fallback visual, no bloquear el MVP |
| Selección de texto precisa en páginas custom | Prototipo temprano; si es costoso, resaltado por párrafo en v1 y afinar luego |
| Fabricantes que matan trabajos en segundo plano | Avisos inexactos aceptables; documentar la limitación; botón "ver lecturas" siempre disponible |
