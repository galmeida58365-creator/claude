# Cofrinho

App para juntar dinheiro para comprar uma coisa (por exemplo, um Nintendo Switch). Você cola o link do produto, diz quanto custa e vai registrando o que guarda. O app mostra quanto já tem, quanto falta e quando você chega lá.

É um app separado do Caixa do Mês, feito do mesmo jeito: uma página só (`cofrinho/index.html`), que também vira app Android.

## O que faz

- **Metas de compra**: cada meta tem o link do produto, o nome, o preço, uma foto e, se quiser, uma data até quando você quer comprar. Dá para ter várias metas ao mesmo tempo.
- **Lê a página do produto**: ao colar o link, o app tenta pegar sozinho o nome, a foto e o preço. No app Android isso costuma funcionar; no navegador do computador as lojas bloqueiam, e aí você preenche na mão (o link fica guardado igual).
- **Compartilhar da loja**: no Android, no app da loja (Mercado Livre, Amazon…) toque em **Compartilhar** > **Cofrinho**. A meta nova já abre com o link.
- **Guardar e tirar**: digite o valor ou toque em +10, +20, +50, +100, +200. Escolha o dia e escreva uma anotação, se quiser. Se precisar usar o dinheiro, toque em **Tirei**.
- **Quanto falta**: barra de progresso, porcentagem e quanto falta em reais.
- **Plano**: com uma data, mostra quanto guardar por semana, por mês e por dia. Depois de algumas semanas, mostra seu ritmo médio e o mês em que você chega lá. O **E se eu guardar…** simula: "guardando R$ 100 por mês, chego lá em…".
- **Gráfico** de como o cofrinho encheu, com a linha da meta.
- **Meta batida**: quando chega no preço, aparece o botão para ir comprar. Depois, **Já comprei!** guarda a meta em "Já comprei".
- **Backup** em texto ou arquivo, e **sincronização** entre celular e computador usando o mesmo Firebase do Caixa do Mês (mesmo código e mesma conta).

## Como usar

- **No celular (Android)**: baixe e instale o `dist/Cofrinho.apk`:
  **https://github.com/galmeida58365-creator/claude/raw/main/dist/Cofrinho.apk**
  O Android avisa que é "de fonte desconhecida": toque em **Configurações** > **Permitir desta fonte** > **Instalar** (o mesmo processo do Caixa do Mês).
- **No computador**: abra o `cofrinho/index.html` no Chrome ou no Edge (baixe o repositório pelo botão verde **Code** > **Download ZIP**).

Os dados ficam no aparelho e o app funciona sem internet. Valores aceitam `1.234,56` ou `1234.56`.

## Sincronizar celular e computador

Se você já ligou a sincronização no Caixa do Mês, é só repetir no Cofrinho: abra **Sincronizar celular e computador**, cole o **mesmo** código `firebaseConfig` e toque em **Entrar** com o **mesmo** e-mail e senha. Não precisa mudar nada no Firebase: as regras do `firebase-regras.json` já cobrem o Cofrinho.

Se ainda não tem, siga a parte 3 do [PASSO-A-PASSO.md](../PASSO-A-PASSO.md) (é grátis) e use **Criar conta** no primeiro aparelho.

As metas ficam em `usuarios/<id da conta>/cofrinho/<id da meta>`. Quando a mesma meta muda nos dois aparelhos, vale a alteração mais recente. Diferente do Caixa do Mês, aqui tanto faz a ordem: ao entrar, as metas do aparelho e as da nuvem são juntadas.

## Gerar o APK de novo

```sh
cofrinho/android/build.sh
```

Usa as mesmas ferramentas do Caixa do Mês (`android/embutir_fontes.py`, `android/alinhar.py`, `android/Assina.java`). Na primeira vez cria a chave `cofrinho/android/cofrinho.p12`, que **não vai para o repositório**: guarde-a e use sempre a mesma, para que uma versão nova instale por cima sem perder os dados. Aumente `VERSAO_CODIGO` no `build.sh` a cada versão. Os ícones saem de `node cofrinho/android/gerar_icones.js`.
