# Caixa do Mês

Organizador de finanças mensal em uma única página (HTML + CSS + JavaScript, sem dependências), também disponível como app Android e programa para Windows.

**Quer usar o app?** Siga o [PASSO-A-PASSO.md](PASSO-A-PASSO.md): instalar no celular, abrir no computador e ligar a sincronização entre os dois, sem precisar saber programar.

## O que faz

- **Até 2 fontes de renda** por mês (nome + valor).
- **Gastos** com descrição, valor, categoria, dia de vencimento e **prioridade** (Alta, Média, Baixa).
- **Divisão por mês**: uma faixa no topo mostra cada mês com o número de contas, o total e quanto sobra ou falta. Toque num mês para ver as contas dele.
- **Contas parceladas e fixas**: ao lançar um gasto, escolha *Parcelada* (ex.: 10 parcelas, uma em cada mês, marcadas como 1/10, 2/10…) ou *Todo mês* (conta fixa por quantos meses quiser). Ao editar ou excluir, dá para aplicar só no mês aberto ou também nos próximos.
- **Cálculo da sobra**: renda total − total de gastos, com a porcentagem da renda comprometida por prioridade.
- **Controle de pagamento**: marque contas como pagas e veja quanto já foi pago, quanto falta pagar e quanto está disponível agora. Contas vencidas e não pagas aparecem como *atrasada*.
- **Ordem de pagamento**: lista as contas pela prioridade (e depois pelo vencimento), mostra o saldo depois de cada uma e marca onde a renda acaba. Se faltar dinheiro, diz se adiar as contas de prioridade baixa resolve.
- **Copiar mês anterior**: um mês em branco oferece copiar as rendas e as contas do último mês preenchido.
- **Guardar dinheiro**: em cada mês, defina quanto vai guardar (digitando o valor ou tocando em R$ 50, R$ 100, R$ 200, R$ 500 ou "Tudo que sobra"), veja quanto fica livre para gastar, marque "já guardei" e acompanhe o total guardado. Dá para repetir o valor nos próximos meses.
- **Sincronizar celular e computador**: com uma conta (e-mail e senha) no Firebase, o app Android e o programa do Windows ficam com os mesmos dados. As mudanças vão para a nuvem na hora e chegam no outro aparelho quando ele é aberto, quando você volta para ele e a cada 30 segundos. Sem internet, tudo continua funcionando e sincroniza depois.
- **Backup**: gera um texto com todos os meses para guardar ou levar para outro aparelho, e restaura a partir dele. No computador também dá para salvar e abrir o backup como arquivo.

## Como usar

- **No computador**: abra o `index.html` no navegador. Os dados ficam salvos no próprio navegador (`localStorage`), separados por mês.
- **No Android**: instale o `dist/CaixaDoMes.apk` (é preciso permitir a instalação de apps de fora da Play Store). Os dados ficam no aparelho e o app funciona sem internet.
- **No Windows**: extraia o `CaixaDoMes-Windows.zip` e abra `CaixaDoMes\CaixaDoMes.exe` (instruções no `LEIA-ME.txt` que vem junto). Os dados ficam no computador e o programa funciona sem internet.
- **No Claude**: quando a página roda como Artifact, os dados ficam no espaço privado da sua conta.

Valores aceitam o formato brasileiro (`1.234,56`) ou com ponto (`1234.56`).

## Gerar o APK de novo

```sh
android/build.sh
```

O script baixa as ferramentas necessárias (aapt2 do bundletool, android.jar, dx), copia o `index.html` para dentro do app com as fontes embutidas, compila, alinha e assina com a chave `android/caixa-do-mes.p12`. Os scripts de build rodam em Linux (ou no WSL do Windows) com bash, Java 17+, Node e Python.

A chave **não fica no repositório** (está no `.gitignore`). Na primeira vez, o `build.sh` cria uma chave nova só sua; troque antes a `SENHA` no `build.sh`. Guarde a chave e use sempre a mesma: assim uma versão nova instala por cima da antiga sem perder os dados. Um APK assinado com outra chave não instala por cima: é preciso desinstalar o antigo (fazendo backup antes). Lembre de aumentar `VERSAO_CODIGO` no `build.sh` a cada versão.

## Gerar o programa para Windows

```sh
desktop/build.sh
```

Monta o programa com o Electron para Windows (baixado do GitHub), coloca a página com as fontes embutidas, aplica o ícone e a versão no `CaixaDoMes.exe` e gera `dist/CaixaDoMes-Windows.zip`. O zip tem mais de 100 MB e por isso não fica no repositório. A versão vem do `desktop/package.json`.

## Ligar a sincronização (Firebase)

Resumo técnico abaixo; o [PASSO-A-PASSO.md](PASSO-A-PASSO.md) explica tela por tela.

1. Em https://console.firebase.google.com, crie um projeto (o Google Analytics pode ficar desligado).
2. **Authentication** > Começar > **E-mail/senha** > Ativar > Salvar.
3. **Realtime Database** > Criar banco de dados > modo bloqueado. Na aba **Regras**, cole o conteúdo de `firebase-regras.json` e publique. Assim cada pessoa só lê e grava os próprios dados.
4. Em Configurações do projeto > Seus apps > Web (`</>`), registre um app e copie `apiKey` e `databaseURL`.
5. No app e no programa, abra **Sincronizar celular e computador**, cole o código `firebaseConfig` e toque em **Ligar sincronização** (em cada aparelho). Ou deixe fixo para todos: preencha `FIREBASE_FIXO` no `index.html` e gere de novo o APK e o programa.

Os dados ficam em `usuarios/<id da conta>/meses/<AAAA-MM>`. Ao sincronizar, vale a versão do mês alterada por último. O aparelho que cria a conta envia os dados dele; um aparelho que entra numa conta existente recebe os dados da nuvem.
