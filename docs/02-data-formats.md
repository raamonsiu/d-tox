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

**Rango (resaltado):** `start` y `end` son dos anclas; `end` no necesita `quote`. El `text` de un resaltado
es el texto exacto entre ambas (`end` exclusivo); si cruza bloques, la parte de cada uno se une con un salto
de línea (`\n`), y los separadores o bloques sin caracteres en el rango no añaden nada.

La página mostrada **no** se persiste, solo se calcula: `página = f(ancla, viewport, tipografía)`.

## 4. Archivo de progreso y notas (por libro)

Ubicación: por defecto, almacenamiento interno de la app, `files/books/<bookId>.json`; con RF-27, `books/<bookId>.json` dentro de la carpeta de datos elegida por el usuario (§11).

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
- El cliente descarga `index.json` y cada `items/<id>.md` solo por HTTPS (también tras redirecciones), con tiempos de espera y tamaño máximo, y solo las lecturas nuevas que encajan en las preferencias (§5 `content`). Un cuerpo cuyo `sha256` no coincide no se cachea y se reintenta en la siguiente sincronización.

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

Archivo `files/library-index.json` (D-34), escrito de forma atómica: `{ "version": 1, "books": [{ "bookId", "title", "author", "file", "lastOpenedAt", "finished", "source" }] }`. Es una caché: si falta, está corrupto o es de una versión más nueva, se reconstruye desde `files/library/<hex>.<ext>` y `files/books/<bookId>.json`.

- `source` (opcional, `null` en los libros añadidos a mano y en índices anteriores): `{ "folder", "document", "missing" }`, el URI de la carpeta vigilada (§8), el URI SAF del documento donde se encontró el libro y si el último escaneo ya no lo encontró. Se pierde al reconstruir el índice y el siguiente escaneo lo recupera.
- Escaneo de carpetas vigiladas (RF-05): al abrir la app (cuando el índice se ha cargado) y con «Buscar libros ahora» en la biblioteca, sin servicio permanente. Recorre cada carpeta y sus subcarpetas; los documentos EPUB/TXT/Markdown cuyo URI no está en ningún `source` se importan como un archivo elegido a mano (copia propia, mismo `bookId` si son los mismos bytes). Si el libro ya estaba en la biblioteca, conserva su entrada y toma el `source` nuevo solo si no tenía o el suyo falta.
- Un libro cuyo documento ya no está en su carpeta se marca `"missing": true` y la biblioteca lo indica en gris («Ya no está en su carpeta»); no se borra nada (ni la copia ni los datos) y se sigue pudiendo leer. Si vuelve a aparecer, se desmarca. Una carpeta que no se puede leer no cambia las marcas de sus libros, ni tampoco quitar una carpeta de la lista.

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

## 10. Importación de datos (zip)

RF-41. Ajustes › Datos › «Importar datos» abre el diálogo del sistema para elegir un archivo (`ACTION_OPEN_DOCUMENT`, `application/zip`) y fusiona en los datos locales un zip de §9, sin perder lo más reciente de este dispositivo. No es sincronización: solo ocurre cuando el usuario lo pide.

**Primero se valida todo, después se escribe.** Se lee el zip entero y, ante cualquier error, no se escribe nada y la app lo indica («El archivo no es una exportación de D-TOX que esta versión pueda importar. No se ha cambiado nada.»):

- `manifest.json` obligatorio, con `format = "dtox-export"` y `version` entero; una `version` mayor que la soportada (o un `books/<bookId>.json` de una versión de §4 más nueva) se rechaza para no perder datos.
- Solo se aceptan los nombres de §9: `manifest.json`, `settings.json`, `books/<bookId>.json` (con un `bookId` válido, §2) y `library/<hex>.<ext>`. Cualquier otro nombre (rutas absolutas, `..`, subcarpetas, barras invertidas, otros archivos) o un nombre repetido invalida el zip, así que nada puede escribirse fuera de su sitio (_zip slip_).
- Límites, contados sobre los bytes descomprimidos que se leen de verdad (no los que declara el zip): 20 000 entradas, 16 MiB por JSON, 512 MiB por libro y 4 GiB en total.
- Se comprueban las sumas CRC del zip; cada JSON debe ser un objeto válido y cada `books/<bookId>.json` debe leerse según §4 y llevar el mismo `bookId` que su nombre.

**Fusión, por libro** (`books/<bookId>.json`, cada archivo con escritura atómica):

- Sin datos locales del libro: se toma el importado tal cual.
- `title`, `author` y `source`: los locales.
- `progress`: el de `updatedAt` más reciente; si empatan, el local; si un lado no tiene, el otro.
- `annotations`: unión por `id`; con el mismo `id`, la de `updatedAt` más reciente; si empatan, la local.
- Campos desconocidos: se conservan los del lado del que viene cada valor (el progreso y cada anotación, con los suyos); los de primer nivel que solo existen en el importado se añaden.
- Limitación: los borrados no se representan. Una anotación borrada aquí que siga en el zip vuelve a aparecer al importarlo.

**Ajustes:** `settings.json` se valida pero **no se importa**. Son preferencias del dispositivo (tema, tipografía, recordatorios) que el usuario ya eligió aquí, las secciones viven en memoria mientras la app está abierta (§5) y los recordatorios dependen de alarmas del sistema; sobrescribirlos sorprendería más que ayudaría.

