package br.baixartiktok.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baixar TikTok: recebe o link de um vídeo (pelo "Compartilhar" do TikTok, colando ou
 * copiando) e salva o vídeo na galeria, na melhor qualidade e sem marca d'água.
 *
 * Como funciona: o vídeo é aberto num WebView, como num navegador comum. O assets/extrair.js
 * lê da própria página os endereços do vídeo, e o app baixa o melhor deles com os mesmos
 * cookies da página. Se o TikTok mostrar alguma verificação, a página aparece na tela para
 * a pessoa resolver.
 */
public class MainActivity extends Activity {

    // Mesmas regras do programa do computador (baixar_tiktok.py).
    static final Pattern LINK_TIKTOK = Pattern.compile(
            "https?://(?:[\\w-]+\\.)*tiktok\\.com/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    static final Pattern LINK_VIDEO = Pattern.compile(
            "https?://(?:(?:www\\.|m\\.)?tiktok\\.com/(?:@[\\w.-]*/)?video/\\d+"
                    + "|m\\.tiktok\\.com/v/\\d+"
                    + "|(?:vm|vt)\\.tiktok\\.com/\\w+"
                    + "|(?:www\\.)?tiktok\\.com/t/\\w+)",
            Pattern.CASE_INSENSITIVE);

    static final String PASTA = "Movies/TikTok";
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final long MOSTRAR_PAGINA_APOS = 15_000;   // ms sem achar o vídeo: mostra a página
    static final long DESISTIR_APOS = 120_000;

    // Cores do tema claro ou escuro, definidas em definirCores().
    int FUNDO, CARTAO, CAMPO, TEXTO, CINZA, VERDE, VERMELHO, DESTAQUE;

    final Handler h = new Handler(Looper.getMainLooper());
    final ArrayDeque<Pedido> fila = new ArrayDeque<>();
    final Set<String> pedidos = new HashSet<>();
    Pedido atual;
    String scriptExtrair;
    SharedPreferences prefs;

    EditText campo;
    LinearLayout lista;
    LinearLayout caixaPagina;
    TextView avisoPagina;
    WebView web;
    View vazio;

    static class Pedido {
        String link, chave;
        long inicio;
        boolean baixando, paginaVisivel;
        Uri arquivo;
        TextView titulo, situacao, capaIcone;
        ImageView capa;
        ProgressBar barra;
    }

    // ------------------------------------------------------------------ tela

    @Override
    protected void onCreate(Bundle salvo) {
        super.onCreate(salvo);
        prefs = getSharedPreferences("baixar", MODE_PRIVATE);
        scriptExtrair = "(function(){\n" + lerAsset("extrair.js")
                + "\nreturn JSON.stringify(extrair(document));\n})()";
        definirCores();
        montarTela();
        receberIntent(getIntent());
    }

    void definirCores() {
        boolean escuro = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        FUNDO = escuro ? 0xFF141216 : 0xFFFFF6F7;
        CARTAO = escuro ? 0xFF1F1C22 : 0xFFFFFFFF;
        CAMPO = escuro ? 0xFF2B272E : 0xFFF7EFF1;
        TEXTO = escuro ? 0xFFF3EEF1 : 0xFF1D1B20;
        CINZA = escuro ? 0xFFADA6AF : 0xFF6F6A72;
        VERDE = escuro ? 0xFF6BD68A : 0xFF1E8E3E;
        VERMELHO = escuro ? 0xFFFF8A8A : 0xFFD32F2F;
        DESTAQUE = 0xFFE5304A; // o vermelho do gorro do ícone
    }

    void montarTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(dp(20), dp(20), dp(20), 0);
        raiz.setBackgroundColor(FUNDO);

