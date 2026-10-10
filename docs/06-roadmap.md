# 06 · Roadmap

Orden pensado para validar antes lo más arriesgado técnicamente (paginación, parser) y dejar el contenido para cuando la base de lectura funcione.

## Fase 0 · Preparación (siguiente paso)

- [x] Cerrar preguntas abiertas de [07-decisions.md](07-decisions.md) (licencia, nombre, `applicationId`).
- [x] Harness estático: [CLAUDE.md](../CLAUDE.md), hooks, versionado, plantillas, CI de PR.
- [ ] Harness dinámico (skills/subagentes/comandos útiles) cuando exista código.
- [ ] Inicializar proyecto en Android Studio (Kotlin, Compose, `minSdk 26`).
- [x] Repositorio Git, CI de PR (slug, versión, Prettier, Semgrep), plantillas.
- [ ] Cablear en Gradle: ktlint, detekt (`config/detekt/detekt.yml`), `versionName/versionCode` desde `VERSION`.
- [ ] En GitHub: proteger `main`, exigir los checks, crear etiquetas `deps-approved`, `deletion-approved`, `ready`, `in-progress` y `blocked`. Probar `scripts/issue.sh` con `gh` y remoto reales (aún sin probar).
- [ ] Recopilar corpus de prueba: 10 EPUB variados (Gutenberg, Standard Ebooks, Calibre), 5 PDF, TXT/MD.
- [ ] Empaquetar fuentes (Libron, OpenDyslexic, IBM Plex Mono) con sus licencias.

**Salida:** proyecto compila, abre una pantalla vacía con el tema propio y pasa CI.

## Fase 1 · MVP · Lector base

Alcance: RF-01–04, 08, 10–18, 25 (progreso), 40.

- Modelo de documento y parsers EPUB / TXT / MD.
- Importación por selector de archivos; índice de biblioteca.
- Paginador y lector con `HorizontalPager`, transición, temas y fuentes.
- Progreso con anclas; archivo `books/<bookId>.json`.
- Ajustes mínimos.

**Salida (criterios de aceptación):**

1. Importar y leer un EPUB con capítulos y títulos diferenciados.
2. La posición se restaura tras cerrar la app y tras cambiar tamaño de fuente.
3. 60 fps en el cambio de página en gama media; apertura < 2 s para un libro típico.
4. Tests del parser, paginador y formato de datos en verde.

## Fase 2 · v1 · Notas y biblioteca ampliada

Alcance: RF-05–07, 19–24, 26, 27, 41.

- Selección de texto, resaltados, notas, marcadores y su lista.
- Carpeta(s) vigilada(s) con SAF.
- PDF mejor esfuerzo + fallback visual.
- Exportación a Markdown y exportación/importación de datos.
- Búsqueda en el libro.

**Salida:** un usuario puede leer, anotar y recuperar sus notas exportadas; el archivo JSON sobrevive a reinstalación mediante exportación.

## Fase 3 · v2 · Lecturas cortas

Alcance: RF-30–38.

- Repo/carpeta de contenido, `sources.yaml`, pipeline y GitHub Actions + Pages.
- Pack inicial (30–60 lecturas) empaquetado.
- Cliente: cache, selección, pantalla "Lecturas", avisos locales, ajustes.
- Atribución y licencias en la app.

**Salida:** instalación sin red muestra lecturas; con red se actualiza el feed; los avisos llegan en las horas elegidas y respetan silencio y límites.

## Fase 4 · Pulido y publicación

- Revisión de accesibilidad (TalkBack, contraste, escala de fuente).
- Rendimiento en dispositivos de gama baja.
- Preparar publicación: README, capturas, política de privacidad (breve), `LICENSES.md`, firma de release.
- Publicar release en GitHub; valorar F-Droid y Play Store.

## Después (sin compromiso)

- Sincronización por archivos (Syncthing/Drive) con fusión por `id`.
- Más categorías y más idiomas.
- TTS opcional.
- Tablet/horizontal, e-ink.
- Estadísticas **opcionales y privadas** (tiempo leído) si no contradicen los principios.

## Riesgos de calendario

| Riesgo                                  | Efecto      | Respuesta                                                      |
| --------------------------------------- | ----------- | -------------------------------------------------------------- |
| Selección de texto en paginación propia | Retrasa v1  | Prototipo temprano en Fase 1; fallback a resaltado por párrafo |
| Verificación de licencias/traducciones  | Retrasa v2  | Empezar curación en paralelo desde Fase 1                      |
| PDF poco fiable                         | Frustración | Mantenerlo fuera del MVP y marcado como "mejor esfuerzo"       |
