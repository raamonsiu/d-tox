# D-TOX · Documentación de planificación

Estado: **planificación (pre-código)** · Última revisión: 2026-10-10

D-TOX es una app Android de lectura pensada para la desintoxicación digital: un lector calmado y poco estimulante, más un flujo diario de lecturas cortas y completas.

## Índice

| Documento | Contenido |
|---|---|
| [00-vision.md](00-vision.md) | Propósito, principios, público, no-objetivos, diferenciador |
| [01-requirements.md](01-requirements.md) | Requisitos funcionales y no funcionales con prioridad y fase |
| [02-data-formats.md](02-data-formats.md) | Modelo de documento, anclas, archivo de progreso/notas, feed de lecturas |
| [03-architecture.md](03-architecture.md) | Stack, capas, paginación, importación, notificaciones, backend estático |
| [04-design.md](04-design.md) | Guía visual: paleta, tipografía, pantallas, interacción |
| [05-content-sources.md](05-content-sources.md) | Fuentes de lecturas cortas, licencias, pipeline de contenido |
| [06-roadmap.md](06-roadmap.md) | Fases y criterios de salida |
| [07-decisions.md](07-decisions.md) | Registro de decisiones y preguntas abiertas |

## Resumen en 30 segundos

- Lector de **EPUB / TXT / Markdown** (PDF en modo "mejor esfuerzo"), texto reflowable y paginado, estética mínima.
- Progreso, notas y resaltados en **archivos planos** (JSON) anclados al contenido, no a la página.
- **Lecturas cortas** (5/10/20 min) de filosofía clásica y divulgación científica, en español, desde un feed estático + pack offline, avisadas con notificaciones locales configurables.
- Sin cuentas, sin analítica, sin servidores propios, sin sincronización (MVP). Código abierto (GPL-3.0-or-later) en GitHub.
