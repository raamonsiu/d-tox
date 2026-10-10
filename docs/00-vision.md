# 00 · Visión

## Propósito

Ayudar a recuperar tiempo de atención sostenida. D-TOX sustituye parte del consumo digital fragmentado (feeds, notificaciones, vídeos cortos) por **lectura completa y tranquila**.

## Qué es

1. **Un lector** sencillo para los libros del usuario (EPUB, TXT/Markdown; PDF con limitaciones).
2. **Un proveedor de lecturas cortas completas** (5, 10 o 20 min) de filosofía clásica y divulgación científica, avisadas un par de veces al día, a elección del usuario.

## Principios

1. **Calma antes que engagement.** Ninguna decisión de producto puede aumentar el tiempo en app a costa del bienestar del usuario.
2. **Poco estímulo visual.** Sin iconografía decorativa, sin color de acento, sin imágenes en el lector, sin animaciones salvo el cambio de página.
3. **El texto es el protagonista.** La interfaz desaparece mientras se lee.
4. **Los datos son del usuario.** Archivos planos legibles, exportables, sin cuenta.
5. **Local primero.** Todo funciona sin red; la red solo trae lecturas nuevas.
6. **Honestidad con los avisos.** Pocas notificaciones, configurables, sobrias, sin presión.

## Anti-características (no se harán)

- Rachas, logros, medallas, puntos, ranking, "llevas X días".
- Estadísticas que culpabilicen o comparen.
- Notificaciones de reenganche ("hace días que no lees…").
- Feed infinito, recomendaciones algorítmicas, perfilado.
- Cuentas, analítica de terceros, publicidad.

## Público objetivo (MVP)

Personas hispanohablantes que quieren leer más y usar menos el móvil de forma compulsiva; cómodas con una app austera. Uso en móvil Android en vertical.

## Diferenciador

Existen buenos lectores (KOReader, Librera, Moon+ Reader, Readest…). D-TOX se diferencia por la **combinación**: lector deliberadamente austero + lecturas cortas completas curadas, entregadas sin cuentas ni servidor propio, con filosofía anti-dopamina explícita.

## No-objetivos del MVP

- Sincronización entre dispositivos.
- Tienda o catálogo de libros comerciales; formatos con DRM.
- OCR de PDF escaneados.
- Audiolibros / TTS.
- Idiomas distintos del español en el contenido recomendado (la UI y la lectura de libros propios pueden ser agnósticas del idioma).
- Tablets/plegables optimizados y orientación horizontal avanzada.
- Social, compartir, comentarios.

## Distribución

- Código abierto en GitHub bajo **GPL-3.0-or-later** (ver D-16 en [07-decisions.md](07-decisions.md)).
- Publicación futura posible en Play Store y F-Droid; por eso se evitan dependencias propietarias (Firebase, Google Play Services) desde el inicio.
