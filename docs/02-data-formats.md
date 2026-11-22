# 02 · Formatos de datos

Principio: **los archivos son la fuente de verdad**. Cualquier base de datos local (índice de biblioteca) es una caché reconstruible.

## 1. Modelo de documento interno

Cualquier formato de entrada (EPUB, TXT, MD, PDF) se convierte a este modelo. El lector y el paginador solo conocen este modelo.

```
Book
 ├─ id: bookId            (hash del contenido, ver §2)
 ├─ title, author, language
 └─ chapters: Chapter[]
     ├─ index: Int
     ├─ title: String?
     └─ blocks: Block[]
         ├─ Heading(level 1..3, text)
         ├─ Paragraph(text)
         ├─ Quote(text)
         ├─ ListItem(text, ordered, depth)
         └─ Separator
```

- El texto de un bloque es **texto plano** con, como mucho, marcas de énfasis mínimas (cursiva/negrita) si el lector las soporta. Las imágenes, tablas y CSS se descartan en el MVP.
- Un bloque se identifica por `(chapterIndex, blockIndex)`. Las posiciones dentro del bloque son offsets de carácter (UTF-16 `Char` index sobre el texto normalizado).

## 2. Identidad del libro

`bookId = "sha256:" + hex(sha256(bytes del archivo original))[0..32]`

- Independiente de la ruta y el nombre.
- Dos copias idénticas comparten datos.
- Si el usuario sustituye el archivo por otra edición, el `bookId` cambia; se intenta reubicar por título+autor y las anclas se revalidan con el fragmento (ver §3).

## 3. Ancla de posición

Una posición es independiente de la paginación (que depende de pantalla, fuente y tamaño).

```json
{
  "chapter": 4,
  "block": 12,
  "offset": 87,
  "quote": "ser feliz no es otra cosa que"
}
```

| Campo     | Significado                                                                                           |
| --------- | ----------------------------------------------------------------------------------------------------- |
| `chapter` | Índice de capítulo (0-based)                                                                          |
| `block`   | Índice de bloque en el capítulo                                                                       |
| `offset`  | Carácter dentro del bloque                                                                            |
| `quote`   | Hasta ~40 caracteres del texto que empieza en la posición; sirve para reubicar si el extractor cambia |

**Reubicación:** si al cargar `quote` no coincide con el texto en `(chapter, block, offset)`, se busca en el mismo bloque, luego en el capítulo, y en último caso se cae al inicio del bloque/capítulo.

**Rango (resaltado):** `start` y `end` son dos anclas; `end` no necesita `quote`.

La página mostrada **no** se persiste, solo se calcula: `página = f(ancla, viewport, tipografía)`.

## 4. Archivo de progreso y notas (por libro)

Ubicación (MVP): almacenamiento interno de la app, `files/books/<bookId>.json`. Con RF-27 podrá apuntar a una carpeta elegida por el usuario.

```json
{
  "version": 1,
  "bookId": "sha256:3f2a…",
  "title": "Cartas a Lucilio",
  "author": "Séneca",
  "source": { "name": "cartas-lucilio.epub", "addedAt": "2026-10-10T18:20:00Z" },
  "progress": {
    "anchor": { "chapter": 4, "block": 12, "offset": 87, "quote": "ser feliz no es otra cosa que" },
    "percent": 0.31,
    "updatedAt": "2026-10-11T07:45:12Z",
    "finished": false
  },
  "annotations": [
    {
      "id": "01JAB7Z…",
      "type": "highlight",
      "start": { "chapter": 2, "block": 3, "offset": 10, "quote": "…" },
      "end": { "chapter": 2, "block": 3, "offset": 64 },
      "text": "texto resaltado (copia para exportar)",
      "note": null,
      "createdAt": "2026-10-11T07:50:00Z",
      "updatedAt": "2026-10-11T07:50:00Z"
    },
    {
      "id": "01JAB80…",
      "type": "note",
      "start": { "chapter": 2, "block": 5, "offset": 0, "quote": "…" },
      "end": null,
      "text": null,
      "note": "Relacionar con la carta 12",
      "createdAt": "2026-10-11T08:02:00Z",
      "updatedAt": "2026-10-11T08:02:00Z"
    },
    {
      "id": "01JAB81…",
      "type": "bookmark",
      "start": { "chapter": 6, "block": 0, "offset": 0, "quote": "…" },
      "end": null,
      "text": null,
      "note": null,
      "createdAt": "2026-10-11T08:10:00Z",
      "updatedAt": "2026-10-11T08:10:00Z"
    }
  ]
}
```

Reglas:

