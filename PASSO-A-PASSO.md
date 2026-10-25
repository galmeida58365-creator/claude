# Caixa do Mês: passo a passo

Este guia é para quem quer usar o Caixa do Mês sem saber programar. São três partes:

1. instalar o app no celular Android;
2. abrir o app no computador;
3. (opcional) ligar a sincronização, para o celular e o computador terem os mesmos dados.

Sem a parte 3, cada aparelho guarda os próprios dados e tudo funciona sem internet.

---

## Parte 1: instalar no celular (Android)

1. No celular, abra este link no navegador:
   **https://github.com/galmeida58365-creator/claude/raw/main/dist/CaixaDoMes.apk**
2. O arquivo `CaixaDoMes.apk` vai baixar. Toque nele quando terminar.
3. O Android vai avisar que o app é "de fonte desconhecida". Toque em **Configurações**, ligue **Permitir desta fonte** e volte.
4. Toque em **Instalar**. Se o Play Protect perguntar, escolha **Instalar mesmo assim** (o aviso aparece porque o app não está na Play Store).
5. Abra o **Caixa do Mês**.

> O app é só para Android; não há versão para iPhone.

## Parte 2: abrir no computador

1. Abra **https://github.com/galmeida58365-creator/claude**.
2. Clique no botão verde **Code** > **Download ZIP**.
3. Na pasta Downloads, clique com o botão direito no zip > **Extrair tudo**.
4. Abra a pasta extraída e dê dois cliques em **`index.html`**. Ele abre no Chrome ou no Edge.
5. Para achar fácil depois, salve a página nos favoritos.

Os dados ficam guardados nesse navegador. Se você limpar os dados de navegação do navegador, eles somem: use a sincronização (parte 3) ou o **Backup** do app.

> Também existe um programa próprio para Windows (`CaixaDoMes.exe`). Ele não vem no repositório porque o arquivo passa de 100 MB; quem souber usar o terminal pode gerar com `desktop/build.sh` (veja o [README.md](README.md)).

---

## Parte 3 (opcional): sincronizar celular e computador

A sincronização usa o **Firebase**, um serviço do Google, no plano **grátis**. Cada pessoa cria o **seu próprio** projeto: assim os seus dados ficam na sua conta do Google, e ninguém mais tem acesso.

Leva uns 10 minutos. Faça no computador, com a sua conta do Google (Gmail) aberta no navegador.

### 3.1 Criar o projeto

1. Abra **https://console.firebase.google.com**.
2. Clique em **Comece configurando um projeto do Firebase** (ou **Criar um projeto**).
3. Nome do projeto: `caixa-do-mes`. O Google acrescenta um código no fim (ex.: `caixa-do-mes-1a2b3`); tudo bem.
4. Marque **Aceito os Termos do Firebase**. A opção **Programa para desenvolvedores do Google** pode ficar desligada. Clique em **Continuar**.
5. Na tela do **Gemini no Firebase**, pode desligar. Clique em **Continuar**.
6. Na tela do **Google Analytics**, desligue. Clique em **Criar projeto**.
7. Espere aparecer "Seu projeto do Firebase está pronto" e clique em **Continuar**.

> **Plano:** fique no **Spark (sem custos)**, que aparece no canto de baixo à esquerda. Não clique em "Fazer upgrade" e não cadastre cartão. O app não precisa de nada pago.

### 3.2 Ligar o login por e-mail e senha

1. No menu da esquerda: **Segurança** (ou **Criação**) > **Authentication** > **Vamos começar**.
2. Em **Provedores nativos**, clique em **E-mail/senha**.
3. Ligue a **primeira** chave (**E-mail/senha**). Deixe desligada a de **Link do e-mail (login sem senha)**.
4. Clique em **Salvar**. O status fica **Ativado**.

### 3.3 Criar o banco de dados

1. No menu da esquerda: **Bancos de dados e armazenamento** > **Realtime Database** > **Criar banco de dados**.
   (Atenção: é o **Realtime Database**, não o "Firestore".)
2. Local: **Estados Unidos (us-central1)**. Clique em **Avançar**.
3. Escolha **Iniciar no modo bloqueado** e clique em **Ativar**.
4. No topo aparece o endereço do banco, parecido com `https://caixa-do-mes-1a2b3-default-rtdb.firebaseio.com`. Ele vai aparecer de novo no passo 3.5.

### 3.4 Colar as regras de segurança

As regras fazem cada pessoa ver só os próprios dados.

