PLACEHOLDER

The real launcher icon files (ic_launcher.png and ic_launcher_round.png) should be placed in:
- mipmap-mdpi/
- mipmap-hdpi/
- mipmap-xhdpi/
- mipmap-xxhdpi/
- mipmap-xxxhdpi/

The preferred modern approach is the adaptive icon defined in:
mipmap-anydpi-v26/ic_launcher.xml and ic_launcher_round.xml

Use Android Studio → New → Image Asset to generate proper icons from a vector or image.

For now the adaptive icon will be used on Android 8.0+.
