# Contribuir

1. **Clona** y activa los hooks: `git clone <repo> && cd D-TOX && scripts/setup.sh`
   (requiere Node para Prettier; JDK 17 + Android Studio para la app).
   `make help` lista los atajos: `make build`, `make run`, `make debug`, `make check`…
2. **Coge un issue** libre (`ready`, sin assignee) o crea uno con la plantilla. Un issue = una responsabilidad.
   Mira `scripts/issue.sh list` (requiere `gh`).
3. **Resérvalo y crea la rama con el slug**: `scripts/issue.sh claim 42 feat add_epub_parser`
   (te asigna, pone `in-progress` y crea `DTOX-42(feat)/add_epub_parser`). Si lo dejas: `scripts/issue.sh release 42`.
4. **Implementa** en pequeño: pocas líneas, un solo propósito. Si crece, abre otro issue.
5. **Testea**: `make check` (tests + lint, lo mismo que CI) y prueba a mano con `make run`.
6. **Commit** con el slug como primera línea; los hooks validan formato, lint y suben la versión.
7. **PR a `main`** con la plantilla. Título = slug. Si cambia algo visible, adjunta captura o GIF:
   `scripts/pr-media.sh screenshot`/`record-start`/`record-stop` y `publish` (sube a la rama `pr-media`
   y te da el Markdown). Grabar requiere `ffmpeg`.

Con Claude Code, las skills de `.claude/skills/` encadenan estos pasos: `/take-issue [número]` (2–3),
`/check` (5) y `/open-pr` (7). Las reglas siguen en [CLAUDE.md](CLAUDE.md); las skills solo las referencian.

## Slug

`DTOX[-ticket](tipo)/descripcion_corta`: minúsculas, `_`, máximo 6 palabras.
Tipos: `feat` `fix` `perf` `refactor` `style` `docs` `test` `build` `ci` `chore`.

## Reglas que bloquean la PR

- **Librería nueva**: explica origen, licencia (compatible con GPL-3.0), autoría y por qué hace falta. Requiere etiqueta `deps-approved`.
- **Borrar algo**: requiere confirmación del dev y etiqueta `deletion-approved`.
- **Versión**: no edites `VERSION` ni la versión del README; el check de la PR falla si lo haces. Al mergear,
  CI sube fix y lo publica en `main`. Para minor/major pon la etiqueta `version:minor` o `version:major`
  en la PR (como mucho una). Si cambias `scripts/version.sh` o `scripts/lib.sh`, pasa `scripts/test-version.sh`.
- **`main` local divergido** (los hooks de versión antiguos reescribían merges al hacer `git pull`): estando en `main`,
  `git fetch origin && git reset --hard origin/main` (descarta los commits locales que no estén en remoto).

Código y comentarios en inglés, textos de usuario en español. Más detalle en [CLAUDE.md](CLAUDE.md).

Al contribuir aceptas que tu aportación se licencie bajo GPL-3.0-or-later.
