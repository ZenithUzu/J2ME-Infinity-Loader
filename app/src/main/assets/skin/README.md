# Skin del mando en pantalla

Skin por defecto de la distribución "Mando" del teclado virtual, tomada de
[JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit) (paquete "3ds_v2", recortado).

Cada `<nombre>.png` tiene su `<nombre>_pressed.png`: `a`, `b`, `x`, `y`, `l`, `r`, `select`, `start`.
El joystick usa `joystick.png` en reposo; al moverlo, `joystick_base.png` (la base) queda fija y
`joystick_pressed.png` (la palanca) se mueve. En Citra son `stick_main_range.png`, `stick_main.png`
y `stick_main_pressed.png`.
La cruceta usa `dpad.png` en reposo y una imagen por dirección (`dpad_up.png`, `dpad_down_left.png`...),
o al estilo Citra `dpad_pressed_one.png` (arriba) y `dpad_pressed_two.png` (arriba + izquierda), que se giran.

Para usar otra skin no hace falta tocar esto: en Ajustes → Skin del mando, elige una carpeta con los PNG.
También se aceptan los nombres de Citra (`button_a.png`, `dpad_pressed_one_direction.png`...).
