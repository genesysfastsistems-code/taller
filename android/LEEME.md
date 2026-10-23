# App Android de KEVANIX Taller

App nativa (WebView) que abre el sistema publicado en https://genesysfastsistems-code.github.io/taller/

- Fotos con la cámara o la galería en la recepción.
- Impresión de facturas, recibos y hojas de OT con el servicio de impresión de Android.
- PDF y respaldos se guardan en Descargas/KEVANIX Taller.
- WhatsApp, teléfono y enlaces externos se abren en su app.
- Botón atrás: cierra ventanas y vuelve a la pantalla anterior.

## Compilar (Ubuntu / Linux)
```
sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk
KS=/ruta/kevanix-taller.keystore KP='clave-del-keystore' ./build.sh
```
Para una versión nueva subí `android:versionCode` en AndroidManifest.xml.
El keystore NO va en el repositorio: guardalo junto con su clave. Sin él no se pueden publicar actualizaciones que se instalen encima.
