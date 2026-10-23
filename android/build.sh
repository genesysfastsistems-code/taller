#!/bin/bash
# Compila el APK de KEVANIX Taller con las herramientas de Android de Ubuntu (sin Gradle).
set -euo pipefail
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
KS=${KS:-kevanix-taller.keystore}; KP=${KP:?Falta KP (clave del keystore)}
rm -rf build && mkdir -p build/gen build/obj build/dex
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$SDK"
javac -source 8 -target 8 -nowarn -encoding UTF-8 -bootclasspath "$SDK" -classpath "$SDK" -d build/obj build/gen/py/kevanix/taller/R.java src/py/kevanix/taller/*.java
dalvik-exchange --dex --min-sdk-version=21 --output=build/dex/classes.dex build/obj
aapt package -f -M AndroidManifest.xml -S res -I "$SDK" -F build/sin-firmar.apk
(cd build/dex && aapt add ../sin-firmar.apk classes.dex)
zipalign -f -p 4 build/sin-firmar.apk build/alineado.apk
apksigner sign --ks "$KS" --ks-pass pass:"$KP" --key-pass pass:"$KP" --ks-key-alias kevanix --out build/KEVANIX-Taller.apk build/alineado.apk
apksigner verify --print-certs build/KEVANIX-Taller.apk | head -3
ls -la build/KEVANIX-Taller.apk
