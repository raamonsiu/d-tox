# 01 · Requisitos

Prioridad: **M** = Must, **S** = Should, **C** = Could. Fase: ver [06-roadmap.md](06-roadmap.md).

## Funcionales

### Biblioteca e importación

| ID    | Requisito                                                                                  | Prio | Fase |
| ----- | ------------------------------------------------------------------------------------------ | ---- | ---- |
| RF-01 | Importar un libro individual (EPUB, TXT, MD) mediante el selector de archivos del sistema  | M    | MVP  |
| RF-02 | Mostrar la lista de libros importados (título, autor) ordenada por última lectura          | M    | MVP  |
| RF-03 | Identificar cada libro por hash del contenido (`bookId`) para sobrevivir a cambios de ruta | M    | MVP  |
| RF-04 | Eliminar un libro de la biblioteca sin borrar su archivo original                          | M    | MVP  |
| RF-05 | Definir una o varias carpetas vigiladas (Storage Access Framework) y listar sus libros     | S    | v1   |
| RF-06 | Importar PDF con extracción de texto "mejor esfuerzo" y aviso de calidad                   | S    | v1   |
| RF-07 | Abrir PDF en su diseño original (fallback) cuando la extracción sea mala                   | C    | v1   |
| RF-08 | Leer metadatos (título, autor, portada ignorada) de EPUB; nombre de archivo en su defecto  | M    | MVP  |

### Lector

| ID    | Requisito                                                                                                | Prio | Fase |
| ----- | -------------------------------------------------------------------------------------------------------- | ---- | ---- |
| RF-10 | Mostrar el texto reflowable con estructura tipo Markdown: títulos jerarquizados, párrafos, citas, listas | M    | MVP  |
| RF-11 | Paginación (sin scroll infinito) calculada según pantalla, fuente y tamaño                               | M    | MVP  |
| RF-12 | Transición entre páginas con deslizamiento horizontal tipo "escritorios"; opción de desactivarla         | M    | MVP  |
| RF-13 | Cambio de página por toque en bordes y por deslizamiento                                                 | M    | MVP  |
| RF-14 | Elegir fuente (Libron, OpenDyslexic, monoespaciada), tamaño, interlineado y márgenes                     | M    | MVP  |
| RF-15 | Elegir tema (papel, sepia, noche)                                                                        | M    | MVP  |
| RF-16 | Navegar por tabla de contenidos / capítulos                                                              | S    | MVP  |
| RF-17 | Guardar y restaurar automáticamente la posición de lectura                                               | M    | MVP  |
| RF-18 | Indicador de progreso discreto (opcional, oculto por defecto o muy tenue)                                | S    | MVP  |
| RF-19 | Búsqueda de texto dentro del libro                                                                       | C    | v1   |

### Notas y marcas

| ID    | Requisito                                                                                           | Prio | Fase                         |
| ----- | --------------------------------------------------------------------------------------------------- | ---- | ---------------------------- |
| RF-20 | Resaltar un fragmento de texto                                                                      | M    | v1                           |
| RF-21 | Añadir una nota de texto a un fragmento o posición                                                  | M    | v1                           |
| RF-22 | Marcadores de posición                                                                              | S    | v1                           |
| RF-23 | Listar y navegar a todas las marcas y notas de un libro                                             | M    | v1                           |
| RF-24 | Editar y eliminar marcas y notas                                                                    | M    | v1                           |
| RF-25 | Guardar progreso y marcas en archivo plano por libro (ver [02-data-formats.md](02-data-formats.md)) | M    | MVP (progreso) / v1 (marcas) |
| RF-26 | Exportar notas y resaltados a Markdown                                                              | S    | v1                           |
| RF-27 | Elegir carpeta de datos externa para los archivos (p. ej. para Syncthing)                           | C    | v1                           |

### Lecturas cortas

| ID    | Requisito                                                                                | Prio | Fase |
| ----- | ---------------------------------------------------------------------------------------- | ---- | ---- |
| RF-30 | Incluir un pack de lecturas empaquetado en la app (funciona sin red)                     | M    | v2   |
| RF-31 | Descargar periódicamente un feed estático con lecturas nuevas                            | M    | v2   |
| RF-32 | Elegir categorías (filosofía clásica, divulgación científica) y duraciones (5/10/20 min) | M    | v2   |
| RF-33 | Programar 0–N avisos diarios en horas configurables (por defecto 1–2)                    | M    | v2   |
| RF-34 | Horario de silencio y desactivación global de avisos                                     | M    | v2   |
| RF-35 | Pantalla "Lecturas" con la selección disponible, sin scroll infinito (lista finita)      | M    | v2   |
| RF-36 | Abrir la lectura en el mismo lector; marcar como leída; no repetir lecturas              | M    | v2   |
| RF-37 | Mostrar autor, fuente, licencia y enlace de origen en cada lectura                       | M    | v2   |
| RF-38 | Guardar una lectura corta en la biblioteca                                               | C    | v2   |

### Ajustes

| ID    | Requisito                                                                | Prio | Fase |
| ----- | ------------------------------------------------------------------------ | ---- | ---- |
| RF-40 | Pantalla de ajustes mínima: lectura, avisos, contenido, datos, acerca de | M    | MVP  |
| RF-41 | Exportar / importar todos los datos del usuario (zip)                    | S    | v1   |

## No funcionales

| ID     | Requisito      | Detalle                                                                                                                                             |
| ------ | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| RNF-01 | Compatibilidad | `minSdk 26` (Android 8.0). `targetSdk` = la última estable al inicializar el proyecto (requisito de Play Store)                                     |
| RNF-02 | Privacidad     | Sin cuentas, sin analítica, sin SDK de terceros que rastree. Única red: descarga del feed estático                                                  |
| RNF-03 | Offline        | Toda la funcionalidad salvo la actualización del feed funciona sin red                                                                              |
| RNF-04 | Rendimiento    | Abrir un libro típico (≤ 1 MB de texto) en < 2 s en un dispositivo de gama media; cambio de página a 60 fps                                         |
| RNF-05 | Memoria        | No cargar libros enteros como un único `String` en UI; modelo por capítulos con carga diferida                                                      |
| RNF-06 | Robustez       | Escritura atómica de archivos de datos (temp + rename); un archivo corrupto no debe impedir abrir el libro                                          |
| RNF-07 | Accesibilidad  | Contraste WCAG AA, tamaños escalables con `sp`, compatibilidad con TalkBack en navegación; respetar escala de fuente del sistema como valor inicial |
| RNF-08 | Batería        | Sin servicios en primer plano; trabajo diferido con WorkManager; avisos con alarmas inexactas                                                       |
| RNF-09 | Tamaño         | APK < 25 MB incluyendo fuentes y pack de lecturas inicial                                                                                           |
| RNF-10 | Licencias      | Todas las dependencias y recursos con licencia compatible con distribución abierta; atribución visible                                              |
| RNF-11 | Localización   | UI en español; cadenas en recursos para localizar después                                                                                           |
| RNF-12 | Calidad        | Tests unitarios del parser, paginador y formato de datos; lint sin errores                                                                          |
| RNF-13 | Seguridad      | No ejecutar contenido activo (JS) de EPUB; sanitizar HTML al convertir al modelo propio                                                             |

## Criterios de aceptación del MVP

1. Un usuario instala la app, importa un EPUB y lo lee paginado con tema y fuente elegidos.
2. Cierra la app, la reabre y vuelve exactamente al mismo párrafo, incluso tras cambiar el tamaño de fuente.
3. Todo ello sin permisos peligrosos ni acceso a red.
