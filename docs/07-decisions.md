# 07 · Decisiones y preguntas abiertas

## Decisiones tomadas

| # | Decisión | Fecha | Motivo |
|---|---|---|---|
| D-01 | App para promover la lectura y la desintoxicación digital; lector + lecturas cortas | 2026-10-10 | Visión del autor |
| D-02 | Proyecto de código abierto en GitHub; posible publicación en Play Store más adelante | 2026-10-10 | Decisión del autor |
| D-03 | MVP solo en español | 2026-10-10 | Reducir alcance |
| D-04 | Categorías del MVP: filosofía clásica y divulgación científica, elegibles por el usuario | 2026-10-10 | Decisión del autor |
| D-05 | Sin sincronización entre dispositivos en el MVP | 2026-10-10 | Reducir alcance |
| D-06 | Android `minSdk 26` (Android 8.0) | 2026-10-10 | Buena retrocompatibilidad con APIs modernas (java.time, canales de notificación) |
| D-07 | Posiciones ancladas al contenido (capítulo/bloque/offset + fragmento), no a página | 2026-10-10 | La paginación depende de pantalla y tipografía |
| D-08 | Un archivo JSON por libro para progreso y notas | 2026-10-10 | Legible, fusionable, borrado simple |
| D-09 | EPUB prioritario; PDF "mejor esfuerzo" | 2026-10-10 | El PDF no tiene estructura semántica |
| D-10 | Lecturas cortas desde feed estático (GitHub Actions + Pages) + pack offline + avisos locales | 2026-10-10 | Sin servidor que mantener, privacidad, compatible con F-Droid |
| D-11 | Contenido solo de dominio público o licencia libre, curado manualmente en el MVP | 2026-10-10 | Riesgo legal de scrapear prensa y traducciones con copyright |
| D-12 | Sin rachas, logros ni notificaciones de reenganche | 2026-10-10 | Coherencia con el propósito |
| D-13 | Fuente de lectura por defecto: Libron; alternativas OpenDyslexic y monoespaciada | 2026-10-10 | Preferencia del autor; OFL |
| D-14 | Nombre definitivo: **D-TOX**. La app vive en este mismo repo (proyecto Android Studio en la raíz) | 2026-10-10 | Decisión del autor |
| D-15 | Licencia del código: libre de usar y modificar, pero **sin uso comercial** por terceros. Candidata: PolyForm Noncommercial 1.0.0 (pendiente de confirmar y añadir `LICENSE`) | 2026-10-10 | Decisión del autor; ver Q-01 |

## Preguntas abiertas

| # | Pregunta | Opciones / notas | Bloquea |
|---|---|---|---|
| Q-01 | **Texto de la licencia** | Intención: no comercial. Una licencia que prohíbe uso comercial **no es "open source" según OSI/FSF**, y **F-Droid no la acepta**; Play Store sí. Candidata: PolyForm Noncommercial 1.0.0 (alternativas: CC BY-NC-SA, poco recomendada para software). Confirmar y añadir `LICENSE` | Fase 0 |
| Q-02 | **`applicationId`** | Nombre ya decidido (D-TOX); falta el id de paquete al crear el proyecto en Android Studio, p. ej. `dev.<usuario>.dtox`. Es difícil de cambiar tras publicar | Fase 0 |
| Q-03 | **Licencia del contenido** | Código y contenido pueden tener licencias distintas; ¿repo aparte `dtox-content`? | Fase 3 |
| Q-04 | **Verificar `LICENSE` de Libron** | La búsqueda indica OFL (derivada de Readerly), pero hay que leer el archivo y comprobar nombre reservado | Fase 0 (empaquetado) |
| Q-05 | **Alcance de PDF en v1** | ¿Solo extracción o también fallback visual? | Fase 2 |
| Q-06 | **DI**: manual vs Hilt | Decidir al inicializar; proyecto pequeño | Fase 0 |
| Q-07 | **Calendario editorial**: ¿cuántas lecturas nuevas al día en el feed? | Propuesta: 1 por categoría y día, acumulativas | Fase 3 |
| Q-08 | **Cómo se muestran notas en el lector** | Punto `·` + panel, o solo en lista | Fase 2 |
| Q-09 | **Tabla de contenidos para TXT/MD** | Derivar de encabezados; en TXT sin encabezados, un capítulo único | Fase 1 |
| Q-10 | **Hash para libros grandes** | SHA-256 del archivo completo; considerar hash parcial si es lento | Fase 1 |

## Plantilla para nuevas decisiones

```
| D-XX | Decisión | AAAA-MM-DD | Motivo |
```

Las decisiones no se borran; si se revierten, se añade una nueva que referencie la anterior.
