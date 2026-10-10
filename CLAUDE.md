# D-TOX · Reglas del proyecto (harness estático)

App Android de lectura calmada + lecturas cortas. Contexto completo en [docs/](docs/README.md);
decisiones en [docs/07-decisions.md](docs/07-decisions.md). Si algo de aquí choca con los docs, para y pregunta.

## Flujo (siempre)

1. Trabaja **un issue mínimo**: una responsabilidad. Si la petición es grande, propón dividirla en
   varios issues antes de escribir código. Objetivo orientativo: ≤ ~150 líneas y ≤ 5 archivos por cambio.
2. Rama y commits con **slug**: `DTOX[-ticket](tipo)/descripcion_corta`
   (ej. `DTOX-42(feat)/add_epub_parser`; tipos: feat fix perf refactor style docs test build ci chore;
   minúsculas, `_`, ≤ 6 palabras, ≤ 72 caracteres). La primera línea del commit **es** el slug.
3. **Testea** antes de commitear (`./gradlew testDebugUnitTest`; los cambios de comportamiento llevan test).
4. Nunca `--no-verify`, nunca push a `main`, nunca commits con la versión editada a mano salvo para minor/major.
5. Solo commitea/pushea cuando el dev lo pida.

## Hooks y versión (automáticos, `scripts/setup.sh` los activa)

- `pre-commit`: Prettier (md/json/yml), sintaxis shell, ktlint + detekt si hay Kotlin.
- `commit-msg`: valida el slug. `post-commit`/`post-merge`: suben `VERSION` (fix+1) y el README.
- `VERSION` (`major.minor.fix`) es la fuente única. Minor/major: edítalo a mano en el commit.
- Gradle (cuando exista): `versionName = VERSION`; `versionCode = major*10_000_000 + minor*10_000 + fix`.
- Si un hook falla, arregla la causa. No lo saltes.

## README

Cada commit solo toca la versión del README (lo hace el hook). Edita contenido del README
**únicamente** si cambia algo que describe (alcance, instalación, uso, requisitos). Optimizaciones,
features mínimas, fuentes/colores/transiciones nuevas **no** lo modifican.

## Aprobación del dev (parar y preguntar, no avanzar)

- **Librería nueva** (incluye plugins Gradle): explica nombre, repositorio/origen oficial, licencia
  (debe ser compatible con GPL-3.0-or-later), autoría/mantenedores, última release, tamaño, por qué la
  stdlib/AndroidX no basta y alternativas. Sin Firebase, GMS, analítica, anuncios ni SDK que rastree.
- **Borrar algo** (archivos, funciones públicas, tests, docs, datos de usuario, migraciones): di qué
  y por qué, espera el visto bueno.

## Código

- Inglés para identificadores, KDoc y comentarios; español para textos de usuario (en `strings.xml`).
- Nombres **autoexplicativos**, sin abreviaturas ni nombres de 1–2 letras (`bookIdentifier`, no `bid`).
- KDoc mínimo en funciones/bloques no triviales: qué hace, `@param`, `@return`, pre/postcondiciones y
  errores. Nada de comentarios que repitan lo que ya dice el código.
- Clean Code y SOLID: funciones pequeñas con una sola responsabilidad, dependencias por interfaz,
  composición sobre herencia, sin duplicación, sin números mágicos, sin estado global mutable.
- Escrituras de datos de usuario **ACID en lo posible**: archivo temporal + `fsync` + rename atómico,
  validar antes de escribir, no dejar estados a medias ([docs/02-data-formats.md](docs/02-data-formats.md)).
- Errores explícitos (tipos sellados/`Result`); nada de excepciones tragadas ni `!!`; nunca bloquear el hilo principal.
- Sin logs con datos personales ni contenido de libros/notas.

## Producto (no negociable, ver docs/00-vision.md y 04-design.md)

Sin cuentas, analítica ni red salvo descarga del feed. Sin rachas/logros/notificaciones de reenganche.
Sin color de acento, imágenes en el lector ni animaciones salvo el cambio de página.

## Calidad y documentación

- Lint/format: ktlint (`ktlint_official`, 100 cols), detekt (`config/detekt/detekt.yml`), Prettier, Semgrep (`.semgrep/`).
- Si cambias una decisión o requisito, actualiza [docs/07-decisions.md](docs/07-decisions.md) o el doc afectado en el mismo cambio.
- No inventes datos ni licencias de contenido: verifica fuente y traducción ([docs/05-content-sources.md](docs/05-content-sources.md)).