**Libros** (`library/`, si el zip los trae): los que la biblioteca no lista ya (por `bookId`) se añaden por el mismo camino que una importación normal (copia atómica, `bookId` recalculado del contenido, sin duplicados). Al acabar se actualiza el índice de biblioteca (§7) sin reconstruirlo, para no perder los `source` de las carpetas vigiladas: se añaden los libros nuevos y `finished` y `lastOpenedAt` se refrescan con el progreso fusionado.

Importar el mismo zip otra vez no cambia nada. Si una escritura falla a medias (p. ej. sin espacio), lo ya escrito queda completo y coherente, la app indica «No se pudieron importar los datos.» y basta con repetir la importación.

## 11. Carpeta de datos

RF-27. Ajustes › Datos › «Carpeta de datos» permite guardar los `<bookId>.json` (§4) en una carpeta elegida con `ACTION_OPEN_DOCUMENT_TREE` (p. ej. una sincronizada con Syncthing), dentro de su subcarpeta `books/`, en vez de en `files/books/`. «Volver a la memoria interna» deshace la elección. El resto de archivos (`settings.json`, `library/`, índices) sigue en `files/`.

Archivo `files/data-folder.json`, escrito de forma atómica; aparte de `settings.json` porque el URI lleva el nombre de una carpeta del usuario (como §8):

```json
{
  "version": 1,
  "folder": "content://com.android.externalstorage.documents/tree/primary%3ASync%2FDTOX"
}
```

- `folder`: URI de árbol SAF, o `null` para la memoria interna. Si el archivo falta, está corrupto o no se puede leer, se usa la memoria interna (nunca bloquea la app).
- Mientras la carpeta está en uso, la app conserva su permiso persistente de lectura y escritura: se toma antes de cambiar y se libera la de la carpeta que se deja (o la nueva, si el cambio falla).
- **Cambio de carpeta:** sin lecturas ni escrituras de datos de libros en medio, se copia cada `<bookId>.json` del sitio actual al nuevo salvo que el nuevo tenga uno igual de reciente o más (por fecha de modificación; si alguna fecha se desconoce, se conserva el del destino). No se copian `*.corrupt`, temporales ni otros archivos (p. ej. conflictos de sync). Luego se guarda `data-folder.json` y se empieza a usar el nuevo sitio. Si algo falla, se sigue usando el sitio anterior (las copias ya hechas quedan en el destino). Si el sitio actual no se puede leer (permiso revocado, tarjeta retirada), se cambia sin copiar, para poder volver siempre a la memoria interna. Los archivos del sitio que se deja no se borran.
- **Escritura segura en la carpeta:** SAF no tiene renombrado atómico que sustituya un archivo. Se escribe `<bookId>.json.tmp`, se relee para comprobarlo, se borra el anterior y se renombra la copia. Si la app muere entre el borrado y el renombrado, solo queda la copia comprobada y la siguiente lectura la pone en su sitio (una `.tmp` cortada en la primera escritura de un libro se aparta como corrupta, §4). No hay `fsync`: la durabilidad depende del proveedor. Si el proveedor no permite renombrar, tras comprobar la copia se sobrescribe el archivo en su sitio y se borra la copia; una muerte en medio puede dejarlo incompleto (se aparta como corrupto y se pierde ese progreso).
- Fusión de conflictos de sync: fuera de alcance; cada archivo es completo y gana el más reciente al cambiar de carpeta.

## 12. Exportación de notas a Markdown

RF-26. En «Marcas y notas», «Exportar a Markdown» abre el diálogo «Guardar como» del sistema (`ACTION_CREATE_DOCUMENT`, sin permisos de almacenamiento) con el nombre `<título>-notas.md` (sin los caracteres que rechazan los sistemas de archivos y acortado; `notas.md` si no queda nada) y escribe un archivo UTF-8:

```markdown
# Título

Autor

## Capítulo

> Texto resaltado

Nota del resaltado

> Inicio del texto donde está la nota…

Nota sin resaltado
```

- Orden de lectura (por ancla de inicio, luego fin, fecha de creación e id), agrupado por el capítulo donde está cada anotación ahora (ancla reubicada, §3); un capítulo sin título se llama «Capítulo N».
- Un resaltado es su texto como cita y, si tiene, su nota debajo. Una nota sin resaltado cita hasta 80 caracteres del texto donde apunta, como en la lista. Sin autor, no hay línea de autor.
- **Los marcadores no se exportan:** RF-26 pide notas y resaltados, y un marcador no lleva texto. Si solo hay marcadores (o nada), la acción aparece desactivada; el generador devolvería solo el título y el autor.
- El texto del libro y del usuario se escapa con `\` donde Markdown lo leería como formato (``\`*_[]<|~``, entidades `&…;`, y `#`, `>`, `-`, `+`, `=` o `1.` al empezar línea; en los títulos, también `#`), así se lee como texto plano. Los saltos de línea se normalizan (`\r\n`, `\r`, U+2028…), cada línea se recorta, varias líneas en blanco son un solo cambio de párrafo y un salto simple se conserva como salto duro (`\` al final de línea).
- El documento se genera y se escribe fuera del hilo principal; el resultado se indica con una línea: «Notas exportadas.» o «No se pudieron exportar las notas.».
