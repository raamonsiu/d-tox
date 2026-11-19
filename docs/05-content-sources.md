# 05 · Fuentes de contenido y pipeline

> Aviso: este documento es planificación técnica, no asesoramiento legal. **Cada fuente y cada texto deben verificarse** antes de incluirse en el feed.

## Reglas de inclusión

1. Solo texto en **dominio público** o con licencia que permita redistribuirlo íntegro (CC BY, CC BY-SA…), respetando atribución y condiciones.
2. La **traducción** tiene derechos propios. Un autor clásico en dominio público no implica que su traducción al español lo esté. Se necesita saber _quién_ tradujo y _cuándo_, o que la traducción tenga licencia libre.
3. En España, con carácter general, la obra pasa a dominio público 70 años tras la muerte del autor (80 años para autores fallecidos antes de 1987). Para otros países, comprobar su plazo; Wikisource y Gutenberg ya filtran por su jurisdicción, pero no sustituyen la verificación.
4. Noticias y artículos de prensa con copyright: **no** se copian en el feed. En el MVP quedan fuera.
5. Cada ítem lleva `license`, `source.url`, autor y traductor cuando aplique, visibles en la app.
6. Se respeta `robots.txt` y los términos de uso de cada fuente; descargas con ritmo moderado y un `User-Agent` identificable.

## Categorías del MVP

### Filosofía clásica

Candidatas (verificar traducción y licencia ítem a ítem):

- Séneca: _Cartas a Lucilio_, _De la brevedad de la vida_
- Marco Aurelio: _Meditaciones_ (por libros/secciones)
- Epicteto: _Enquiridión_ (completo, ~10–15 min), _Disertaciones_ (selecciones)
- Epicuro: _Carta a Meneceo_ (completa y corta)
- Montaigne: _Ensayos_ (ensayos individuales)
- Platón: diálogos breves (_Critón_, _Ion_, fragmentos de _Apología_)
- Lucrecio, Cicerón (_De la amistad_, _De la vejez_), Boecio, Schopenhauer (ensayos), Nietzsche (aforismos agrupados)

Fuentes: Wikisource en español, Project Gutenberg en español, dominio público de bibliotecas digitales (Biblioteca Virtual Miguel de Cervantes: revisar condiciones de cada obra).

### Divulgación científica