- `type ∈ { highlight, note, bookmark }`. Un resaltado puede llevar `note`.
- `end` (sin `quote`, ver §3) y `text` solo en `highlight`, donde son obligatorios; en `note` y `bookmark` valen `null`. `note` es obligatoria en `note` y `null` en `bookmark`; si existe, no está vacía.
- `id`: ULID/UUID para poder fusionar sin colisiones en el futuro (sync por archivos); único en el archivo. La app genera UUID v4 y acepta ambos al leer.
- Una anotación inválida o de `type` desconocido hace corrupto todo el archivo (se aparta como `*.corrupt`, sin borrarlo); un tipo nuevo requiere subir `version`.
- Fechas en ISO-8601 UTC.
- Campos desconocidos se **preservan** al reescribir (compatibilidad hacia delante); dentro de `annotations`, emparejando cada anotación por `id`.
- Escritura atómica: escribir a `*.tmp`, `fsync`, `rename`.
- Guardado del progreso con _debounce_ (p. ej. 2 s tras cambiar de página y al pasar a segundo plano), no en cada cambio.
- `version` entero; se migra al abrir si es menor.

**Por qué JSON por libro y no una línea global `[idlibro, pos, nota]`:** sigue siendo plano y legible, pero es más fácil de fusionar, de borrar por libro y de validar.

## 5. Ajustes de usuario

`files/settings.json` (o DataStore). Incluye tipografía, tema, animación, avisos, categorías, duraciones, horario de silencio. No contiene datos personales.

- Un objeto `{ "version": 1, ... }` con una sección por grupo de ajustes; todas las secciones comparten un único archivo y una única escritura atómica, que conserva las demás secciones y los campos desconocidos. Un valor ausente o inválido toma su valor por defecto.
- `content` (RF-32): `{ "categories": ["filosofia", "ciencia"], "durations": [5, 10, 20] }`, con los valores del feed (§6). Por defecto, todas; siempre queda al menos una categoría y una duración elegidas, y una lista vacía o sin valores conocidos se lee como la de por defecto.
- `reminders` (RF-33/RF-34): `{ "enabled": false, "times": ["19:00"], "quietHours": { "start": "22:00", "end": "08:00" } }`. Horas en 24 h `"HH:mm"`; avisos al día = número de `times` (0–4), sin repetir y ordenadas. El silencio puede cruzar la medianoche (`start` está dentro y `end` no), `start ≠ end` y ninguna hora de `times` cae en él; al leer, las horas repetidas, en silencio o de más se descartan.

## 6. Feed de lecturas cortas

Feed estático servido por HTTPS (p. ej. GitHub Pages). Detalle del pipeline en [05-content-sources.md](05-content-sources.md).

### `index.json`

```json
{
  "version": 1,
  "generatedAt": "2026-10-10T04:00:00Z",
  "language": "es",
  "items": [
    {
      "id": "seneca-cartas-lucilio-001",
      "title": "Sobre el buen uso del tiempo",
      "author": "Séneca",
      "category": "filosofia",
      "tags": ["estoicismo", "tiempo"],
      "minutes": 8,
      "words": 1850,
      "license": "PD",
      "source": { "name": "Wikisource", "url": "https://es.wikisource.org/…" },
      "translator": "…",
      "path": "items/seneca-cartas-lucilio-001.md",
      "sha256": "…",
      "publishedAt": "2026-10-10"
    }
  ]
}
```

- `category ∈ { filosofia, ciencia }` en el MVP; el conjunto es extensible.
- `minutes` se calcula con ~200 palabras/min y se redondea a la duración "bucket" (5/10/20).
- `license` ∈ `PD` (dominio público), `CC-BY`, `CC-BY-SA`, etc.
- `sha256` permite verificar integridad y deduplicar.

### `items/<id>.md`

Markdown simple con _front matter_ opcional y el cuerpo usando solo `#`, `##`, párrafos, citas `>` y listas. Se convierte al modelo de documento (§1) con el mismo pipeline que un libro de una sola capa.

### Caché local (`files/shortreads/`)

Lecturas descargadas y estado de lectura en el dispositivo (D-36: una lectura sigue en la caché hasta leerse o retirarse).

- `items/<id>.md`: cuerpo de cada lectura cacheada no leída, tal como llega del feed. Solo se cachean ids válidos como nombre de archivo (`[A-Za-z0-9][A-Za-z0-9._-]*`).
- `state.json`, escrito de forma atómica:

```json
{
  "version": 1,
  "items": [
    {
      "id": "seneca-cartas-lucilio-001",
      "title": "Sobre el buen uso del tiempo",
      "author": "Séneca",
      "category": "filosofia",
      "tags": ["estoicismo", "tiempo"],
      "minutes": 8,
      "words": 1850,
      "license": "PD",
      "source": { "name": "Wikisource", "url": "https://es.wikisource.org/…" },
      "translator": "…",
      "path": "items/seneca-cartas-lucilio-001.md",
      "sha256": "…",
      "publishedAt": "2026-10-10"
    }
  ],
  "readIds": ["epicteto-enquiridion-001"]
}
```

