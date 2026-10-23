#!/usr/bin/env python3
"""Descarga el padrón de RUC publicado por la DNIT (ruc0.zip … ruc9.zip) y arma un
índice en archivos JSON pequeños para que el sistema busque un RUC sin servidor.

Salida: <dir>/000.json … <dir>/999.json  →  {"4521378": ["APELLIDO, NOMBRE", "6"], "80034521": ["EMPRESA SA", "1", "CANCELADO"]}
        <dir>/meta.json                   →  {"fecha": "...", "total": N}
El archivo se elige por los últimos 3 dígitos del RUC (sin DV). Estado ACTIVO se omite para ahorrar espacio.
Si la DNIT no responde, se conserva el índice anterior (si existe) y el sistema se publica igual.
"""
import io, json, os, re, sys, time, zipfile, datetime, urllib.request

PAGINA = "https://www.dnit.gov.py/web/portal-institucional/listado-de-ruc-con-sus-equivalencias"
BASE = "https://www.dnit.gov.py"
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"}
DIAS_VIGENCIA = 25  # si el índice tiene menos días, no se vuelve a descargar (salvo FORZAR=1)

def get(url, intentos=3):
    for i in range(intentos):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
                return r.read()
        except Exception as e:
            print(f"  intento {i+1} falló: {e}")
            time.sleep(5 * (i + 1))
    raise RuntimeError(f"No se pudo descargar {url}")

def enlaces():
    html = get(PAGINA).decode("utf-8", "ignore")
    urls = {}
    for m in re.finditer(r'href="([^"]*?/(ruc(\d))\.zip[^"]*)"', html, re.I):
        u = m.group(1).replace("&amp;", "&")
        urls[m.group(3)] = u if u.startswith("http") else BASE + u
    if len(urls) < 10:
        raise RuntimeError(f"Se encontraron {len(urls)} archivos en la página de la DNIT (se esperaban 10)")
    return [urls[k] for k in sorted(urls)]

def texto(b):
    for enc in ("utf-8", "cp1252", "latin-1"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            pass
    return b.decode("latin-1", "ignore")

def filas(txt):
    muestra = txt[:5000]
    sep = max(["|", ";", "\t"], key=muestra.count)
    for linea in txt.splitlines():
        p = [x.strip() for x in linea.split(sep)]
        if len(p) < 3 or not p[0].isdigit():
            continue
        ruc, nombre = str(int(p[0])), p[1]
        dv = p[2] if p[2].isdigit() and len(p[2]) == 1 else ""
        estado = ""
        for x in p[3:]:
            if x and re.fullmatch(r"[A-ZÁÉÍÓÚÑ ]{4,}", x.upper()) and not re.search(r"\d", x):
                estado = x.upper()
        if nombre and dv:
            yield ruc, nombre, dv, estado

def main(destino):
    forzar = os.environ.get("FORZAR") == "1"
    meta_p = os.path.join(destino, "meta.json")
    if os.path.exists(meta_p) and not forzar:
        try:
            meta = json.load(open(meta_p))
            dias = (datetime.date.today() - datetime.date.fromisoformat(meta["fecha"][:10])).days
            if dias < DIAS_VIGENCIA:
                print(f"Índice RUC vigente ({meta['total']} registros, {dias} días). No se descarga.")
                return
        except Exception:
            pass
    try:
        urls = enlaces()
        shards, total = {}, 0
        for u in urls:
            print("Descargando", u.split("?")[0])
            z = zipfile.ZipFile(io.BytesIO(get(u)))
            for n in z.namelist():
                for ruc, nombre, dv, estado in filas(texto(z.read(n))):
                    reg = [nombre, dv] + ([estado] if estado and estado != "ACTIVO" else [])
                    shards.setdefault(int(ruc) % 1000, {})[ruc] = reg
                    total += 1
        if total < 100000:
            raise RuntimeError(f"Solo se leyeron {total} registros; el formato pudo haber cambiado")
    except Exception as e:
        print("::warning::Padrón DNIT no actualizado:", e)
        if os.path.exists(meta_p):
            print("Se mantiene el índice anterior.")
        return
    os.makedirs(destino, exist_ok=True)
    for k in range(1000):
        with open(os.path.join(destino, f"{k:03d}.json"), "w", encoding="utf-8") as f:
            json.dump(shards.get(k, {}), f, ensure_ascii=False, separators=(",", ":"))
    json.dump({"fecha": datetime.datetime.utcnow().isoformat() + "Z", "total": total, "fuente": PAGINA}, open(meta_p, "w"))
    print(f"Índice RUC listo: {total} registros en 1000 archivos.")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "_site/ruc")