- Artículos de **Wikipedia en español** (CC BY-SA): artículos destacados/buenos de extensión adecuada, con atribución obligatoria y mantenimiento de la licencia.
- **Wikinews**/**Wikilibros** (CC BY-SA) cuando haya piezas apropiadas.
- Obras divulgativas clásicas en dominio público (p. ej. textos de Darwin, Faraday, Huxley, Ramón y Cajal) con traducción verificada.
- Textos de divulgación con licencia Creative Commons (universidades, institutos, revistas abiertas) **tras revisar caso por caso**, prestando atención a variantes ND (sin derivadas) y NC (no comercial): si el recorte cuenta como obra derivada, no sirven.

## Criterio de calidad de una "lectura completa"

- Tiene sentido por sí sola, con inicio y cierre (ensayo, carta, capítulo autocontenido, artículo).
- Duración en 3 grupos: **5 min** (~900–1.100 palabras), **10 min** (~1.800–2.200), **20 min** (~3.600–4.400), con tolerancia ±20 %.
- Si una obra larga se divide, cada pieza se presenta como parte numerada ("Carta 12, de 124") y es legible de forma independiente.
- Sin boilerplate de la fuente (cabeceras, notas de edición, licencias de Gutenberg en cuerpo; se mueven a metadatos).

## Repositorio y licencia del contenido

Decidido en D-41 ([07-decisions.md](07-decisions.md)).

- **Dónde**: en este repo, carpeta `content/`. Issues, hooks, CI y revisión son los mismos que los de la app, y un cambio de curación y el pack offline que lo empaqueta (RF-30) caben en la misma PR. Licenciar contenido y código por separado no exige otro repo: basta separarlo por carpeta.
- **Licencias**, explicadas en `content/LICENSE.md`:
  - **Textos**: cada uno conserva su licencia y atribución (D-11), recogidas en `license`, `source` y `translator` del ítem. Un texto en dominio público sigue en dominio público: no se le añade licencia. Un texto CC BY o CC BY-SA recortado o limpiado es una adaptación: se indica que se ha modificado y, si es CC BY-SA, se publica con esa misma licencia.
  - **Aportaciones propias** (selección y orden de `sources.yaml`, metadatos, títulos de lectura, etiquetas, `overrides/`): **CC0-1.0**. Una corrección de erratas no convierte un texto PD en una obra con licencia propia, no se suma una atribución a D-TOX a la del texto y la renuncia cubre también el derecho _sui generis_ sobre bases de datos (UE). No es copyleft: se acepta porque los textos ya son libres y el copyleft protege el código (D-16).
  - **Código del pipeline**: GPL-3.0-or-later, como el resto del código (D-16).
- Con el cambio que cree `content/` se añaden `content/LICENSE.md` y una nota en `CONTRIBUTING.md` y en la sección «Licencia» del README: lo que se aporte a `content/` que no sea código se licencia en CC0-1.0.
- **Lenguaje del pipeline**: Kotlin/JVM, en un módulo Gradle `:content-pipeline` (`content/pipeline/`) que no es dependencia de `:app` ni entra en el APK. CI ya instala JDK 21 y tiene tests, ktlint y detekt; los tests del módulo se añaden al job de Android y a `make check`. Reutiliza librerías ya aprobadas: jsoup (HTML de Wikisource), kotlinx.serialization (`index.json`) y commonmark (comprobar que cada `items/*.md` se lee como en la app); descarga con `java.net.http` del JDK.
- Necesitan aprobación del dev (D-22) cuando se creen: el plugin `org.jetbrains.kotlin.jvm` (JetBrains, Apache-2.0, versión `kotlin` del catálogo) y, si `sources.yaml` sigue en YAML, un parser YAML.

## Pipeline

```
content/
 ├─ LICENSE.md          Licencias de la carpeta: textos, aportaciones propias (CC0-1.0), código (GPL)
 ├─ sources.yaml        Lista curada: fuente, obra, licencia, traductor, rango, duración
 ├─ overrides/          Correcciones manuales por ítem (typos, cortes)
 ├─ pipeline/           Módulo Gradle `:content-pipeline` (Kotlin/JVM): fetch → clean → split → render → validate
 └─ out/                index.json + items/*.md   (generado, no versionado; CI lo publica)
```

`out/` se regenera de forma determinista desde `sources.yaml` (URLs fijas) y `overrides/`, así que no se versiona: el historial auditable es el de la curación.

1. **fetch**: descarga la obra desde la URL fija en `sources.yaml` (con caché).
2. **clean**: limpieza específica de la fuente (HTML de Wikisource, texto plano de Gutenberg), normalización de espacios y comillas, eliminación de notas.
3. **split**: segmentación en lecturas por reglas explícitas del `sources.yaml` (por carta, por libro, por sección), calcula `words` y `minutes`.
4. **render**: genera Markdown simple + `index.json` + hashes.
5. **validate**: esquema JSON, licencia presente, duración dentro de rango, enlaces de origen vivos.
6. **publish**: GitHub Actions publica en GitHub Pages.

### Curación vs automatización

El MVP **no** descubre contenido automáticamente. Una persona (el autor) añade entradas a `sources.yaml`; el cron diario solo **dosifica** lo ya curado (marca `publishedAt`) y regenera el feed. Esto evita problemas de calidad y de licencia, y mantiene el feed pequeño y auditable.

La "novedad diaria" se consigue liberando ítems en un calendario editorial precalculado: 1 lectura nueva por categoría y día, acumulativas (D-36); el cliente nunca queda sin material porque el pack inicial y la caché cubren días sin red.

## Esquema de selección en cliente

- El usuario elige categorías y duraciones.
- La app elige el siguiente ítem no leído que cumpla; orden estable (calendario editorial), no ranking personalizado ni perfilado.
- Nunca se repite una lectura marcada como leída.

## Seguimiento de licencias

- `LICENSES.md` en el repo: tabla con cada fuente, licencia, cumplimiento de atribución.
- Pantalla "Acerca de → Lecturas" generada desde el feed.
- Retirada: si una lectura debe retirarse, se elimina del feed y el cliente la quita de la caché en la siguiente sincronización.

## Pendiente de decidir

- Formato de `sources.yaml` y su lectura: YAML con un parser aprobado, o JSON con kotlinx.serialization (#91).
- Cómo fija el cron de #98 `publishedAt` sin versionar `out/`: calcularlo del orden estable de D-36 y una fecha de inicio, o guardarlo en `sources.yaml`.
- Si las PRs que solo tocan `content/` (sin pack ni código) deben subir la versión de la app (D-33) y pasar el job de Android en CI.
- Tamaño del pack inicial (propuesta: 30–60 lecturas, ≈ 1–2 MB de texto).
