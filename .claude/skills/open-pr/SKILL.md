---
name: open-pr
description: Abre la PR de D-TOX de la rama actual hacia main con el slug como título, la plantilla rellena y capturas si cambia la UI. Úsala cuando el dev pida abrir o crear la PR.
---

# Abrir la PR

Solo cuando el dev lo pida (CLAUDE.md, «Flujo»).

1. Comprueba la rama: su nombre es un slug válido (`scripts/validate-slug.sh "$(git branch --show-current)"`)
   y no es `main`. El trabajo ya está commiteado y comprobado (skill `check`).
2. `git push`. No hace falta mergear `main` por la versión: la sube CI al mergear (D-33).
3. Si el cambio no es un fix, añade la etiqueta `version:minor` o `version:major` (`gh pr edit --add-label`).
4. **Capturas** (si el cambio se ve en pantalla). Con la app instalada en un emulador libre
   (`ANDROID_SERIAL`; no uses uno en el que esté trabajando otro agente) y contenido de ejemplo de
   dominio público:

   - estado estático: `scripts/pr-media.sh screenshot <scratchpad>/<nombre>.png`;
   - interacción (gestos, transiciones): `scripts/pr-media.sh record-start`, haz los pasos con
     `adb shell input` y `scripts/pr-media.sh record-stop <scratchpad>/<nombre>.gif` (≤ ~10 s: saca las coordenadas
     con `uiautomator dump` antes de grabar, no entre toques).
   - mira cada imagen antes de publicarla (que muestre el cambio y nada personal) y
     `scripts/pr-media.sh publish <archivos>`: imprime el Markdown para la sección «Capturas».

   Sin cambios visibles, escribe «Sin cambios visibles».

5. Rellena [.github/pull_request_template.md](../../../.github/pull_request_template.md): qué y por qué
   con `Closes #<número del slug>`, cómo se probó (lo que de verdad se ejecutó) y el checklist marcado
   con honestidad. Si hay librería nueva o borrados, rellena esa sección.
6. `gh pr create --base main --title "<slug>" --body-file <cuerpo>` y da el enlace al dev.
