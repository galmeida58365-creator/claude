package br.cofrinho.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

/** Abre a página do Cofrinho, que fica dentro do próprio app. */
public class MainActivity extends Activity {
    private static final String PAGINA = "file:///android_asset/index.html";
    private static final int ESCOLHER_FOTO = 1;
    private WebView web;
    private ValueCallback<Uri[]> escolha;
    private String compartilhado;
    private boolean carregada;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        web = new WebView(this);
        web.setBackgroundColor(getResources().getColor(R.color.fundo, getTheme()));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // A página é um arquivo local; isto deixa ela ler a página da loja e falar com o Firebase.
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setUserAgentString(s.getUserAgentString() + " CofrinhoApp");

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

            @Override
            public void onPageStarted(WebView v, String url, Bitmap icone) {
                carregada = false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                carregada = true;
                entregarCompartilhado();
            }
        });

        // Botão "Escolher foto" da página: abre a galeria do celular.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (escolha != null) escolha.onReceiveValue(null);
                escolha = cb;
                try {
                    startActivityForResult(p.createIntent(), ESCOLHER_FOTO);
                } catch (Exception e) {
                    escolha = null;
                    return false;
                }
                return true;
            }
        });

        setContentView(web);
        compartilhado = textoCompartilhado(getIntent());
        if (estado == null || web.restoreState(estado) == null) web.loadUrl(PAGINA);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String t = textoCompartilhado(i);
        if (t != null) {
            compartilhado = t;
            entregarCompartilhado();
        }
    }

    private static String textoCompartilhado(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return null;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        return t == null ? null : t.toString();
    }

    /** Entrega para a página o link compartilhado de outro app (abre "Nova meta" com ele). */
    private void entregarCompartilhado() {
        if (!carregada || compartilhado == null) return;
        String js = "window.cofrinhoCompartilhado && window.cofrinhoCompartilhado(" + JSONObject.quote(compartilhado) + ")";
        compartilhado = null;
        web.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        if (pedido == ESCOLHER_FOTO && escolha != null) {
            escolha.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, dados));
            escolha = null;
            return;
        }
        super.onActivityResult(pedido, resultado, dados);
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
