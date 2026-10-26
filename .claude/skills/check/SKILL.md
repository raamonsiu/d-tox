---
name: check
description: Ejecuta en D-TOX las mismas comprobaciones que CI (make check y, según lo tocado, Prettier y el test de versión) e informa del resultado. Úsala antes de commitear o cuando el dev pida pasar tests o lint.
---

# Comprobar el cambio

1. Mira qué cambió: `git status --short` y `git diff --stat origin/main...`.
2. Si hay Kotlin o Gradle: `make check` (tests unitarios + lint, lo mismo que CI).
3. Si hay Markdown/JSON/YAML: `npx --yes "prettier@$(source scripts/lib.sh && echo "$PRETTIER_VERSION")" --check <archivos>`.
4. Si hay cambios en `scripts/version.sh` o `scripts/lib.sh`: `scripts/test-version.sh`.
5. Informa de qué se ejecutó y qué no (y por qué). Si algo falla, muestra el error y arregla la causa;
   nunca saltes hooks ni checks (CLAUDE.md).