        // Cabeçalho: a foto do ícone e o nome do app.
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(forma(GradientDrawable.OVAL, CARTAO, 0));
        avatar.setClipToOutline(true);
        avatar.setElevation(dp(3));
        ImageView foto = new ImageView(this);
        foto.setImageResource(R.mipmap.ic_launcher_foto);
        foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        foto.setScaleX(1.5f); // mostra só o centro da foto, como no ícone
        foto.setScaleY(1.5f);
        avatar.addView(foto, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout.LayoutParams lpAvatar = new LinearLayout.LayoutParams(dp(56), dp(56));
        lpAvatar.setMargins(dp(2), dp(2), dp(2), dp(2));
        topo.addView(avatar, lpAvatar);
        LinearLayout nomes = new LinearLayout(this);
        nomes.setOrientation(LinearLayout.VERTICAL);
        nomes.setPadding(dp(14), 0, 0, 0);
        TextView titulo = texto("Baixar TikTok", 24, TEXTO);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        nomes.addView(titulo);
        nomes.addView(texto("Qualidade máxima, sem marca d'água", 13, CINZA));
        topo.addView(nomes);
        raiz.addView(topo);

        // Cartão do link.
        LinearLayout cartaoLink = cartaoBase(20);
        cartaoLink.setPadding(dp(16), dp(16), dp(16), dp(14));
        TextView rotulo = texto("Link do vídeo", 13, CINZA);
        rotulo.setTypeface(Typeface.DEFAULT_BOLD);
        rotulo.setPadding(dp(2), 0, 0, dp(8));
        cartaoLink.addView(rotulo);
        campo = new EditText(this);
        campo.setHint("https://vm.tiktok.com/...");
        campo.setHintTextColor(CINZA);
        campo.setTextColor(TEXTO);
        campo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        campo.setBackground(forma(GradientDrawable.RECTANGLE, CAMPO, 14));
        campo.setPadding(dp(14), dp(12), dp(14), dp(12));
        campo.setSingleLine(true);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        campo.setImeOptions(EditorInfo.IME_ACTION_GO);
        campo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int acao, android.view.KeyEvent e) {
                pegarDoCampo(true);
                return true;
            }
        });
        campo.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (!acharLinks(s.toString()).isEmpty()) {
                    h.post(new Runnable() { public void run() { pegarDoCampo(false); } });
                }
            }
        });
        cartaoLink.addView(campo, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button colar = botao("Colar e baixar");
        colar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { colarEBaixar(); }
        });
        cartaoLink.addView(colar);
        TextView ou = texto("ou, no TikTok: Compartilhar → Baixar TikTok", 13, CINZA);
        ou.setGravity(Gravity.CENTER_HORIZONTAL);
        ou.setPadding(0, dp(10), 0, 0);
        cartaoLink.addView(ou, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lpLink = new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpLink.topMargin = dp(20);
        raiz.addView(cartaoLink, lpLink);

        // Página do TikTok. Fica com 1 px enquanto tudo vai bem; cresce se precisar de verificação.
        caixaPagina = new LinearLayout(this);
        caixaPagina.setOrientation(LinearLayout.VERTICAL);
        avisoPagina = texto("", 14, TEXTO);
        avisoPagina.setVisibility(View.GONE);
        avisoPagina.setBackground(forma(GradientDrawable.RECTANGLE, CAMPO, 14));
        avisoPagina.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpAviso = new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpAviso.setMargins(0, dp(14), 0, dp(8));
        caixaPagina.addView(avisoPagina, lpAviso);
        web = new WebView(this);
        configurarWeb();
        caixaPagina.addView(web, new LinearLayout.LayoutParams(MATCH, 1));
        raiz.addView(caixaPagina);

        TextView secao = texto("Seus downloads", 17, TEXTO);
        secao.setTypeface(Typeface.DEFAULT_BOLD);
        secao.setPadding(dp(2), dp(20), 0, dp(10));
        raiz.addView(secao);

        ScrollView rolagem = new ScrollView(this);
        rolagem.setClipToPadding(false);
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(dp(2), dp(2), dp(2), dp(24)); // espaço para a sombra dos cartões
        vazio = estadoVazio();
        lista.addView(vazio);
        rolagem.addView(lista);
        raiz.addView(rolagem, new LinearLayout.LayoutParams(MATCH, 0, 1));

        setContentView(raiz);
    }

    View estadoVazio() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(dp(24), dp(28), dp(24), 0);
        TextView emoji = texto("🎬", 40, TEXTO);
        v.addView(emoji);
        TextView t = texto("Nenhum vídeo ainda", 16, TEXTO);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(8), 0, dp(4));
        v.addView(t);
        TextView d = texto("Copie o link de um vídeo no TikTok e volte aqui: o download começa sozinho. "
                + "Os vídeos ficam na galeria, na pasta " + PASTA + ". Toque num vídeo da lista para assistir.",
                14, CINZA);
        d.setGravity(Gravity.CENTER_HORIZONTAL);
        v.addView(d);
        return v;
    }

    void configurarWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true); // não sai som do vídeo enquanto procura
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest pedido) {
                // Só páginas da web; links "abrir no app" (intent://, snssdk://) ficam de fora.
                String esquema = pedido.getUrl().getScheme();
                return !"http".equals(esquema) && !"https".equals(esquema);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (atual != null && !atual.baixando) {
                    h.removeCallbacks(procurar);
                    h.postDelayed(procurar, 500);
                }
            }
        });
    }

    View cartao(Pedido p) {
        LinearLayout c = cartaoBase(18);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), dp(10), dp(14), dp(10));
        c.setForeground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null,
                forma(GradientDrawable.RECTANGLE, Color.WHITE, 18)));

        // Miniatura do vídeo (a capa chega junto com o download).
        FrameLayout moldura = new FrameLayout(this);
        moldura.setBackground(forma(GradientDrawable.RECTANGLE, CAMPO, 12));
        moldura.setClipToOutline(true);
        p.capaIcone = texto("▶", 20, CINZA);
        p.capaIcone.setGravity(Gravity.CENTER);
        moldura.addView(p.capaIcone, new FrameLayout.LayoutParams(MATCH, MATCH));
        p.capa = new ImageView(this);
        p.capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        moldura.addView(p.capa, new FrameLayout.LayoutParams(MATCH, MATCH));
        c.addView(moldura, new LinearLayout.LayoutParams(dp(60), dp(80)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        p.titulo = texto(p.link, 15, TEXTO);
        p.titulo.setTypeface(Typeface.DEFAULT_BOLD);
        p.titulo.setMaxLines(2);
        p.titulo.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(p.titulo);
        p.situacao = texto("", 13, CINZA);
        p.situacao.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams lpSit = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSit.topMargin = dp(8);
        info.addView(p.situacao, lpSit);
        p.barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.barra.setMax(100);
        p.barra.setIndeterminate(true);
        p.barra.setProgressTintList(ColorStateList.valueOf(DESTAQUE));
        p.barra.setIndeterminateTintList(ColorStateList.valueOf(DESTAQUE));
        p.barra.setProgressBackgroundTintList(ColorStateList.valueOf(CAMPO));
        p.barra.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpBarra = new LinearLayout.LayoutParams(MATCH, dp(6));
        lpBarra.topMargin = dp(8);
        info.addView(p.barra, lpBarra);
        c.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        situacao(p, "Na fila", CINZA);

        final Pedido pedido = p;
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { abrirVideo(pedido); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    LinearLayout cartaoBase(int raio) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(forma(GradientDrawable.RECTANGLE, CARTAO, raio));
        c.setElevation(dp(2));
        return c;
    }

    GradientDrawable forma(int tipo, int cor, int raio) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(tipo);
        g.setColor(cor);
        if (tipo == GradientDrawable.RECTANGLE) g.setCornerRadius(dp(raio));
        return g;
    }

    // ------------------------------------------------------------------ receber links

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receberIntent(intent);
    }

    void receberIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String texto = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (texto == null) texto = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (texto == null || acharLinks(texto).isEmpty()) {
            Toast.makeText(this, "Não achei um link do TikTok no que foi compartilhado.", Toast.LENGTH_LONG).show();
            return;
        }
        lembrarClipe(texto); // se o mesmo link também estiver copiado, não baixa duas vezes
        receberTexto(texto, false);
    }

    @Override
    public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        // No Android 10+ só dá para ler a área de transferência com o app em foco.
        if (foco) olharAreaDeTransferencia();
    }

    void olharAreaDeTransferencia() {
        String texto = lerAreaDeTransferencia();
        if (texto.isEmpty() || texto.equals(prefs.getString("ultimo_clipe", ""))) return;
        lembrarClipe(texto);
        receberTexto(texto, true);
    }

    void lembrarClipe(String texto) {
        prefs.edit().putString("ultimo_clipe", texto).apply();
    }

    void colarEBaixar() {
        String texto = lerAreaDeTransferencia();
        if (acharLinks(texto).isEmpty()) {
            Toast.makeText(this, "Nenhum link do TikTok copiado. No TikTok: Compartilhar > Copiar link.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        lembrarClipe(texto);
        if (receberTexto(texto, false) == 0) {
            Toast.makeText(this, "Esse vídeo já está na lista.", Toast.LENGTH_SHORT).show();
        }
    }

    void pegarDoCampo(boolean avisar) {
        String texto = campo.getText().toString();
        if (!acharLinks(texto).isEmpty()) {
            campo.setText("");
            receberTexto(texto, false);
        } else if (avisar && !texto.trim().isEmpty()) {
            Toast.makeText(this, "Isso não parece um link do TikTok.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Põe na fila os links de vídeo do texto. Devolve quantos entraram. */
    int receberTexto(String texto, boolean soVideos) {
        int novos = 0;
        for (String link : acharLinks(texto)) {
            if (!LINK_VIDEO.matcher(link).lookingAt()) {
                if (!soVideos) {
                    Pedido p = novoCartao(link);
                    erro(p, "Não é link de vídeo (perfil, hashtag ou fotos).");
                }
                continue;
            }
            String chave = chaveDoLink(link);
            if (!pedidos.add(chave)) continue;
            Pedido p = novoCartao(link);
            p.chave = chave;
            fila.add(p);
            novos++;
        }
        processarFila();
        return novos;
    }

    Pedido novoCartao(String link) {
        Pedido p = new Pedido();
        p.link = link;
        vazio.setVisibility(View.GONE);
        lista.addView(cartao(p), 0);
        return p;
    }

    static List<String> acharLinks(String texto) {
        List<String> links = new ArrayList<>();
        if (texto == null) return links;
        Matcher m = LINK_TIKTOK.matcher(texto);
        while (m.find()) {
            String link = m.group().replaceAll("[.,;:!?)\\]}]+$", "");
            if (!links.contains(link)) links.add(link);
        }
        return links;
    }

    static String chaveDoLink(String link) {
        String s = link.split("[?#]", 2)[0];
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.toLowerCase(Locale.ROOT);
    }

    String lerAreaDeTransferencia() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence t = clip.getItemAt(0).coerceToText(this);
            return t == null ? "" : t.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ achar o vídeo na página

    void processarFila() {
        if (atual != null || fila.isEmpty()) return;
        atual = fila.poll();
        atual.inicio = SystemClock.uptimeMillis();
        situacao(atual, "Abrindo o vídeo...", CINZA);
        atual.barra.setVisibility(View.VISIBLE);
        atual.barra.setIndeterminate(true);
        web.loadUrl(atual.link);
        h.removeCallbacks(procurar);
        h.postDelayed(procurar, 2000);
    }

    final Runnable procurar = new Runnable() {
        public void run() {
            final Pedido p = atual;
            if (p == null || p.baixando) return;
            web.evaluateJavascript(scriptExtrair, new ValueCallback<String>() {
                public void onReceiveValue(String valor) { tratar(p, valor); }
            });
        }
    };

    void tratar(Pedido p, String valor) {
        if (p != atual || p.baixando) return;
        JSONObject r = null;
        try {
            Object o = new JSONTokener(valor == null ? "null" : valor).nextValue();
            if (o instanceof String) {
                Object dentro = new JSONTokener((String) o).nextValue();
                if (dentro instanceof JSONObject) r = (JSONObject) dentro;
            }
        } catch (Exception ignorado) {
            // página ainda montando
        }

        String url = web.getUrl() == null ? "" : web.getUrl();
        if (r == null && url.contains("tiktok.com/login")) {
            terminarComErro(p, "O TikTok pediu login para este vídeo.");
            return;
        }
        if (r != null && r.has("erro")) {
            String e = r.optString("erro");
            int status = r.optInt("status");
            if ("fotos".equals(e)) terminarComErro(p, "Esse post é de fotos, não de vídeo.");
            else if (status == 10216 || status == 10222) terminarComErro(p, "Vídeo privado: só o dono consegue ver.");
            else if (status == 10204) terminarComErro(p, "O TikTok não liberou este vídeo para esta conexão.");
            else terminarComErro(p, "Vídeo não encontrado: pode ter sido apagado (código " + status + ").");
            return;
        }
        if (r != null && (tamanho(r, "urls") > 0 || tamanho(r, "marca") > 0)) {
            esconderPagina();
            comecarDownload(p, r, url);
            return;
        }

        long passou = SystemClock.uptimeMillis() - p.inicio;
        if (passou > DESISTIR_APOS) {
            terminarComErro(p, "Não consegui achar o vídeo nessa página. Tente de novo mais tarde.");
            return;
        }
        if (passou > MOSTRAR_PAGINA_APOS && !p.paginaVisivel) {
            p.paginaVisivel = true;
            mostrarPagina("O vídeo está demorando. Se o TikTok mostrar uma verificação ou um botão "
                    + "aqui embaixo, resolva que o download continua sozinho.");
        }
        h.postDelayed(procurar, 1000);
    }

    void mostrarPagina(String aviso) {
        avisoPagina.setText(aviso);
        avisoPagina.setVisibility(View.VISIBLE);
        web.getLayoutParams().height = dp(380);
        web.requestLayout();
    }

    void esconderPagina() {
        avisoPagina.setVisibility(View.GONE);
        web.getLayoutParams().height = 1;
        web.requestLayout();
    }

    void terminarComErro(Pedido p, String msg) {
        h.removeCallbacks(procurar);
        esconderPagina();
        pedidos.remove(p.chave); // deixa tentar de novo
        erro(p, msg);
        proximo();
    }

    void proximo() {
        atual = null;
        web.stopLoading();
        web.loadUrl("about:blank");
        processarFila();
    }

    // ------------------------------------------------------------------ baixar

    void comecarDownload(final Pedido p, JSONObject r, final String pagina) {
        p.baixando = true;
        h.removeCallbacks(procurar);

        final List<String> urls = new ArrayList<>();
        final List<String> cookies = new ArrayList<>();
        JSONArray boas = r.optJSONArray("urls");
        JSONArray comMarca = r.optJSONArray("marca");
        if (boas == null) boas = new JSONArray();
        if (comMarca == null) comMarca = new JSONArray();
        final int semMarca = boas.length();
        for (int i = 0; i < boas.length(); i++) urls.add(boas.optString(i));
        for (int i = 0; i < comMarca.length(); i++) urls.add(comMarca.optString(i));
        CookieManager cm = CookieManager.getInstance();
        for (String u : urls) cookies.add(cm.getCookie(u));

        final String agente = web.getSettings().getUserAgentString();
        final String id = r.optString("id");
        final String nome = nomeDoArquivo(r.optString("autor"), r.optString("titulo"), id);
        final String qualidade = qualidade(r.optInt("largura"), r.optInt("altura"));
        String desc = r.optString("titulo").trim();
        p.titulo.setText(desc.isEmpty() ? nome : desc);
        carregarCapa(p, r.optString("capa"), agente, pagina);

        // A página já fez o trabalho dela; libera o WebView para o próximo da fila.
        atual = null;
        web.loadUrl("about:blank");

        Uri existente = id.isEmpty() ? null : acharJaBaixado(id);
        if (existente != null) {
            p.arquivo = existente;
            pronto(p, "✓ Já estava salvo");
            processarFila();
            return;
        }

        situacao(p, "Baixando" + (qualidade.isEmpty() ? "" : " (" + qualidade + ")") + "...", CINZA);
        new Thread(new Runnable() {
            public void run() {
                String falha = "não consegui baixar o arquivo do vídeo.";
                for (int i = 0; i < urls.size(); i++) {
                    try {
                        Uri uri = baixarArquivo(p, urls.get(i), cookies.get(i), agente, pagina, nome);
                        if (uri != null) {
                            final boolean marca = i >= semMarca;
                            p.arquivo = uri;
                            final String q = qualidade;
                            h.post(new Runnable() {
                                public void run() {
                                    pronto(p, marca ? "✓ Salvo, mas só achei com marca d'água"
                                            : "✓ Salvo" + (q.isEmpty() ? "" : " · " + q));
                                    Toast.makeText(MainActivity.this, "Vídeo salvo na galeria",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            fimDoDownload();
                            return;
                        }
                    } catch (IOException e) {
                        falha = "sem conexão com o TikTok. Confira a internet.";
                    } catch (Exception e) {
                        falha = "não consegui salvar o vídeo na galeria.";
                    }
                }
                final String msg = falha;
                h.post(new Runnable() {
                    public void run() {
                        pedidos.remove(p.chave);
                        erro(p, "Erro: " + msg + " Toque em Colar e baixar para tentar de novo.");
                    }
                });
                fimDoDownload();
            }
        }).start();
        processarFila();
    }

    void fimDoDownload() {
        h.post(new Runnable() { public void run() { processarFila(); } });
    }

    /** Baixa de um endereço direto para a galeria. Devolve null se esse endereço não serviu. */
    Uri baixarArquivo(final Pedido p, String endereco, String cookie, String agente, String pagina,
                      String nome) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(endereco).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", agente);
        c.setRequestProperty("Referer", pagina.isEmpty() ? "https://www.tiktok.com/" : pagina);
        c.setRequestProperty("Accept", "*/*");
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        try {
            int codigo = c.getResponseCode();
            String tipo = String.valueOf(c.getContentType()).toLowerCase(Locale.ROOT);
            if (codigo / 100 != 2 || tipo.startsWith("text/") || tipo.contains("json")) return null;
            final long total = c.getContentLengthLong();

            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, nome);
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH, PASTA);
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("galeria recusou o arquivo");

            long feito = 0;
            boolean ok = false;
            try (InputStream in = c.getInputStream(); OutputStream out = cr.openOutputStream(uri)) {
                byte[] buf = new byte[64 * 1024];
                long ultimoAviso = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    feito += n;
                    long agora = SystemClock.uptimeMillis();
                    if (agora - ultimoAviso > 250) {
                        ultimoAviso = agora;
                        final long f = feito;
                        h.post(new Runnable() { public void run() { andamento(p, f, total); } });
                    }
                }
                ok = feito > 20_000 && (total <= 0 || feito >= total);
            } finally {
                if (ok) {
                    ContentValues fim = new ContentValues();
                    fim.put(MediaStore.Video.Media.IS_PENDING, 0);
                    cr.update(uri, fim, null, null);
                } else {
                    cr.delete(uri, null, null);
                }
            }
            return ok ? uri : null;
        } finally {
            c.disconnect();
        }
    }

    /** Miniatura do vídeo no cartão. Se não vier, fica o ▶. */
    void carregarCapa(final Pedido p, final String endereco, final String agente, final String pagina) {
        if (endereco == null || !endereco.startsWith("http")) return;
        final String cookie = CookieManager.getInstance().getCookie(endereco);
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(endereco).openConnection();
                    c.setConnectTimeout(15_000);
                    c.setReadTimeout(15_000);
                    c.setRequestProperty("User-Agent", agente);
                    c.setRequestProperty("Referer", pagina.isEmpty() ? "https://www.tiktok.com/" : pagina);
                    if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
                    if (c.getResponseCode() / 100 != 2) return;
                    Bitmap b;
                    try (InputStream in = c.getInputStream()) {
                        b = BitmapFactory.decodeStream(in);
                    }
                    if (b == null) return;
                    if (b.getWidth() > 360) {
                        b = Bitmap.createScaledBitmap(b, 360, Math.max(1, b.getHeight() * 360 / b.getWidth()), true);
                    }
                    final Bitmap img = b;
                    h.post(new Runnable() {
                        public void run() {
                            p.capa.setImageBitmap(img);
                            p.capaIcone.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception ignorado) {
                    // sem miniatura, sem problema
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    Uri acharJaBaixado(String id) {
        String[] col = {MediaStore.Video.Media._ID};
        try (Cursor cur = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, col,
                MediaStore.Video.Media.DISPLAY_NAME + " LIKE ? AND " + MediaStore.Video.Media.IS_PENDING + "=0",
                new String[]{"%[" + id + "].mp4"}, null)) {
            if (cur != null && cur.moveToFirst()) {
                return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cur.getString(0));
            }
        } catch (Exception ignorado) {
            // sem acesso: baixa de novo
        }
        return null;
    }

    static int tamanho(JSONObject r, String campo) {
        JSONArray a = r.optJSONArray(campo);
        return a == null ? 0 : a.length();
    }

    static String nomeDoArquivo(String autor, String titulo, String id) {
        String t = titulo.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        if (t.length() > 60) t = t.substring(0, 60).trim();
        String a = autor.replaceAll("[^\\w.-]+", "").trim();
        StringBuilder s = new StringBuilder(a.isEmpty() ? "tiktok" : a);
        if (!t.isEmpty()) s.append(" - ").append(t);
        s.append(" [").append(id.isEmpty() ? String.valueOf(System.currentTimeMillis()) : id).append("].mp4");
        return s.toString();
    }

    static String qualidade(int largura, int altura) {
        int menor = largura > 0 && altura > 0 ? Math.min(largura, altura) : Math.max(largura, altura);
        return menor > 0 ? menor + "p" : "";
    }

    // ------------------------------------------------------------------ estado dos cartões

    void andamento(Pedido p, long feito, long total) {
        if (total > 0) {
            int pct = (int) Math.min(99, feito * 100 / total);
            p.barra.setIndeterminate(false);
            p.barra.setProgress(pct);
            situacao(p, String.format(Locale.ROOT, "Baixando %d%% (%.1f MB)", pct, total / 1048576.0), CINZA);
        } else {
            situacao(p, String.format(Locale.ROOT, "Baixando %.1f MB", feito / 1048576.0), CINZA);
        }
    }

    void pronto(Pedido p, String msg) {
        p.barra.setVisibility(View.GONE);
        situacao(p, msg, VERDE);
    }

    void erro(Pedido p, String msg) {
        if (p.barra != null) p.barra.setVisibility(View.GONE);
        situacao(p, msg, VERMELHO);
    }

    void situacao(Pedido p, String msg, int cor) {
        p.situacao.setText(msg);
        p.situacao.setTextColor(cor);
        // "Etiqueta" com a mesma cor, bem clarinha, atrás do texto.
        p.situacao.setBackground(forma(GradientDrawable.RECTANGLE, (cor & 0x00FFFFFF) | 0x1F000000, 20));
    }

    void abrirVideo(Pedido p) {
        if (p.arquivo == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(p.arquivo, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Abra pela galeria, na pasta " + PASTA + ".", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ utilidades

    String lerAsset(String nome) {
        try (InputStream in = getAssets().open(nome)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    TextView texto(String s, int sp, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        return t;
    }

    Button botao(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF),
                forma(GradientDrawable.RECTANGLE, DESTAQUE, 26), null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(52));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }
}
