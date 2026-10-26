---
name: open-pr
description: Abre la PR de D-TOX de la rama actual hacia main con el slug como título y la plantilla del repo rellena. Úsala cuando el dev pida abrir o crear la PR.
---

# Abrir la PR

Solo cuando el dev lo pida (CLAUDE.md, «Flujo»).

1. Comprueba la rama: su nombre es un slug válido (`scripts/validate-slug.sh "$(git branch --show-current)"`)
   y no es `main`. El trabajo ya está commiteado y comprobado (skill `check`).
2. `git push`. No hace falta mergear `main` por la versión: la sube CI al mergear (D-33).
3. Si el cambio no es un fix, añade la etiqueta `version:minor` o `version:major` (`gh pr edit --add-label`).
4. Rellena [.github/pull_request_template.md](../../../.github/pull_request_template.md): qué y por qué
   con `Closes #<número del slug>`, cómo se probó (lo que de verdad se ejecutó) y el checklist marcado
   con honestidad. Si hay librería nueva o borrados, rellena esa sección.
5. `gh pr create --base main --title "<slug>" --body-file <cuerpo>` y da el enlace al dev.
