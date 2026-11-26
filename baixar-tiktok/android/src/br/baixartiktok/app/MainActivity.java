package br.baixartiktok.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaMetadataRetriever;
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
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
 *
 * A tela segue a linha da aba de downloads do YouTube: fundo liso, barra de link
 * arredondada e uma lista com miniatura, duração e o menu ⋮.
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
    // "autor - título [id].mp4", o nome que o app dá aos arquivos.
    static final Pattern NOME_SALVO = Pattern.compile("^(.*?)(?: - (.*))? \\[(\\w+)\\]\\.mp4$");

    static final String PASTA = "Movies/TikTok";
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final Locale BR = new Locale("pt", "BR");
    static final long MOSTRAR_PAGINA_APOS = 15_000;   // ms sem achar o vídeo: mostra a página
    static final long DESISTIR_APOS = 120_000;
    static final int VERMELHO_YT = 0xFFFF0033;        // a barrinha de andamento

    // Cores do tema claro ou escuro, definidas em definirCores().
    int FUNDO, CAMPO, TEXTO, CINZA, ERRO, TOQUE;

    final Handler h = new Handler(Looper.getMainLooper());
    final ArrayDeque<Pedido> fila = new ArrayDeque<>();
    final Set<String> pedidos = new HashSet<>();
    final List<Pedido> itens = new ArrayList<>();
    Pedido atual;
    String scriptExtrair;
    SharedPreferences prefs;

    EditText campo;
    LinearLayout lista;
    LinearLayout caixaPagina;
    TextView avisoPagina;
    TextView contagem;
    WebView web;
    View vazio;

    static class Pedido {
        String link, chave, autor = "", qualidade = "";
        long inicio, bytes;
        boolean baixando, paginaVisivel, temCapa, comErro;
        Uri arquivo;
        View linha;
        TextView titulo, meta, duracao;
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
        carregarSalvos();
        receberIntent(getIntent());
    }

    void definirCores() {
        boolean escuro = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        FUNDO = escuro ? 0xFF0F0F0F : 0xFFFFFFFF;
        CAMPO = escuro ? 0xFF272727 : 0xFFF2F2F2;
        TEXTO = escuro ? 0xFFF1F1F1 : 0xFF0F0F0F;
        CINZA = escuro ? 0xFFAAAAAA : 0xFF606060;
        ERRO = escuro ? 0xFFFF8A80 : 0xFFCC0000;
        TOQUE = escuro ? 0x33FFFFFF : 0x1F000000;
    }

    void montarTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FUNDO);

        // Barra do link, no formato da busca do YouTube: campo arredondado + botão redondo.
        LinearLayout barraLink = new LinearLayout(this);
        barraLink.setGravity(Gravity.CENTER_VERTICAL);
        barraLink.setPadding(dp(12), dp(14), dp(12), 0);
        campo = new EditText(this);
        campo.setHint("Colar link do TikTok");
        campo.setHintTextColor(CINZA);
        campo.setTextColor(TEXTO);
        campo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        campo.setBackground(forma(GradientDrawable.RECTANGLE, CAMPO, 24));
        campo.setPadding(dp(18), 0, dp(18), 0);
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
        barraLink.addView(campo, new LinearLayout.LayoutParams(0, dp(48), 1));

        ImageView botao = new ImageView(this);
        botao.setImageResource(R.drawable.ic_baixar);
        botao.setImageTintList(ColorStateList.valueOf(FUNDO));
        botao.setScaleType(ImageView.ScaleType.CENTER);
        botao.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55888888),
                forma(GradientDrawable.OVAL, TEXTO, 0), null));
        botao.setContentDescription("Colar o link e baixar");
        botao.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!acharLinks(campo.getText().toString()).isEmpty()) pegarDoCampo(true);
                else colarEBaixar();
            }
        });
        LinearLayout.LayoutParams lpBotao = new LinearLayout.LayoutParams(dp(48), dp(48));
        lpBotao.leftMargin = dp(10);
        barraLink.addView(botao, lpBotao);
        raiz.addView(barraLink);

        TextView dica = texto("Ou, no TikTok: Compartilhar → Baixar TikTok", 13, CINZA);
        dica.setPadding(dp(30), dp(8), dp(16), 0);
        raiz.addView(dica);

        // Página do TikTok. Fica com 1 px enquanto tudo vai bem; cresce se precisar de verificação.
        caixaPagina = new LinearLayout(this);
        caixaPagina.setOrientation(LinearLayout.VERTICAL);
        caixaPagina.setPadding(dp(12), 0, dp(12), 0);
        avisoPagina = texto("", 14, TEXTO);
        avisoPagina.setVisibility(View.GONE);
        avisoPagina.setBackground(forma(GradientDrawable.RECTANGLE, CAMPO, 12));
        avisoPagina.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpAviso = new LinearLayout.LayoutParams(MATCH, WRAP);
        lpAviso.setMargins(0, dp(14), 0, dp(8));
        caixaPagina.addView(avisoPagina, lpAviso);
        web = new WebView(this);
        configurarWeb();
        caixaPagina.addView(web, new LinearLayout.LayoutParams(MATCH, 1));
        raiz.addView(caixaPagina);

        // Título da lista.
        LinearLayout cabecalho = new LinearLayout(this);
        cabecalho.setGravity(Gravity.BOTTOM);
        cabecalho.setPadding(dp(16), dp(22), dp(16), dp(8));
        TextView secao = texto("Downloads", 20, TEXTO);
        secao.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cabecalho.addView(secao);
        contagem = texto("", 14, CINZA);
        contagem.setPadding(dp(8), 0, 0, dp(2));
        cabecalho.addView(contagem);
        raiz.addView(cabecalho);

        FrameLayout area = new FrameLayout(this);
        ScrollView rolagem = new ScrollView(this);
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0, 0, 0, dp(24));
        rolagem.addView(lista);
        area.addView(rolagem, new FrameLayout.LayoutParams(MATCH, MATCH));
        vazio = estadoVazio();
        area.addView(vazio, new FrameLayout.LayoutParams(MATCH, MATCH));
        raiz.addView(area, new LinearLayout.LayoutParams(MATCH, 0, 1));

        setContentView(raiz);
        atualizarContagem();
    }

    View estadoVazio() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(dp(40), dp(56), dp(40), 0);
        ImageView icone = new ImageView(this);
        icone.setImageResource(R.drawable.ic_baixar);
        icone.setImageTintList(ColorStateList.valueOf(CINZA));
        v.addView(icone, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView t = texto("Nenhum download ainda", 16, TEXTO);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, dp(16), 0, dp(6));
        v.addView(t);
        TextView d = texto("Copie o link de um vídeo no TikTok e volte aqui. "
                + "O download começa sozinho e o vídeo vai para a galeria.", 14, CINZA);
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

    /** Uma linha da lista: miniatura vertical, título, detalhes e o menu ⋮. */
    View linha(final Pedido p) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(dp(16), dp(8), dp(4), dp(8));
        l.setBackground(new RippleDrawable(ColorStateList.valueOf(TOQUE), null, new ColorDrawable(0xFFFFFFFF)));

        FrameLayout moldura = new FrameLayout(this);
        moldura.setBackground(forma(GradientDrawable.RECTANGLE, CAMPO, 8));
        moldura.setClipToOutline(true);
        p.capa = new ImageView(this);
        p.capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        moldura.addView(p.capa, new FrameLayout.LayoutParams(MATCH, MATCH));
        p.duracao = texto("", 11, 0xFFFFFFFF);
        p.duracao.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.duracao.setBackground(forma(GradientDrawable.RECTANGLE, 0xCC000000, 4));
        p.duracao.setPadding(dp(4), dp(1), dp(4), dp(1));
        p.duracao.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpDur = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
        lpDur.setMargins(0, 0, dp(4), dp(6));
        moldura.addView(p.duracao, lpDur);
        // Andamento: a barrinha vermelha no pé da miniatura, como a do "continuar assistindo".
        p.barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.barra.setMax(100);
        p.barra.setIndeterminate(true);
        p.barra.setProgressTintList(ColorStateList.valueOf(VERMELHO_YT));
        p.barra.setIndeterminateTintList(ColorStateList.valueOf(VERMELHO_YT));
        p.barra.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        p.barra.setVisibility(View.GONE);
        moldura.addView(p.barra, new FrameLayout.LayoutParams(MATCH, dp(4), Gravity.BOTTOM));
        l.addView(moldura, new LinearLayout.LayoutParams(dp(72), dp(128)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(2), dp(4), 0);
        p.titulo = texto(p.link == null ? "" : p.link, 15, TEXTO);
        p.titulo.setMaxLines(2);
        p.titulo.setEllipsize(TextUtils.TruncateAt.END);
        p.titulo.setLineSpacing(dp(1), 1f);
        info.addView(p.titulo);
        p.meta = texto("", 12, CINZA);
        p.meta.setPadding(0, dp(4), 0, 0);
        p.meta.setMaxLines(3);
        info.addView(p.meta);
        l.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1));

        final TextView mais = texto("⋮", 20, TEXTO);
        mais.setGravity(Gravity.CENTER);
        mais.setContentDescription("Mais opções");
        mais.setBackground(new RippleDrawable(ColorStateList.valueOf(TOQUE), null, null));
        mais.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { abrirMenu(p, mais); }
        });
        l.addView(mais, new LinearLayout.LayoutParams(dp(40), dp(40)));

        l.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { abrirVideo(p); }
        });
        l.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { abrirMenu(p, mais); return true; }
        });
        p.linha = l;
        return l;
    }

    void abrirMenu(final Pedido p, View ancora) {
        PopupMenu menu = new PopupMenu(this, ancora);
        final Menu m = menu.getMenu();
        if (p.arquivo != null) {
            m.add(0, 1, 0, "Compartilhar");
            m.add(0, 2, 1, "Apagar do celular");
        }
        if (p.comErro && p.link != null && LINK_VIDEO.matcher(p.link).lookingAt()) {
            m.add(0, 3, 0, "Tentar de novo");
        }
        if (p.arquivo == null && !p.baixando && p != atual && !fila.contains(p)) {
            m.add(0, 4, 2, "Tirar da lista");
        }
        if (p.link != null) m.add(0, 5, 3, "Copiar link");
        if (m.size() == 0) return;
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1: compartilhar(p); break;
                    case 2: confirmarApagar(p); break;
                    case 3: tentarDeNovo(p); break;
                    case 4: removerLinha(p); break;
                    case 5: copiarLink(p); break;
                }
                return true;
            }
        });
        menu.show();
    }

    // ------------------------------------------------------------------ vídeos já salvos

    /** Mostra na lista os vídeos que o app já salvou na galeria. */
    void carregarSalvos() {
        new Thread(new Runnable() {
            public void run() {
                final List<Pedido> salvos = new ArrayList<>();
                String[] col = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT};
                try (Cursor c = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, col,
                        MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? AND "
                                + MediaStore.Video.Media.IS_PENDING + "=0",
                        new String[]{PASTA + "%"}, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                    while (c != null && c.moveToNext()) {
                        Pedido p = new Pedido();
                        p.arquivo = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0));
                        String nome = c.getString(1) == null ? "" : c.getString(1);
                        Matcher m = NOME_SALVO.matcher(nome);
                        String titulo = nome.replaceAll("\\.mp4$", "");
                        if (m.matches()) {
                            p.autor = "tiktok".equals(m.group(1)) ? "" : m.group(1);
                            titulo = m.group(2) != null ? m.group(2) : (p.autor.isEmpty() ? "Vídeo do TikTok" : p.autor);
                            p.link = "https://www.tiktok.com/@" + (p.autor.isEmpty() ? "_" : p.autor) + "/video/" + m.group(3);
                        }
                        p.chave = titulo; // só para guardar o título até a linha existir
                        p.bytes = c.getLong(3);
                        p.qualidade = qualidade(c.getInt(4), c.getInt(5));
                        p.inicio = c.getLong(2); // duração em ms
                        salvos.add(p);
                    }
                } catch (Exception ignorado) {
                    // sem acesso à galeria: a lista começa vazia
                }
                h.post(new Runnable() {
                    public void run() {
                        for (Pedido p : salvos) {
                            String titulo = p.chave;
                            long duracao = p.inicio;
                            p.chave = null;
                            p.inicio = 0;
                            View v = linha(p);
                            lista.addView(v);
                            itens.add(p);
                            p.titulo.setText(titulo);
                            mostrarPronto(p, duracao);
                            carregarMiniatura(p);
                        }
                        atualizarContagem();
                    }
                });
            }
        }).start();
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
                    Pedido p = novaLinha(link);
                    erro(p, "Não é link de vídeo (perfil, hashtag ou fotos).");
                }
                continue;
            }
            String chave = chaveDoLink(link);
            if (!pedidos.add(chave)) continue;
            Pedido p = novaLinha(link);
            p.chave = chave;
            fila.add(p);
            situacao(p, "Na fila", CINZA);
            novos++;
        }
        processarFila();
        return novos;
    }

    Pedido novaLinha(String link) {
        Pedido p = new Pedido();
        p.link = link;
        lista.addView(linha(p), 0);
        itens.add(0, p);
        atualizarContagem();
        return p;
    }

    void tentarDeNovo(Pedido p) {
        removerLinha(p);
        receberTexto(p.link, false);
    }

    void removerLinha(Pedido p) {
        lista.removeView(p.linha);
        itens.remove(p);
        atualizarContagem();
    }

    void atualizarContagem() {
        int n = 0;
        for (Pedido p : itens) if (p.arquivo != null) n++;
        contagem.setText(n == 0 ? "" : n == 1 ? "1 vídeo" : n + " vídeos");
        vazio.setVisibility(itens.isEmpty() ? View.VISIBLE : View.GONE);
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
        p.autor = r.optString("autor").replaceAll("[^\\w.-]+", "");
        p.qualidade = qualidade(r.optInt("largura"), r.optInt("altura"));
        String desc = r.optString("titulo").trim();
        p.titulo.setText(desc.isEmpty() ? "Vídeo do TikTok" : desc);
        carregarCapa(p, r.optString("capa"), agente, pagina);

        // A página já fez o trabalho dela; libera o WebView para o próximo da fila.
        atual = null;
        web.loadUrl("about:blank");

        Uri existente = id.isEmpty() ? null : acharJaBaixado(id);
        if (existente != null) {
            // Já está na galeria: tira a linha antiga e deixa esta, no topo.
            for (Pedido outro : new ArrayList<>(itens)) {
                if (outro != p && existente.equals(outro.arquivo)) removerLinha(outro);
            }
            p.arquivo = existente;
            p.baixando = false;
            mostrarPronto(p, -1);
            if (!p.temCapa) carregarMiniatura(p);
            Toast.makeText(this, "Esse vídeo já estava salvo", Toast.LENGTH_SHORT).show();
            atualizarContagem();
            processarFila();
            return;
        }

        situacao(p, "Baixando" + (p.qualidade.isEmpty() ? "" : " · " + p.qualidade), CINZA);
        new Thread(new Runnable() {
            public void run() {
                String falha = "não consegui baixar o arquivo do vídeo.";
                for (int i = 0; i < urls.size(); i++) {
                    try {
                        Uri uri = baixarArquivo(p, urls.get(i), cookies.get(i), agente, pagina, nome);
                        if (uri != null) {
                            final boolean marca = i >= semMarca;
                            final long duracao = lerDuracao(uri);
                            p.arquivo = uri;
                            h.post(new Runnable() {
                                public void run() {
                                    p.baixando = false;
                                    mostrarPronto(p, duracao);
                                    if (marca) situacao(p, p.meta.getText() + "\nSó achei a versão com marca d'água", CINZA);
                                    if (!p.temCapa) carregarMiniatura(p);
                                    atualizarContagem();
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
                        p.baixando = false;
                        pedidos.remove(p.chave);
                        erro(p, "Erro: " + msg);
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
            p.bytes = feito;
            return ok ? uri : null;
        } finally {
            c.disconnect();
        }
    }

    long lerDuracao(Uri uri) {
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        try {
            m.setDataSource(this, uri);
            String d = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? -1 : Long.parseLong(d);
        } catch (Exception e) {
            return -1;
        } finally {
            try { m.release(); } catch (Exception ignorado) { /* nada */ }
        }
    }

    /** Capa do vídeo, vinda da página, enquanto ele baixa. */
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
                    mostrarCapa(p, b);
                } catch (Exception ignorado) {
                    // sem capa: depois do download a miniatura vem do próprio arquivo
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    /** Miniatura tirada do vídeo salvo (sem internet). */
    void carregarMiniatura(final Pedido p) {
        if (p.arquivo == null) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    mostrarCapa(p, getContentResolver().loadThumbnail(p.arquivo, new Size(288, 512), null));
                } catch (Exception ignorado) {
                    // fica o fundo cinza
                }
            }
        }).start();
    }

    void mostrarCapa(final Pedido p, Bitmap b) {
        if (b == null) return;
        if (b.getWidth() > 360) {
            b = Bitmap.createScaledBitmap(b, 360, Math.max(1, b.getHeight() * 360 / b.getWidth()), true);
        }
        final Bitmap img = b;
        h.post(new Runnable() {
            public void run() {
                p.temCapa = true;
                p.capa.setImageBitmap(img);
            }
        });
    }

    Uri acharJaBaixado(String id) {
        String[] col = {MediaStore.Video.Media._ID};
        try (Cursor cur = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, col,
                MediaStore.Video.Media.DISPLAY_NAME + " LIKE ? AND " + MediaStore.Video.Media.IS_PENDING + "=0",
                new String[]{"%[" + id + "].mp4"}, null)) {
            if (cur != null && cur.moveToFirst()) {
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cur.getLong(0));
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

    static String megas(long bytes) {
        return String.format(BR, "%.1f MB", bytes / 1048576.0);
    }

    static String tempo(long ms) {
        long s = Math.round(ms / 1000.0);
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ estado das linhas

    void andamento(Pedido p, long feito, long total) {
        String q = p.qualidade.isEmpty() ? "" : " · " + p.qualidade;
        if (total > 0) {
            int pct = (int) Math.min(99, feito * 100 / total);
            p.barra.setIndeterminate(false);
            p.barra.setProgress(pct);
            situacao(p, "Baixando " + pct + "%" + q + " · " + megas(total), CINZA);
        } else {
            situacao(p, "Baixando " + megas(feito) + q, CINZA);
        }
    }

    /** Detalhes de um vídeo salvo: "@autor · 1080p · 8,4 MB", e a duração na miniatura. */
    void mostrarPronto(Pedido p, long duracaoMs) {
        p.comErro = false;
        p.barra.setVisibility(View.GONE);
        StringBuilder s = new StringBuilder();
        if (!p.autor.isEmpty()) s.append('@').append(p.autor);
        if (!p.qualidade.isEmpty()) s.append(s.length() > 0 ? " · " : "").append(p.qualidade);
        if (p.bytes > 0) s.append(s.length() > 0 ? " · " : "").append(megas(p.bytes));
        situacao(p, s.length() > 0 ? s.toString() : "Salvo na galeria", CINZA);
        if (duracaoMs > 0) {
            p.duracao.setText(tempo(duracaoMs));
            p.duracao.setVisibility(View.VISIBLE);
        }
    }

    void erro(Pedido p, String msg) {
        p.comErro = true;
        if (p.barra != null) p.barra.setVisibility(View.GONE);
        situacao(p, msg, ERRO);
    }

    void situacao(Pedido p, String msg, int cor) {
        p.meta.setText(msg);
        p.meta.setTextColor(cor);
    }

    // ------------------------------------------------------------------ ações do menu

    void abrirVideo(Pedido p) {
        if (p.arquivo == null) {
            if (p.comErro) abrirMenu(p, p.meta);
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(p.arquivo, "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Abra pela galeria, na pasta " + PASTA + ".", Toast.LENGTH_LONG).show();
        }
    }

    void compartilhar(Pedido p) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_STREAM, p.arquivo);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Compartilhar vídeo"));
    }

    void copiarLink(Pedido p) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        lembrarClipe(p.link); // para o próprio app não baixar de novo ao voltar
        cm.setPrimaryClip(ClipData.newPlainText("link", p.link));
        Toast.makeText(this, "Link copiado", Toast.LENGTH_SHORT).show();
    }

    void confirmarApagar(final Pedido p) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Apagar este vídeo?")
                .setMessage("Ele sai da lista e da galeria do celular.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { apagar(p); }
                })
                .show();
    }

    void apagar(Pedido p) {
        try {
            getContentResolver().delete(p.arquivo, null, null);
            removerLinha(p);
            if (p.chave != null) pedidos.remove(p.chave);
        } catch (Exception e) {
            Toast.makeText(this, "Não consegui apagar. Apague pela galeria, na pasta " + PASTA + ".",
                    Toast.LENGTH_LONG).show();
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

    GradientDrawable forma(int tipo, int cor, int raio) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(tipo);
        g.setColor(cor);
        if (tipo == GradientDrawable.RECTANGLE) g.setCornerRadius(dp(raio));
        return g;
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