1. Ainda no Realtime Database, abra a aba **Regras**.
2. Apague tudo que está escrito lá e cole isto (é o mesmo conteúdo do arquivo [`firebase-regras.json`](firebase-regras.json)):

   ```json
   {
     "rules": {
       "usuarios": {
         "$uid": {
           ".read": "auth != null && auth.uid === $uid",
           ".write": "auth != null && auth.uid === $uid"
         }
       }
     }
   }
   ```

3. Clique em **Publicar**.

### 3.5 Pegar o código `firebaseConfig`

1. Clique na engrenagem ao lado de **Visão geral do projeto** > **Configurações do projeto**.
2. Em **Seus aplicativos**, clique no ícone **`</>`** (Web).
3. Apelido do app: `caixa`. **Não** marque o Firebase Hosting. Clique em **Registrar app**.
4. Aparece um código. Copie **o bloco inteiro** que começa em `const firebaseConfig = {` e termina em `};`. Ele é parecido com este (os seus valores serão outros):

   ```js
   const firebaseConfig = {
     apiKey: "AIza...",
     authDomain: "caixa-do-mes-1a2b3.firebaseapp.com",
     databaseURL: "https://caixa-do-mes-1a2b3-default-rtdb.firebaseio.com",
     projectId: "caixa-do-mes-1a2b3",
     storageBucket: "caixa-do-mes-1a2b3.firebasestorage.app",
     messagingSenderId: "...",
     appId: "..."
   };
   ```

5. Confira se tem a linha **`databaseURL`**. Se não tiver, é porque o banco do passo 3.3 não foi criado: crie e copie o código de novo.
6. Mande esse bloco para o celular (WhatsApp, e-mail para você mesmo etc.).

> O `apiKey` não é uma senha: quem protege os dados são as regras do passo 3.4 e o login com a sua senha. Mesmo assim, não precisa espalhar esse código.

### 3.6 Ligar a sincronização no app

**A ordem importa.** Comece pelo aparelho que já tem os dados certos (normalmente o celular). Quem **cria a conta** envia os dados dele para a nuvem; quem **entra** depois recebe os dados da nuvem.

**No primeiro aparelho (ex.: celular):**

1. Abra o Caixa do Mês > painel **Sincronizar celular e computador**.
2. Cole o bloco `firebaseConfig` **inteiro** e toque em **Ligar sincronização**.
3. Digite um e-mail e uma senha nova (mínimo 6 caracteres). Pode ser o seu e-mail de sempre, mas use uma senha diferente da do e-mail.
4. Toque em **Criar conta**.

**No segundo aparelho (ex.: computador):**

1. Abra o Caixa do Mês > mesmo painel.
2. Cole o **mesmo** bloco inteiro e clique em **Ligar sincronização**.
3. Digite o **mesmo** e-mail e senha e clique em **Entrar** (não em "Criar conta").

Pronto: no topo aparece **"Sincronizado às HH:MM"**. As mudanças vão para a nuvem na hora e chegam no outro aparelho quando ele é aberto, quando você volta para ele e a cada 30 segundos. Sem internet, tudo continua funcionando e sincroniza depois.

---

## Se der erro

As mensagens aparecem embaixo do painel de sincronização.

| Mensagem | O que fazer |
|---|---|
| "Não achei o apiKey…" | Foi colado só um pedaço. Copie o bloco `firebaseConfig` inteiro, de `const` até `};`. |
| "Não achei o databaseURL…" | Falta a linha `databaseURL`: crie o Realtime Database (passo 3.3) e copie o código de novo (passo 3.5). |
| "O login por e-mail não está ativado no Firebase." | Refaça o passo 3.2. |
| "O Firebase recusou o acesso. Confira as regras do Realtime Database." | Refaça o passo 3.4 e confira se clicou em **Publicar**. |
| "Já existe uma conta com esse e-mail. Use Entrar." | A conta já foi criada no outro aparelho: use **Entrar**. |
| "E-mail ou senha incorretos." | Confira o e-mail e a senha. Se esqueceu, toque em **Esqueci a senha**. |
| Funciona num aparelho e no outro não | No aparelho que não funciona, toque em **Trocar o projeto do Firebase**, cole o bloco inteiro de novo e use **Entrar**. |

## Dicas

- **Backup:** de vez em quando, use **Backup dos dados** > **Gerar e copiar** e guarde o texto. Com ele dá para restaurar tudo em qualquer aparelho.
- **Trocar de celular:** instale o app no celular novo, cole o mesmo `firebaseConfig` e use **Entrar**.
- **Cada pessoa com seu projeto:** se outra pessoa da família quiser usar, ela pode criar a própria conta (outro e-mail) no mesmo projeto; as regras do passo 3.4 deixam os dados de cada uma separados.
