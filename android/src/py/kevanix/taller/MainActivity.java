package py.kevanix.taller;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    static final String URL_APP = "https://genesysfastsistems-code.github.io/taller/";
    static final String HOST = "genesysfastsistems-code.github.io";
    static final int REQ_ARCHIVO = 10, REQ_PERMISO = 11;

    WebView web;
    ValueCallback<Uri[]> callbackArchivo;
    Uri fotoCamara;
    String pendienteB64, pendienteNombre, pendienteMime;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#0B0D10"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B0D10"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(hayInternet() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setUserAgentString(s.getUserAgentString() + " KevanixApp/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Puente(), "KevanixApp");
        web.setWebViewClient(new WebViewClient() {
            // API 24+: se llama por firma aunque el android.jar de compilación sea API 23
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return abrirFuera(r.getUrl());
            }
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return abrirFuera(Uri.parse(url));
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) mostrarSinConexion();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (callbackArchivo != null) callbackArchivo.onReceiveValue(null);
                callbackArchivo = cb;
                elegirArchivo(p);
                return true;
            }
        });

        Uri datos = getIntent() != null ? getIntent().getData() : null;
        if (b != null) web.restoreState(b);
        else web.loadUrl(datos != null && HOST.equals(datos.getHost()) ? datos.toString() : URL_APP);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        Uri d = i.getData();
        if (d != null && HOST.equals(d.getHost())) web.loadUrl(d.toString());
    }

    @Override
    protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); web.saveState(b); }

    boolean hayInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo n = cm != null ? cm.getActiveNetworkInfo() : null;
        return n != null && n.isConnected();
    }

    void mostrarSinConexion() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            + "<body style='margin:0;background:#0B0D10;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>"
            + "<div style='padding:24px'><h2>Sin conexión</h2><p style='color:#aab'>Revisá tu internet y volvé a intentar.</p>"
            + "<button onclick=\"location.href='" + URL_APP + "'\" style='background:#E0262E;color:#fff;border:0;border-radius:8px;padding:14px 22px;font-size:16px'>Reintentar</button></div></body></html>";
        web.loadDataWithBaseURL(URL_APP, html, "text/html", "UTF-8", URL_APP);
    }

    // Enlaces de WhatsApp, teléfono, correo y otros sitios: se abren fuera de la app
    boolean abrirFuera(Uri u) {
        String esq = u.getScheme() == null ? "" : u.getScheme();
        if (("https".equals(esq) || "http".equals(esq)) && HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/taller")) return false;
        if ("blob".equals(esq) || "data".equals(esq) || "about".equals(esq) || "javascript".equals(esq)) return false;
        try {
            Intent i;
            if ("intent".equals(esq)) i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
            else i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No hay una app para abrir este enlace", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    // Fotos: cámara o galería
    void elegirArchivo(WebChromeClient.FileChooserParams p) {
        Intent galeria = new Intent(Intent.ACTION_GET_CONTENT);
        galeria.addCategory(Intent.CATEGORY_OPENABLE);
        String[] tipos = p.getAcceptTypes();
        String tipo = (tipos != null && tipos.length > 0 && tipos[0] != null && tipos[0].length() > 0) ? tipos[0] : "*/*";
        galeria.setType(tipo);
        if (p.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) galeria.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        ArrayList<Intent> extra = new ArrayList<Intent>();
        fotoCamara = null;
        if (tipo.startsWith("image") && Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, "kevanix_" + System.currentTimeMillis() + ".jpg");
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put("relative_path", "Pictures/KEVANIX Taller");
                fotoCamara = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, fotoCamara);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (cam.resolveActivity(getPackageManager()) != null) extra.add(cam);
            } catch (Exception e) { fotoCamara = null; }
        }
        Intent chooser = Intent.createChooser(galeria, "Elegir foto");
        if (!extra.isEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toArray(new Intent[0]));
        try { startActivityForResult(chooser, REQ_ARCHIVO); }
        catch (ActivityNotFoundException e) { callbackArchivo.onReceiveValue(null); callbackArchivo = null; }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_ARCHIVO) { super.onActivityResult(req, res, data); return; }
        if (callbackArchivo == null) return;
        Uri[] r = null;
        if (res == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                r = new Uri[n];
                for (int i = 0; i < n; i++) r[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) {
                r = new Uri[]{data.getData()};
            } else if (fotoCamara != null) {
                r = new Uri[]{fotoCamara};
            }
        } else if (fotoCamara != null) {
            try { getContentResolver().delete(fotoCamara, null, null); } catch (Exception e) { }
        }
        callbackArchivo.onReceiveValue(r);
        callbackArchivo = null;
    }

    // Puente con el sistema web: imprimir y guardar PDF
    class Puente {
        @JavascriptInterface
        public void imprimir(final String titulo) {
            runOnUiThread(new Runnable() { public void run() {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                String n = (titulo == null || titulo.length() == 0) ? "KEVANIX Taller" : titulo;
                PrintDocumentAdapter ad = web.createPrintDocumentAdapter(n);
                pm.print(n, ad, new PrintAttributes.Builder().build());
            }});
        }
        @JavascriptInterface
        public void guardarArchivo(String b64, String nombre, String mime) {
            pendienteB64 = b64; pendienteNombre = nombre; pendienteMime = mime;
            runOnUiThread(new Runnable() { public void run() { guardarPendiente(); } });
        }
        @JavascriptInterface
        public String version() { return "1.0"; }
    }

    void guardarPendiente() {
        if (pendienteB64 == null) return;
        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMISO);
            return;
        }
        try {
            byte[] bytes = Base64.decode(pendienteB64, Base64.DEFAULT);
            String nombre = pendienteNombre.replaceAll("[\\\\/:*?\"<>|]", "_");
            Uri uri;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, pendienteMime);
                cv.put("relative_path", "Download/KEVANIX Taller");
                uri = getContentResolver().insert(Uri.parse("content://media/external/downloads"), cv);
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(bytes); os.close();
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KEVANIX Taller");
                dir.mkdirs();
                File f = new File(dir, nombre);
                FileOutputStream os = new FileOutputStream(f);
                os.write(bytes); os.close();
                uri = Uri.fromFile(f);
            }
            Toast.makeText(this, "Guardado en Descargas/KEVANIX Taller: " + nombre, Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 29) {
                Intent ver = new Intent(Intent.ACTION_VIEW);
                ver.setDataAndType(uri, pendienteMime);
                ver.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(ver); } catch (Exception e) { }
            }
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el archivo", Toast.LENGTH_LONG).show();
        }
        pendienteB64 = null;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ_PERMISO) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) guardarPendiente();
            else { pendienteB64 = null; Toast.makeText(this, "Sin permiso para guardar", Toast.LENGTH_SHORT).show(); }
        }
    }

    // Botón atrás: primero cierra ventanas del sistema, después vuelve, y al final sale
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.kvBack&&window.kvBack())?'1':'0'", new ValueCallback<String>() {
            public void onReceiveValue(String v) {
                if (v != null && v.contains("1")) return;
                if (web.canGoBack()) web.goBack(); else MainActivity.super.onBackPressed();
            }
        });
    }

    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
}
