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
- `id`: ULID/UUID para poder fusionar sin colisiones en el futuro (sync por archivos).
- Fechas en ISO-8601 UTC.
- Campos desconocidos se **preservan** al reescribir (compatibilidad hacia delante).
- Escritura atómica: escribir a `*.tmp`, `fsync`, `rename`.
- Guardado del progreso con _debounce_ (p. ej. 2 s tras cambiar de página y al pasar a segundo plano), no en cada cambio.
- `version` entero; se migra al abrir si es menor.

**Por qué JSON por libro y no una línea global `[idlibro, pos, nota]`:** sigue siendo plano y legible, pero es más fácil de fusionar, de borrar por libro y de validar.

## 5. Ajustes de usuario

`files/settings.json` (o DataStore). Incluye tipografía, tema, animación, avisos, categorías, duraciones, horario de silencio. No contiene datos personales.

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

## 7. Caché de índice de biblioteca (derivada)

Tabla local (Room o equivalente): `bookId, title, author, uri, lastOpenedAt, finished`. Se puede borrar y reconstruir leyendo los archivos y los URIs importados.
