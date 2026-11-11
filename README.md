# J2ME Infinity Loader

**Español** | [English](README.en.md)

J2ME Infinity Loader es un emulador de J2ME (juegos y aplicaciones Java ME) para Android. Es un fork de [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), así que conserva todo lo que lo hace bueno: soporta la mayoría de juegos 2D y 3D (incluidos los de Mascot Capsule 3D), tiene teclado virtual, ajustes individuales para cada aplicación y escalado de pantalla.

Encima de eso añade mejoras pensadas para jugar en un teléfono moderno, tomadas de otros forks y de [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit).

## Qué añade esta versión

Todas las opciones nuevas están en la **configuración de cada juego**, igual que las del J2ME Loader original.

- **Multitáctil forzado** (*Controles → Forzar multitáctil*): cada dedo llega al juego por separado, aunque el juego no lo declare en su manifiesto. Así se puede, por ejemplo, moverse y saltar a la vez con los controles táctiles del propio juego. Viene apagado porque algunos juegos hechos para un solo dedo se confunden con un segundo.
- **Girar imagen 90° / 270°** (*Pantalla → Girar imagen*): para juegos que se juegan de lado. La imagen se gira en los tres modos de dibujo (software, hardware y OpenGL) y los toques se giran también, para que caigan en el sitio correcto. Combínalo con la orientación *Horizontal*.
- **Superresolución** (*Fuentes → Superresolución*): el juego se dibuja de 2× a 10× más grande y se reduce al mostrarlo, así el texto y los gráficos se ven nítidos en vez de borrosos. En *Auto* elige el tamaño según tu pantalla y baja solo si falta memoria. Incluye el 3D (M3G y Mascot Capsule). Tomado de [J2ME-Loader-HD](https://github.com/fuyijian/J2ME-Loader-HD).
- **Escala entera** (*Pantalla → Tipo de escalado*): cada píxel del juego ocupa exactamente N×N píxeles de la pantalla; la imagen más nítida posible.
- **Anti-parpadeo** (*Pantalla*): mezcla cada fotograma con el anterior. Arregla los juegos que hacen transparencias con rayas o puntos que se alternan y en una pantalla moderna parpadean.
- **Ajuste automático** (*Pantalla*): si el juego solo dibuja una parte de la pantalla (por ejemplo 176×208 dentro de 240×320), muestra solo esa parte. Espera unos 12 segundos para no confundirse con un logo.
- **Velocidad 1×–4×** (menú del juego → *Velocidad del juego*): acelera el juego en tiempo real. Los juegos instalados antes de esta versión hay que volver a instalarlos.
- **Mando en pantalla** (menú del juego → teclado → *Mando*): cruceta de 8 direcciones, A B X Y, L y R, Select y Start. Se puede mover, cambiar de tamaño y reasignar con el editor de siempre.
- **Mandos físicos listos para usar**: Xbox, PlayStation y genéricos funcionan sin configurar (A = OK, B = 7, X = 9, Y = 0, L = \*, R = #, Select/Start = teclas suaves), y el stick izquierdo mueve como la cruceta.
- **Instalar una carpeta** (menú principal): instala todos los juegos de una carpeta y sus subcarpetas de una vez.
- **Silencio en segundo plano**: al salir del juego la música se pausa y vuelve al regresar.
- **Se instala junto al original**: tiene su propio identificador, así que no reemplaza al J2ME Loader.
- **Instala juegos compilados con Java moderno**: los ports hechos con un JDK reciente ya no fallan con `Dexing error` al instalar.
- **Permisos más discretos**: no pide "acceso a todos los archivos"; el almacenamiento funciona como en el J2ME Loader original, y los permisos antiguos de almacenamiento y Bluetooth solo se declaran en las versiones de Android que los usan.

Requisitos del sistema: los mismos que el J2ME Loader original.

## Compatibilidad

Las listas de juegos probados del proyecto original siguen sirviendo como referencia:

- [Juegos probados (pantalla táctil)](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Touchscreen))
- [Juegos probados (sin pantalla táctil)](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Non-Touchscreen))
- [Juegos con errores](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Java-Games-with-Bugs)

## Consejos

- Activar el filtrado puede bajar mucho el rendimiento en algunos casos. Desactívalo si el juego va lento.
- Si la imagen parpadea, prueba a activar la opción "Modo de procesamiento inmediato".
- Si un juego táctil no reacciona a dos dedos, activa *Forzar multitáctil* en su configuración.
- La superresolución usa más memoria. Si un juego va lento o se cierra, bájala o ponla en *No*.

## Compilar

Abre el proyecto en Android Studio y compila la variante que quieras, o usa la línea de comandos:

```bash
./gradlew assembleOpenDebug     # versión "open" de depuración
```

## Capturas

<img src="/screenshots/screen.jpg" width="288" height="512"> <img src="/screenshots/screen2.jpg" width="288" height="512">
<img src="/screenshots/screen3.jpg" width="288" height="512"> <img src="/screenshots/screen4.jpg" width="288" height="512">

## Créditos

- [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), la base de este proyecto, que a su vez es un fork de [J2meLoader](https://github.com/NaikSoftware/J2meLoader).
- [woesss](https://github.com/woesss), autor de [JL-Mod](https://github.com/woesss/JL-Mod), por la implementación libre de Mascot Capsule, el soporte de bancos de sonido y muchas otras mejoras.
- [fuyijian/J2ME-Loader-HD](https://github.com/fuyijian/J2ME-Loader-HD), por la superresolución y el arreglo de instalación.
- [H3nb/JL-Mod-Plus](https://github.com/H3nb/JL-Mod-Plus), como referencia de controles y biblioteca.
- [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit), de donde vienen las ideas de multitáctil y rotación.

## Licencia

> Copyright 2017-2026 Nikita Shakarun y colaboradores.
> Licenciado bajo la [Licencia Apache, versión 2.0](http://www.apache.org/licenses/LICENSE-2.0).
> (Consulta el archivo [LICENSE](LICENSE) para leer el texto completo.)
