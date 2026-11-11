# J2ME Infinity Loader

**Español** | [English](README.en.md)

J2ME Infinity Loader es un emulador de J2ME (juegos y aplicaciones Java ME) para Android. Es un fork de [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), así que conserva todo lo que lo hace bueno: soporta la mayoría de juegos 2D y 3D (incluidos los de Mascot Capsule 3D), tiene teclado virtual, ajustes individuales para cada aplicación y escalado de pantalla.

Encima de eso añade mejoras pensadas para jugar en un teléfono moderno, tomadas de otros forks y de [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit).

## Qué añade esta versión

Todas las opciones nuevas están en la **configuración de cada juego**, igual que las del J2ME Loader original.

- **Multitáctil forzado** (*Controles → Forzar multitáctil*): cada dedo llega al juego por separado, aunque el juego no lo declare en su manifiesto. Así se puede, por ejemplo, moverse y saltar a la vez con los controles táctiles del propio juego. Viene apagado porque algunos juegos hechos para un solo dedo se confunden con un segundo.
- **Girar imagen 90° / 270°** (*Pantalla → Girar imagen*): para juegos que se juegan de lado. La imagen se gira en los tres modos de dibujo (software, hardware y OpenGL) y los toques se giran también, para que caigan en el sitio correcto. Combínalo con la orientación *Horizontal*.
- **Superresolución** (*Fuentes → Superresolución*): el juego se dibuja de 2× a 10× más grande y se reduce al mostrarlo, así el texto y los gráficos se ven nítidos en vez de borrosos. En *Auto* elige el tamaño según tu pantalla y baja solo si falta memoria. Incluye el 3D (M3G y Mascot Capsule). Tomado de [J2ME-Loader-HD](https://github.com/fuyijian/J2ME-Loader-HD).
- **Escala entera** (*Pantalla → Tipo de escalado*): cada píxel del juego ocupa exactamente N×N píxeles de la pantalla; la imagen más nítida posible. También hay *16:9*, que ensancha el juego hacia la pantalla sin pasar de 16:9 (llena casi todo y deforma poco).
- **Anti-parpadeo** (*Pantalla*): mezcla cada fotograma con el anterior. Arregla los juegos que hacen transparencias con rayas o puntos que se alternan y en una pantalla moderna parpadean.
- **Ajuste automático** (*Pantalla*): si el juego solo dibuja una parte de la pantalla (por ejemplo 176×208 dentro de 240×320), muestra solo esa parte. Espera unos 12 segundos para no confundirse con un logo.
- **Velocidad 0.25×–16×** (menú del juego → *Velocidad del juego*): acelera el juego o lo pone en cámara lenta. Los juegos instalados antes de esta versión hay que volver a instalarlos.
- **Mando en pantalla** (menú del juego → teclado → *Mando*): cruceta de 8 direcciones, joystick, A B X Y, L y R, Select y Start. Se puede mover, cambiar de tamaño y reasignar con el editor de siempre. Se dibuja con una skin de imágenes PNG; en *Ajustes → Skin del mando* puedes poner la tuya desde una carpeta o un .zip, también skins de Citra.
- **Mandos físicos listos para usar**: Xbox, PlayStation y genéricos funcionan sin configurar (A = OK, B = 7, X = 9, Y = 0, L = \*, R = #, Select/Start = teclas suaves), y el stick izquierdo mueve como la cruceta. Al usar un mando o teclado físico, el teclado en pantalla se esconde y vuelve al tocar la pantalla.
- **Colecciones** (menú principal y menú de cada juego): agrupa tus juegos y muestra solo los de una colección. Incluye *★ Favoritos* y *Recientes* (los últimos 20 que abriste). Quitar un juego de una colección no lo desinstala.
- **Vibración que sí se siente**: en Android 12 o más nuevo (HyperOS, One UI) la vibración se marca como de juego para que el sistema no la silencie.
- **Usa la zona del notch** por defecto (se puede apagar en Ajustes).
- **Widescreen** (*Pantalla → lista de tamaños → Widescreen*): como en JarNix, conserva el alto del juego y le da más espacio a los lados hasta 16:9, 18:9, 19.5:9, 20:9, 21:9 o la proporción de tu teléfono, sin estirar la imagen. Sirve en juegos que se adaptan al tamaño de pantalla; en los de fondo o interfaz fijos pueden quedar zonas vacías.
- **Girar imagen automático**: si casi todo lo que dibuja el juego va de lado, gira la imagen, pone la app en horizontal y lo recuerda. Es la opción por defecto en juegos nuevos.
- **Editor de memoria** (menú del juego): busca un número del juego (vidas, dinero…) por su valor o por cómo cambió, y cámbialo o congélalo. Es una versión sencilla: los resultados son candidatos, prueba cuál es el bueno.
- **Rendimiento en pantalla**: la opción *Mostrar FPS* ahora también muestra la memoria y la CPU.
- **Capturas nítidas**: salen recortadas al ajuste automático, giradas como la pantalla y ampliadas sin borrosidad.
- **Tasa de refresco según el juego** (Android 11+): la pantalla va a 60, 90 o 120 Hz según los FPS del juego, para gastar menos batería.
- **Errores sin cerrar la app**: si un hilo del juego falla, se avisa en pantalla en vez de cerrar todo.
- **Preguntar teclado**: en juegos hechos para pantalla táctil, la primera vez pregunta si mostrar el teclado en pantalla.
- **Exportar e importar un juego** (menú del juego / menú principal): un .zip con el juego, sus ajustes y sus partidas guardadas, para pasarlo a otro teléfono.
- **Cambiar icono** (menú de cada juego): pon la imagen que quieras como icono del juego en la lista, o vuelve al original; el juego no se modifica.
- **Mando físico completo**: L2/R2 (también como gatillos analógicos), L3/R3 y el stick derecho se pueden asignar en *Asignar teclas*, y una tecla del juego puede tener a la vez una tecla de teclado y un botón del mando. La vibración del juego también hace vibrar el mando.
- **Botones de volumen**: dentro del juego cambian el volumen del juego, no el del timbre.
- **Buscar actualizaciones** (menú principal): revisa las versiones publicadas en GitHub y abre la descarga. También puede avisarte al abrir la app (como mucho una vez al día; se apaga en Ajustes).
- **Filtros de pantalla incluidos** (modo gráfico OpenGL ES → *Filtro*): xBR, MMPX, AANN, CRT Lottes, matriz de puntos (de RetroArch) y los de JarNix: sharp bilinear, CRT, scanlines, LCD, LCD verde, color vivo y ámbar. En los efectos, *Suavizar efecto* baja su intensidad. Además de los shaders de PPSSPP (.ini + .fsh), ahora también se pueden usar los .glsl de RetroArch, y se importan desde *Ajustes → Importar filtros de pantalla* (carpeta o .zip).
- **Español completo (ñ, acentos, ¿ ¡)**: el texto de cada juego se lee en la codificación correcta sin configurar nada. Si viene en UTF-8 se lee como UTF-8; si no, como Latin-1 (windows-1252). Así la `ñ` ya no sale como `Ã±` ni desaparece. También el nombre del juego en la biblioteca. Los juegos instalados antes hay que volver a instalarlos. Si el juego dibuja el texto con su propia fuente de imágenes y esa fuente no trae la ñ, eso no se puede arreglar desde el emulador.
- **Teclado estilo JarNix** (por defecto): cruceta con OK a la izquierda, números a la derecha y las teclas suaves arriba, con teclas oscuras redondeadas, borde blanco y letras en negrita. Se puede mover, cambiar de tamaño, ocultar teclas, reasignar y cambiar colores y transparencia como siempre. Los juegos que ya tenían un diseño guardado lo conservan.
- **Cambiar entre mando y teclas dentro del juego**: la tecla *Mando* (en el teclado) o *123* (en el mando) cambia al otro al instante, y también desde el menú del juego → *Teclado virtual* → *Mando ↔ teclas*. Cada uno recuerda su propio diseño, aunque lo hayas editado.
- **Instalar desde ZIP**: elige un .zip y se instalan los juegos que trae dentro. Al instalar varios, si un juego ya existe te pregunta si reemplazarlo u omitirlo (o todos).
- **Rejilla de la biblioteca** (Ajustes): tamaño de los iconos, iconos 3:4 tipo portada y ocultar títulos.
- **Sonido sin tirones**: los sonidos se preparan en su propio hilo, así el juego no se congela cada vez que suena un efecto.
- **Límite de FPS exacto**: 30 FPS dan 30, no ~27.
- **Toques con menos retraso** (Android 11+) y **fondo negro** por defecto en las zonas que el juego no dibuja.
- **Teclas de LG**: además de Siemens, Motorola y Nokia E62, ya se pueden usar los códigos de teclas suaves de LG.
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

## Descargar el APK de prueba

Cada cambio que se sube a GitHub genera un APK de prueba: entra en la pestaña **Actions** del repositorio, abre la última ejecución de *Build test APK* y descarga **J2ME-Infinity-Loader-debug** en *Artifacts*.

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
