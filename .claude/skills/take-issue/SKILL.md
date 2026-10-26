---
name: take-issue
description: Toma un issue de D-TOX con scripts/issue.sh (comprueba que está libre, lo reclama y crea la rama con el slug). Úsala cuando el dev pida coger, reservar o empezar un issue. Argumento opcional, el número del issue.
---

# Tomar un issue

Sigue la sección «Tomar un issue» de [CLAUDE.md](../../../CLAUDE.md); esto solo encadena los comandos.

1. Sin número en `$ARGUMENTS`: `scripts/issue.sh list` y pregunta cuál, salvo que el dev ya lo haya
   acotado (fase, tema) y solo encaje uno.
2. `scripts/issue.sh show <número>`. Si dice `STATUS: BUSY`, para y di quién lo lleva.
3. Lee el issue entero y elige tipo y descripción del slug (reglas en CLAUDE.md, «Flujo»).
   Si no cabe en un cambio mínimo, propón dividirlo antes de reclamar.
4. `scripts/issue.sh claim <número> <tipo> <descripcion>`. Si falla por carrera con otro, elige otro issue.
5. Resume al dev la responsabilidad, los criterios y lo que queda fuera de alcance antes de escribir código.
