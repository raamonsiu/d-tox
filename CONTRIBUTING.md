# Contribuir

1. **Clona** y activa los hooks: `git clone <repo> && cd D-TOX && scripts/setup.sh`
   (requiere Node para Prettier; JDK 17 + Android Studio para la app).
2. **Coge un issue** libre (`ready`, sin assignee) o crea uno con la plantilla. Un issue = una responsabilidad.
   Mira `scripts/issue.sh list` (requiere `gh`).
3. **Resérvalo y crea la rama con el slug**: `scripts/issue.sh claim 42 feat add_epub_parser`
   (te asigna, pone `in-progress` y crea `DTOX-42(feat)/add_epub_parser`). Si lo dejas: `scripts/issue.sh release 42`.
4. **Implementa** en pequeño: pocas líneas, un solo propósito. Si crece, abre otro issue.
5. **Testea**: `./gradlew testDebugUnitTest` y prueba a mano lo que toques.
6. **Commit** con el slug como primera línea; los hooks validan formato, lint y suben la versión.
7. **PR a `main`** con la plantilla. Título = slug.

## Slug

`DTOX[-ticket](tipo)/descripcion_corta`: minúsculas, `_`, máximo 6 palabras.
Tipos: `feat` `fix` `perf` `refactor` `style` `docs` `test` `build` `ci` `chore`.

## Reglas que bloquean la PR

- **Librería nueva**: explica origen, licencia (compatible con GPL-3.0), autoría y por qué hace falta. Requiere etiqueta `deps-approved`.
- **Borrar algo**: requiere confirmación del dev y etiqueta `deletion-approved`.
- **Versión**: la sube el hook. Si `main` avanzó, haz `git merge origin/main` en local (no el botón de GitHub).
  Para subir minor/major edita `VERSION` a mano en tu commit.

Código y comentarios en inglés, textos de usuario en español. Más detalle en [CLAUDE.md](CLAUDE.md).

Al contribuir aceptas que tu aportación se licencie bajo GPL-3.0-or-later.
