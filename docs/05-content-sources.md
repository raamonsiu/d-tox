# 05 · Fuentes de contenido y pipeline

> Aviso: este documento es planificación técnica, no asesoramiento legal. **Cada fuente y cada texto deben verificarse** antes de incluirse en el feed.

## Reglas de inclusión

1. Solo texto en **dominio público** o con licencia que permita redistribuirlo íntegro (CC BY, CC BY-SA…), respetando atribución y condiciones.
2. La **traducción** tiene derechos propios. Un autor clásico en dominio público no implica que su traducción al español lo esté. Se necesita saber *quién* tradujo y *cuándo*, o que la traducción tenga licencia libre.
3. En España, con carácter general, la obra pasa a dominio público 70 años tras la muerte del autor (80 años para autores fallecidos antes de 1987). Para otros países, comprobar su plazo; Wikisource y Gutenberg ya filtran por su jurisdicción, pero no sustituyen la verificación.
4. Noticias y artículos de prensa con copyright: **no** se copian en el feed. En el MVP quedan fuera.
5. Cada ítem lleva `license`, `source.url`, autor y traductor cuando aplique, visibles en la app.
6. Se respeta `robots.txt` y los términos de uso de cada fuente; descargas con ritmo moderado y un `User-Agent` identificable.

## Categorías del MVP

### Filosofía clásica

Candidatas (verificar traducción y licencia ítem a ítem):

- Séneca: *Cartas a Lucilio*, *De la brevedad de la vida*
- Marco Aurelio: *Meditaciones* (por libros/secciones)
- Epicteto: *Enquiridión* (completo, ~10–15 min), *Disertaciones* (selecciones)
- Epicuro: *Carta a Meneceo* (completa y corta)
- Montaigne: *Ensayos* (ensayos individuales)
- Platón: diálogos breves (*Critón*, *Ion*, fragmentos de *Apología*)
- Lucrecio, Cicerón (*De la amistad*, *De la vejez*), Boecio, Schopenhauer (ensayos), Nietzsche (aforismos agrupados)

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

## Pipeline

```
content/
 ├─ sources.yaml        Lista curada: fuente, obra, licencia, traductor, rango, duración
 ├─ scripts/            fetch → clean → split → render
 ├─ overrides/          Correcciones manuales por ítem (typos, cortes)
 └─ out/                index.json + items/*.md   (artefacto publicado)
```

1. **fetch**: descarga la obra desde la URL fija en `sources.yaml` (con caché).
2. **clean**: limpieza específica de la fuente (HTML de Wikisource, texto plano de Gutenberg), normalización de espacios y comillas, eliminación de notas.
3. **split**: segmentación en lecturas por reglas explícitas del `sources.yaml` (por carta, por libro, por sección), calcula `words` y `minutes`.
4. **render**: genera Markdown simple + `index.json` + hashes.
5. **validate**: esquema JSON, licencia presente, duración dentro de rango, enlaces de origen vivos.
6. **publish**: GitHub Actions publica en GitHub Pages.

### Curación vs automatización

El MVP **no** descubre contenido automáticamente. Una persona (el autor) añade entradas a `sources.yaml`; el cron diario solo **dosifica** lo ya curado (marca `publishedAt`) y regenera el feed. Esto evita problemas de calidad y de licencia, y mantiene el feed pequeño y auditable.

La "novedad diaria" se consigue liberando ítems en un calendario editorial precalculado; el cliente nunca queda sin material porque el pack inicial y la caché cubren días sin red.

## Esquema de selección en cliente

- El usuario elige categorías y duraciones.
- La app elige el siguiente ítem no leído que cumpla; orden estable (calendario editorial), no ranking personalizado ni perfilado.
- Nunca se repite una lectura marcada como leída.

## Seguimiento de licencias

- `LICENSES.md` en el repo: tabla con cada fuente, licencia, cumplimiento de atribución.
- Pantalla "Acerca de → Lecturas" generada desde el feed.
- Retirada: si una lectura debe retirarse, se elimina del feed y el cliente la quita de la caché en la siguiente sincronización.

## Pendiente de decidir

- Si el contenido vive en el mismo repo (`content/`) o en un repo aparte (permite licenciar contenido y código por separado). Ver [07-decisions.md](07-decisions.md).
- Tamaño del pack inicial (propuesta: 30–60 lecturas, ≈ 1–2 MB de texto).