- `items`: lecturas cacheadas sin leer, en el orden del feed y con la misma forma que en `index.json`. Cada una tiene su cuerpo en `items/<id>.md`.
- `readIds`: ids marcados como leídos. **No se borran nunca**, tampoco cuando la lectura sale del feed, para que una lectura retirada que vuelva no se repita (unos 30 bytes por lectura).
- Marcar como leída: añade el id a `readIds`, quita la lectura de `items` y borra su cuerpo.
- Sincronización (sin red: recibe el `index.json` ya parseado y los cuerpos ya descargados): guarda los cuerpos nuevos de lecturas no leídas (un `sha256` distinto sustituye la versión cacheada), conserva las cacheadas que siguen en el feed y **borra las que ya no están** (retirada, [05-content-sources.md](05-content-sources.md)).
- Orden de escritura: primero los cuerpos nuevos, luego `state.json` y al final se borran los cuerpos que ya no lista. Así `state.json` nunca apunta a un cuerpo que falte; los archivos sobrantes tras un fallo se borran en la siguiente sincronización.
- Un `state.json` corrupto se aparta como `state.json.corrupt` y la caché empieza vacía. Uno de otra versión no se toca y la caché falla con error hasta actualizar la app.

## 7. Caché de índice de biblioteca (derivada)

Archivo `files/library-index.json` (D-34), escrito de forma atómica: `{ "version": 1, "books": [{ "bookId", "title", "author", "file", "lastOpenedAt", "finished" }] }`. Es una caché: si falta, está corrupto o es de una versión más nueva, se reconstruye desde `files/library/<hex>.<ext>` y `files/books/<bookId>.json`.

## 8. Carpetas vigiladas

Archivo `files/watched-folders.json` (RF-05), escrito de forma atómica. Aparte de `settings.json` porque los URI llevan nombres de carpetas del usuario (§5 no contiene datos personales).

```json
{
  "version": 1,
  "folders": ["content://com.android.externalstorage.documents/tree/primary%3ABooks"]
}
```

- `folders`: URI de árbol SAF tal como los devuelve `ACTION_OPEN_DOCUMENT_TREE`, sin repetir y en el orden en que se añadieron. El nombre que se muestra sale del propio URI.
- Una carpeta está en la lista mientras la app conserva su permiso persistente de lectura (`takePersistableUriPermission`): al añadirla se toma el permiso antes de guardarla (si no se puede guardar, se libera); al quitarla se guarda la lista y luego se libera el permiso. Quitarla no toca sus archivos.
- Si falta, está corrupto o no se puede leer, la lista empieza vacía (los ajustes nunca bloquean la app); las entradas que no son texto se descartan. El siguiente cambio reescribe el archivo.

## 9. Exportación de datos (zip)

RF-41. Ajustes › Datos › «Exportar datos» abre el diálogo del sistema para guardar (`ACTION_CREATE_DOCUMENT`, `application/zip`, nombre sugerido `dtox-datos-<fecha>.zip`) y escribe ahí un zip con esta estructura, en este orden:

```
manifest.json
settings.json            (si existe)
books/<bookId>.json      (por nombre)
library/<hex>.<ext>      (por nombre; solo si se elige «Incluir libros»)
```

`manifest.json`:

```json
{
  "format": "dtox-export",
  "version": 1,
  "appVersion": "0.42.0",
  "exportedAt": "2026-11-21T10:00:00Z",
  "includesBooks": false
}
```

- Los archivos se copian byte a byte (son los mismos formatos de §4 y §5); el importador comprueba `format` y `version`, y un cambio de estructura sube `version`.
- Por defecto no se incluyen los libros (pueden pesar mucho y el usuario suele tener el original).
- Nunca se incluyen: temporales de la escritura atómica (`*.tmp`), archivos apartados como corruptos (`*.corrupt`), cachés reconstruibles o descargables (`library-index.json`, §7, y `shortreads/`, §6) ni ningún otro archivo de `files/`. Tampoco `watched-folders.json` (§8): sus permisos SAF no sobreviven a una reinstalación.
- Orden de entradas determinista y fecha de cada entrada = `exportedAt`: los mismos datos exportados en el mismo instante dan el mismo zip.
- El destino lo gestiona el sistema y no admite renombrado atómico: si la exportación falla a medias, el documento puede quedar incompleto y la app lo indica ("No se pudieron exportar los datos.").
