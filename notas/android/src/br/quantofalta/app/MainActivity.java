package br.quantofalta.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Abre a página do Quanto Falta, que fica dentro do próprio app. As notas ficam salvas no aparelho. */
public class MainActivity extends Activity {
    private static final String PAGINA = "file:///android_asset/index.html";
    private WebView web;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        web = new WebView(this);
        web.setBackgroundColor(getResources().getColor(R.color.fundo, getTheme()));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("file:///android_asset/")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignorado) {
                    // Sem app para abrir o link: ignora.
                }
                return true;
            }
        });

        setContentView(web);
        if (estado == null || web.restoreState(estado) == null) web.loadUrl(PAGINA);
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    protected void onPause() {
        // Grava na hora qualquer nota que ainda esteja esperando.
        web.evaluateJavascript("window.notasSalvarAgora && window.notasSalvarAgora()", null);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
