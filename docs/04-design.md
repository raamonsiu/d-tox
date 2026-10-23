# 04 · Guía de diseño

Objetivo: **mínimo estímulo visual**. Si un elemento no ayuda a leer, no está.

## Principios visuales

1. Sin color de acento. La jerarquía se logra con **tamaño, peso y espacio**, no con color.
2. Sin iconos decorativos; iconos solo funcionales y de trazo fino, o texto.
3. Sin imágenes ni portadas en el lector (en la biblioteca, solo texto).
4. Espacio en blanco generoso; márgenes amplios; columna de lectura estrecha (≈ 55–70 caracteres).
5. Sin sombras, degradados, ni elevaciones. Divisores como líneas finas.
6. Una única animación: el cambio de página.

## Temas

Valores **propuestos**; validar contraste AA/AAA con herramienta antes de fijarlos.

| Token        | Papel                 | Sepia     | Noche     |
| ------------ | --------------------- | --------- | --------- |
| `background` | `#F7F5EF`             | `#F1E9D2` | `#0F0F0E` |
| `text`       | `#1C1C1A`             | `#2B261B` | `#D9D6CC` |
| `textMuted`  | `#6B6A64`             | `#6E6550` | `#8E8B81` |
| `divider`    | `#DEDBD2`             | `#D6CCB0` | `#2A2926` |
| `highlight`  | `#E9E4D2` (sombreado) | `#E0D4AE` | `#2E2D27` |

- "Blanco" puro y "negro" puro se evitan para reducir el deslumbramiento; el tema noche permite probar `#000000` como opción OLED.
- Todos los temas usan la misma estructura; solo cambian los valores.
- Por defecto: seguir el modo claro/oscuro del sistema (papel/noche), con elección manual.

## Tipografía

| Rol                             | Fuente                                                             | Licencia | Notas                                                                                 |
| ------------------------------- | ------------------------------------------------------------------ | -------- | ------------------------------------------------------------------------------------- |
| Lectura principal (por defecto) | **Libron** (revisión de Readerly, optimizada para lectura digital) | SIL OFL  | Verificar el archivo `LICENSE` del repo y respetar _Reserved Font Name_ si lo hubiera |
| Accesibilidad                   | **OpenDyslexic**                                                   | SIL OFL  | Opción seleccionable                                                                  |
| Monoespaciada                   | **IBM Plex Mono** (propuesta; alternativa JetBrains Mono)          | SIL OFL  | Opción seleccionable                                                                  |
| Interfaz (menús, ajustes)       | La misma fuente de lectura elegida                                 |          | Una sola familia en toda la app                                                       |

- Tamaño de texto: 14–28 sp, por defecto 18 sp; interlineado 1.5–1.7; alineación a la izquierda (sin justificar, para evitar "ríos" y espacios irregulares).
- Se empaquetan los archivos de fuente y se incluye un aviso de licencias dentro de la app.

### Jerarquía tipo Markdown

Libron solo tiene Regular y Bold (más cursivas): los títulos usan Bold en vez de un peso medio (D-26).

| Elemento                      | Tratamiento                                                                     |
| ----------------------------- | ------------------------------------------------------------------------------- |
| Título de libro/capítulo (H1) | +40 % de tamaño, negrita, más espacio superior                                  |
| H2                            | +20 %, negrita                                                                  |
| H3                            | Tamaño base, negrita, mayúscula inicial                                         |
| Párrafo                       | Tamaño base, sangría de primera línea o espacio entre párrafos (una de las dos) |
| Cita                          | Sangría, `textMuted`                                                            |
| Lista                         | Guión simple `–`, sangría                                                       |
| Separador                     | Una línea fina o `·  ·  ·`                                                      |

El texto corriente **no destaca**: color `text` sobre `background`, sin negritas arbitrarias.

## Pantallas

### Biblioteca

- Lista de texto: título, autor, y progreso muy tenue (p. ej. `31 %`).
- Orden: último leído primero. Acción "Añadir libro" como texto, no FAB colorido.
- Estado vacío: una frase y la acción de importar.

### Lector

- Pantalla completa: solo texto.
- Toque en el centro → barra mínima (volver, capítulos, ajustes de texto) que se oculta sola.
- Toque en bordes izquierdo/derecho o deslizamiento → cambia de página.
- Indicador de progreso: línea de 1 px o texto `12 / 148` muy tenue; desactivable.
- Pulsación larga → selección → acciones "Resaltar" / "Nota" (v1).
- Resaltados: sombreado sutil (`highlight`); notas: pequeño punto `·` al final del fragmento.

### Transición de página

- Deslizamiento horizontal tipo "cambio de escritorio" (la página saliente y la entrante se desplazan juntas), ~200–250 ms, curva suave.
- Opción "Sin animación". Respetar el ajuste del sistema de "Quitar animaciones".

### Lecturas

- Lista **finita** (las lecturas disponibles ahora, no un feed). Cada una: título, autor, duración (`8 min`), categoría.
- Sin "cargar más". Si no hay, mensaje breve: "No hay más lecturas por hoy."
- Marcar como leída desde el propio lector al terminar.

### Ajustes

- Lista plana de texto. Secciones: Lectura, Avisos, Contenido, Datos, Acerca de.
- Avisos: activar, número al día (0–4), horas, horario de silencio.
- Contenido: categorías (filosofía, ciencia), duraciones (5/10/20 min).
- Acerca de: versión, licencias (código, fuentes tipográficas, lecturas).

### Notificación

- Título: "Una lectura de 10 min". Texto: título y autor. Sin imagen ni acciones adicionales. Sin sonido ni vibración por defecto. Sin contador de insignia.

## Interacción y accesibilidad

- Zonas táctiles ≥ 48 dp.
- Contraste ≥ 4.5:1 para texto normal.
- Escalado con la fuente del sistema como valor inicial.
- Etiquetas de contenido para TalkBack en controles; el texto del lector expuesto como contenido legible.
- Háptica mínima o ninguna.

## Referencias de tono

Papel impreso, e-readers de tinta electrónica, editores de texto tipo "iA Writer" en su sobriedad (sin copiar su identidad visual).
