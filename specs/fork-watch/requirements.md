# Requisitos — fork do openGym com APK próprio + app para Galaxy Watch

> Spec `fork-watch` · Fase 1 (Especificação) · Base: upstream openGym v1.4.1 (`versionCode 26`)
> Status: R1 aprovada em 2026-10-13 · **Revisão 2 (2026-10-14) aguardando aprovação**

> **Revisão 2.** O spike M2.1 mostrou que o JS do celular não roda com o celular bloqueado, nem com
> foreground service (`FORK.md`). O relógio passa a conduzir o treino sozinho, e o celular aplica o
> que veio do relógio quando é aberto, no formato que o upstream já usa para o Apple Watch
> (`design.md` §0). Itens alterados estão marcados com **(R2)**. Os demais seguem como na R1.

## 1. Contexto e objetivo

O openGym é um tracker de treino em React 19 + Vite. O app Android é o mesmo frontend empacotado
com Capacitor (flavor `VITE_MOBILE=1`, dados locais no aparelho). Este fork pessoal entrega:

1. um APK release do celular assinado com uma chave própria, que instala e atualiza sem erro;
2. um app Wear OS para o Samsung Galaxy Watch 4 Classic, sincronizado com o celular durante o
   treino, com experiência equivalente à do Hevy no relógio;
3. sincronização bidirecional celular ↔ relógio durante o treino ativo.

Fatos do código atual que estes requisitos assumem (levantados na Fase 0):

- O treino ativo vive em `S.active` (store Zustand), persistido em `localStorage` e espelhado em
  arquivo no aparelho. Sobrevive a reinício do app.
- Concluir série, editar carga/reps e navegar entre exercícios hoje são funções internas do
  componente `ActiveWorkout` (`frontend/src/views/Workout.jsx`), não ações do store.
- O descanso é controlado por `useUI` (`startRest`, `addRest`, `pauseRest`, `stopRest`). No Android, um
  foreground service nativo (`RestTimerService`) mantém o countdown na notificação, aceita
  pausar/±15s/pular com a WebView congelada e devolve eventos ao JS.
- O verificador de atualização (`frontend/src/lib/update.js`) consulta
  `api.github.com/repos/DuarteSantos8/openGym/releases/latest`.

### Glossário

| Termo | Significado |
|---|---|
| Celular | O app Android do fork (Capacitor), fonte da verdade |
| Relógio | O app Wear OS do fork, cliente fino |
| Treino ativo | Sessão iniciada e não finalizada nem descartada (`S.active != null`) |
| Snapshot | Estado do treino ativo publicado pelo celular para o relógio |
| Comando | Ação enviada pelo relógio ao celular (concluir série, ajustar carga etc.) |
| Conectados | Celular e relógio pareados e com o nó do Data Layer alcançável |

## 2. Histórias de usuário e critérios de aceitação

Formato EARS: **QUANDO** <gatilho> **ENTÃO** o sistema **DEVE** <resposta>. Use **ENQUANTO** para estados
e **SE** para condições indesejadas. Prioridade: **P1** (obrigatório) ou **P2** (desejável, marco M5).

---

### RB — Build e distribuição

**História RB-1.** Como dono do fork, quero instalar no meu celular um APK com identidade própria,
para usar o fork lado a lado com o app oficial, sem conflito de assinatura.

- **RB-1.1 (P1)** QUANDO o APK release do celular for gerado, ENTÃO ele DEVE ter `applicationId`
  `com.cezar.opengym` e nome visível "openGym", igual ao upstream (D-10; antes "openGym (Cezar)").
- **RB-1.2 (P1)** QUANDO o APK do fork for instalado num aparelho que tem o app oficial
  (`ch.duartesantos.opengym`), ENTÃO os dois DEVEM coexistir, cada um com seus próprios dados.
- **RB-1.3 (P1)** QUANDO o build debug for gerado, ENTÃO ele DEVE continuar com sufixo próprio
  (`.test`), para coexistir com o release do fork.

**História RB-2.** Como dono do fork, quero que o `assembleRelease` já entregue os APKs assinados,
para não depender de passos manuais.

- **RB-2.1 (P1)** QUANDO `assembleRelease` rodar com um `keystore.properties` válido, ENTÃO os APKs
  do celular e do relógio DEVEM sair assinados com esquemas v2 e v3, sem rodar `apksigner sign`
  à mão.
- **RB-2.2 (P1)** O Gradle DEVE ler caminho, alias e senhas da keystore de um
  `keystore.properties` fora do controle de versão.
- **RB-2.3 (P1)** SE o `keystore.properties` não existir, ENTÃO o build release DEVE falhar com
  mensagem clara, ou produzir APK sem assinatura avisando isso. Nunca pode assinar com chave de
  debug silenciosamente.
- **RB-2.4 (P1)** A keystore, o `keystore.properties` e qualquer senha NUNCA DEVEM ser commitados. Os
  padrões correspondentes DEVEM entrar no `.gitignore` antes de qualquer um desses arquivos ser
  criado.
- **RB-2.5 (P1)** QUANDO for a hora de criar a keystore, ENTÃO o assistente DEVE parar e pedir ao dono
  que rode o `keytool` interativamente, com o comando exato e um local fora do repositório. Depois
  DEVE lembrá-lo de fazer backup da keystore.

**História RB-3.** Como dono do fork, quero que cada build novo instale por cima do anterior.

- **RB-3.1 (P1)** O `versionCode` DEVE ser estritamente crescente e calculado automaticamente a partir
  da versão do upstream e de um contador de build do fork.
- **RB-3.2 (P1)** Celular e relógio DEVEM ser gerados com o mesmo `versionName` e o mesmo
  `versionCode` no mesmo build. Se o Wear exigir faixa própria, a regra DEVE ficar documentada
  no design.
- **RB-3.3 (P1)** QUANDO o fork fizer merge de uma versão nova do upstream, ENTÃO o `versionCode`
  resultante DEVE ser maior que o de qualquer build anterior do fork.

**História RB-4.** Como dono do fork, não quero que o app me ofereça o APK oficial.

- **RB-4.1 (P1)** O verificador de atualização NÃO DEVE consultar nem oferecer releases de
  `DuarteSantos8/openGym`.
- **RB-4.2 (P1)** QUANDO o verificador rodar, ENTÃO ele DEVE estar desativado ou apontar para
  `Cezarmvm/openGym`. Sem release publicada no fork, ele DEVE responder "sem atualização", sem
  erro na tela (ver P-3).

**História RB-5.** Como dono do fork, quero manter os SDKs compatíveis com o upstream e com o meu
relógio.

- **RB-5.1 (P1)** `minSdk`, `targetSdk` e `compileSdk` do celular DEVEM continuar iguais aos do
  upstream (hoje 23/35/35).
- **RB-5.2 (P1)** O `minSdk` do app Wear DEVE ser compatível com a versão de Wear OS do Galaxy Watch 4
  Classic, confirmada via `adb` com o relógio conectado (ver P-1).

**História RB-6.** Como dono do fork, quero um script único de release.

- **RB-6.1 (P1)** DEVE existir `scripts/fork-release.*` adequado ao SO detectado (Windows → PowerShell
  ou Git Bash). O script DEVE:
  1. rodar o build mobile (equivalente a `npm run build:mobile`, funcionando no Windows);
  2. compilar os APKs release do celular e do relógio;
  3. rodar `apksigner verify --print-certs` nos dois e **falhar se os certificados forem
     diferentes**;
  4. conferir o `applicationId` dos dois com `aapt2 dump badging`;
  5. copiar os APKs para `dist-apk/` com nomes versionados (ex.:
     `openGym-Cezar-phone-<versionName>-<versionCode>.apk`);
  6. opcionalmente (flag), instalar com `adb install -r` no celular e no relógio.
- **RB-6.2 (P1)** SE qualquer etapa falhar, ENTÃO o script DEVE parar com código de saída diferente de
  zero e mensagem indicando a etapa.
- **RB-6.3 (P1)** `dist-apk/` DEVE estar no `.gitignore`.

**História RB-7.** Como dono do fork, quero atualizar o app sem perder dados.

- **RB-7.1 (P1)** QUANDO a versão N+1 for instalada por cima da N (`adb install -r`, sem
  desinstalar), ENTÃO treinos, rotinas, pesagens e configurações DEVEM permanecer intactos,
  no celular e no relógio.
- **RB-7.2 (P1)** O teste de upgrade N → N+1 DEVE fazer parte do checklist manual do marco M1 e de cada
  marco seguinte.

**História RB-8.** Como dono do fork, quero um guia de instalação em português.

- **RB-8.1 (P1)** DEVE existir `docs/FORK_INSTALL.md`, em português, cobrindo:
  - exportar o backup JSON do app oficial e importá-lo no fork;
  - (opcional) importar o histórico do Hevy pelo importador de CSV que o app já tem
    (Settings → Data & backup), sem código novo;
  - permitir "fontes desconhecidas" e o aviso esperado do Play Protect;
  - ativar depuração USB no celular;
  - ativar opções de desenvolvedor e depuração sem fio no Galaxy Watch, com `adb pair` /
    `adb connect`;
  - instalar o APK do relógio;
  - solução de `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `INSTALL_FAILED_VERSION_DOWNGRADE`, "app não
    instalado" e "relógio não encontra o celular".

**História RB-9.** Como dono do fork, quero respeitar a licença e facilitar o merge com o upstream.

- **RB-9.1 (P1)** `LICENSE`, `NOTICE.md` e a mídia dos exercícios DEVEM permanecer sem modificação.
- **RB-9.2 (P1)** Código novo DEVE ficar em módulos e pastas novos (ex.: `frontend/android/wear/`,
  plugin em pasta própria, `frontend/src/watch/`).
- **RB-9.3 (P1)** Toda alteração em arquivo existente do upstream DEVE ser mínima e marcada com
  `// FORK:` (ou o equivalente de comentário da linguagem).
- **RB-9.4 (P1)** Todas as mudanças do fork DEVEM estar listadas em `FORK.md` na raiz.
- **RB-9.5 (P1)** Specs e docs DEVEM estar em português. Código, comentários e commits em inglês,
  no estilo do upstream (Conventional Commits).

---

### RW — App do relógio

**História RW-1.** Como usuário treinando, quero ver no relógio o que fazer agora, sem pegar o
celular.

- **RW-1.1 (P1)** ENQUANTO houver treino ativo e celular e relógio estiverem conectados, o relógio
  DEVE mostrar o nome do exercício atual, "série X de Y", e a carga e as reps da série atual,
  pré-preenchidas com os valores que o celular já calcula.
- **RW-1.2 (P1) (R2)** O relógio NÃO DEVE calcular progressão, PRs, pré-preenchimento nem qualquer regra de
  treino. Ele DEVE exibir os valores que o snapshot traz. A ordem das séries durante a sessão
  (próxima série, outro lado, rodízio do superset, quando descansar) segue o núcleo da sessão do
  upstream (`WatchCore`), portado sem mudança de comportamento.
- **RW-1.3 (P1)** ENQUANTO o exercício atual fizer parte de um superset, o relógio DEVE indicar o
  superset e mostrar o exercício e a série da vez, na ordem que o celular determina.
- **RW-1.4 (P1)** QUANDO o exercício atual for de tempo, cardio ou unilateral (L/R), ENTÃO o relógio DEVE
  mostrar os campos que o celular mostra para esse tipo (tempo, lado). Se não houver UI própria
  para um tipo, DEVE mostrar um aviso "continue no celular" em vez de dados errados (ver P-5).
- **RW-1.5 (P1)** ENQUANTO não houver treino ativo, o relógio DEVE mostrar uma tela de espera
  ("Nenhum treino ativo — inicie pelo celular").

**História RW-2.** Como usuário, quero ajustar carga e reps pelo bezel e por botões.

- **RW-2.1 (P1)** QUANDO o usuário girar o bezel com o campo de carga em foco, ENTÃO a carga DEVE mudar
  no incremento que o celular usa para aquele exercício (vindo no snapshot).
- **RW-2.2 (P1)** QUANDO o usuário girar o bezel com o campo de reps em foco, ENTÃO as reps DEVEM mudar de
  1 em 1, com mínimo 0.
- **RW-2.3 (P1)** O relógio DEVE ter botões +/− na tela que fazem o mesmo ajuste do bezel.
- **RW-2.4 (P1) (R2)** QUANDO a carga ou as reps forem ajustadas no relógio, ENTÃO os valores DEVEM
  seguir junto com a série concluída (`setDone` com `w`/`r`) e ser gravados no celular quando ele
  aplicar o evento (RS-1.1).

**História RW-3.** Como usuário, quero concluir a série no relógio e descansar.

- **RW-3.1 (P1) (R2)** QUANDO o usuário tocar "Concluir série", ENTÃO o relógio DEVE marcar a série na
  hora, decidir o próximo passo pelo núcleo do upstream e enviar o evento. O celular, ao
  aplicá-lo, DEVE marcar a série com a mesma ação de um toque na tela (`bindWatchActions`).
- **RW-3.2 (P1) (R2)** QUANDO uma série for concluída no relógio e o núcleo pedir descanso, ENTÃO o
  relógio DEVE iniciar o descanso sozinho, com o `restSec` do exercício (ou o global) do snapshot.
  QUANDO o snapshot trouxer um descanso rodando no celular, o relógio DEVE segui-lo.
- **RW-3.3 (P1)** QUANDO o descanso terminar, ENTÃO o relógio DEVE vibrar, mesmo com a tela em modo
  ambiente ou apagada.
- **RW-3.4 (P1) (R2)** QUANDO o usuário tocar +15s, −15s ou "Pular descanso" no relógio, ENTÃO o
  ajuste DEVE valer no relógio. O descanso do celular não é alterado pelo relógio (como no Apple
  Watch do upstream).
- **RW-3.5 (P1) (R2)** QUANDO o descanso for ajustado ou pulado no celular com o relógio conectado,
  ENTÃO o relógio DEVE refletir o novo estado pelo próximo snapshot.

**História RW-4.** Como usuário, quero navegar entre exercícios pelo relógio.

- **RW-4.1 (P1) (R2)** O relógio DEVE permitir trocar o exercício em que está e pular uma série
  ("pular série" deixa a série não feita e mostra a próxima). Isso vale para o relógio. O
  exercício atual do celular não é alterado pelo relógio.
- **RW-4.2 (P1)** O relógio DEVE ter uma lista dos exercícios do treino, com indicação de séries feitas
  sobre o total, que permite pular direto para qualquer um.
- **RW-4.3 (P1) (R2)** QUANDO um exercício for adicionado, removido ou trocado no celular, ENTÃO o
  relógio DEVE refletir a nova lista no próximo snapshot, sem perder as séries que já marcou.

**História RW-5.** Como usuário, quero finalizar o treino pelo relógio.

- **RW-5.1 (P1)** QUANDO o usuário pedir para finalizar no relógio, ENTÃO o relógio DEVE pedir
  confirmação (ex.: segurar ou confirmar) antes de enviar o comando.
- **RW-5.2 (P1) (R2)** QUANDO o evento de finalizar for aplicado no celular, ENTÃO o celular DEVE gravar
  o treino pelo fluxo existente da tela (Finish e salvar), exatamente como por um toque. O relógio
  DEVE mostrar o resumo (duração, séries, PRs) ao finalizar.
- **RW-5.3 (P1)** O relógio NÃO DEVE descartar treinos. Descartar continua exclusivo do celular.
- **RW-5.4 (P1)** QUANDO o treino for finalizado ou descartado no celular, ENTÃO o relógio DEVE voltar à
  tela de espera e encerrar a Ongoing Activity.

**História RW-6.** Como usuário, quero voltar ao treino de qualquer lugar do relógio.

- **RW-6.1 (P1)** ENQUANTO houver treino ativo, o relógio DEVE publicar uma Ongoing Activity (Wear OS
  Ongoing Activity API), visível no mostrador ou nos recentes, com ícone e status (exercício atual
  ou tempo de descanso restante).
- **RW-6.2 (P1)** QUANDO o usuário tocar na Ongoing Activity, ENTÃO o app do relógio DEVE abrir na tela do
  treino.
- **RW-6.3 (P1)** QUANDO o treino terminar, ENTÃO a Ongoing Activity DEVE ser removida.

**História RW-7.** Como usuário, quero abrir o app do relógio a qualquer momento.

- **RW-7.1 (P1)** QUANDO um treino for iniciado no celular com o relógio conectado, ENTÃO o relógio
  DEVE criar a Ongoing Activity sem o usuário abrir o app (abrir a tela automaticamente é
  opcional, ver P-6).

**História RW-8 (P2, R2).** Como usuário, quero começar o treino do dia pelo relógio, sem tirar o
celular do bolso.

- **RW-8.1 (P2)** ENQUANTO não houver treino ativo, o relógio DEVE oferecer as sessões prontas que
  o snapshot traz (`ready`: a de hoje e outras rotinas). Ao iniciar uma, o relógio DEVE conduzir a
  sessão e enviar o evento `start`, que o celular aplica com o mesmo início do app.
- **RW-8.2 (P2)** SE a pesagem antes do treino estiver ligada no app, ENTÃO o relógio DEVE pedi-la
  ao iniciar por ele (`weighIn`).

---

### RS — Sincronização

**História RS-1.** Como usuário, quero que o que faço num lado apareça no outro rapidamente.

- **RS-1.1 (P1) (R2)** Uma ação no relógio DEVE aparecer no próprio relógio na hora. Ela DEVE aparecer no
  celular em até **2 s** (p95) com o openGym em primeiro plano. Com o celular bloqueado ou o app em
  segundo plano, DEVE aparecer **ao abrir o openGym**, sem perda. Uma mudança no celular DEVE
  chegar ao relógio conectado em até 2 s.
- **RS-1.2 (P1)** O celular DEVE ser a fonte da verdade. O estado exibido no relógio DEVE sempre convergir
  para o snapshot mais recente do celular.
- **RS-1.3 (P1) (R2)** A ponte DEVE usar a Wearable Data Layer API: `DataClient` para o snapshot e para
  cada evento do relógio (itens duráveis), e `MessageClient` só para avisos sem estado.
- **RS-1.4 (P1) (R2)** A ponte no celular DEVE ser um plugin Capacitor nativo em Kotlin com a mesma
  interface JS do plugin `WatchBridge` do upstream (`status`, `sendSnapshot`, `drain`, `ack`,
  `openOnWatch`, `sendClips`/`dropClips` sem efeito, eventos `watchEvent` e `watchState`), para o
  JS do upstream rodar sem cópia.
- **RS-1.5 (P1) (R2: a versão é o `v` do snapshot do upstream)** Toda mensagem e todo snapshot DEVEM carregar uma versão de protocolo. QUANDO um lado
  receber uma versão incompatível, ENTÃO ele DEVE ignorar a mensagem e mostrar "atualize o app
  do relógio/celular".
- **RS-1.6 (P1)** Celular e relógio DEVEM ter o mesmo `applicationId` e a mesma chave de assinatura
  (requisito do Data Layer). O script de release verifica isso (RB-6.1).

**História RS-2.** Como usuário, quero que os comandos do relógio funcionem com o celular no
bolso e a tela apagada.

- **RS-2.1 (P1)** QUANDO um comando chegar do relógio com a WebView suspensa ou o app em segundo
  plano, ENTÃO ele NÃO DEVE ser perdido. Ele DEVE ser aplicado na ordem de chegada, assim que o
  JS puder processar, dentro de RS-1.1 sempre que o design permitir.
- **RS-2.2 (P1) (R2: cumprido)** O design DEVE comparar ao menos duas abordagens para esse risco (ex.: foreground
  service durante todo o treino; fila durável no nativo com timer autônomo no relógio e
  reconciliação) e justificar a escolha. A comparação foi medida no spike M2.1 (`FORK.md`,
  `design.md` §6).
- **RS-2.3 (P1)** Toda a lógica de treino DEVE continuar em JavaScript (`frontend/src/lib/` e o fluxo
  existente). O Kotlin só transporta, enfileira e mostra dados.

**História RS-3.** Como usuário, quero que comandos repetidos não dupliquem séries.

- **RS-3.1 (P1)** Cada comando DEVE ter identificador único. QUANDO o mesmo comando chegar mais de uma
  vez, ENTÃO o celular DEVE aplicá-lo só uma vez.
- **RS-3.2 (P1)** Comandos sobre uma série DEVEM identificar a série de forma estável (ex.: id do
  treino + índice do exercício + índice da série, ou equivalente definido no design), para não
  agir na série errada se a lista mudou.

---

### RO — Offline e reconexão

**História RO-1.** Como usuário, quero continuar registrando no relógio se a conexão cair.

- **RO-1.1 (P1)** SE a conexão cair durante um treino ativo, ENTÃO o relógio DEVE continuar
  funcionando com o último snapshot. Ele DEVE registrar localmente, com timestamp e em ordem,
  cada comando (concluir série, ajustes, navegação, descanso, finalizar).
- **RO-1.2 (P1)** ENQUANTO desconectado, o relógio DEVE aplicar os próprios comandos de forma otimista
  na tela e indicar que está offline.
- **RO-1.3 (P1)** ENQUANTO desconectado, o timer de descanso do relógio DEVE funcionar sozinho (iniciar
  ao concluir série, ±15s, pular, vibrar no fim), usando a duração de descanso que veio no
  último snapshot.
- **RO-1.4 (P1) (R2)** QUANDO a conexão voltar, ENTÃO os eventos pendentes DEVEM chegar ao celular em ordem
  (sincronização da Data Layer). Depois de o celular aplicá-los, o relógio DEVE adotar o snapshot do
  celular, mantendo os próprios ticks até o snapshot confirmá-los.
- **RO-1.5 (P1) (R2)** A regra de conflito DEVE ser a do upstream: a mesma série mudada nos dois lados
  → vence a mudança com o `at` mais recente, nos dois lados.
- **RO-1.6 (P1)** A fila offline do relógio DEVE sobreviver ao fechamento do app e ao reinício do
  relógio.
- **RO-1.7 (P1) (R2)** SE o treino tiver sido finalizado no celular enquanto o relógio estava longe, ENTÃO
  as séries marcadas no relógio nesse meio-tempo DEVEM ser gravadas naquele treino salvo (como o
  upstream faz), sem alterar nenhum outro treino. SE ele tiver sido descartado, ENTÃO elas DEVEM
  ser ignoradas.

---

### RE — Bateria e modo ambiente

**História RE-1.** Como usuário, quero deixar o app aberto no treino sem drenar a bateria.

- **RE-1.1 (P1)** ENQUANTO em modo ambiente / always-on, o relógio DEVE mostrar uma versão simplificada
  (exercício, série, timer de descanso), sem animações e com atualização no máximo uma vez por
  minuto, exceto o que o sistema permitir para o timer.
- **RE-1.2 (P1)** O relógio NÃO DEVE manter wake locks, sensores ou conexões ativos fora de um treino
  ativo.
- **RE-1.3 (P1)** O celular NÃO DEVE manter serviço em primeiro plano nem wake lock por causa do relógio
  fora de um treino ativo.
- **RE-1.4 (P1)** O snapshot DEVE ser publicado só quando o estado relevante mudar (não periodicamente
  sem mudança), exceto heartbeat que o design justifique.
- **RE-1.5 (P1)** Meta de consumo: um treino de 60 min com o app do relógio aberto DEVE consumir no máximo
  ~15% da bateria do relógio sem frequência cardíaca (P2 aumenta o orçamento). Será medido no
  checklist manual.

---

### RP2 — Itens P2 (marco M5)

**História RP2-1.** Como usuário, quero ver e guardar minha frequência cardíaca no treino.

- **RP2-1.1 (P2)** ENQUANTO houver treino ativo, o relógio DEVE ler a frequência cardíaca via Health
  Services e mostrá-la na tela do treino.
- **RP2-1.2 (P2)** A frequência cardíaca DEVE ser enviada ao celular e salva junto do treino só se o
  modelo de dados aceitar isso sem quebrar compatibilidade: backups antigos continuam
  importáveis, e backups novos importam no app oficial sem erro (ver P-4).
- **RP2-1.3 (P2)** SE a permissão de sensores corporais for negada, ENTÃO o app DEVE funcionar
  normalmente sem frequência cardíaca.

**História RP2-2.** Como usuário de Samsung, quero o treino ativo na Now Bar do celular.

- **RP2-2.1 (P2)** ENQUANTO houver treino ativo, o celular DEVE promover a notificação do treino ou do
  descanso a notificação ongoing / Live Update, onde o Android e o One UI do aparelho
  suportarem (ver P-2).
- **RP2-2.2 (P2)** SE o aparelho não suportar Live Updates ou Now Bar, ENTÃO o comportamento atual da
  notificação DEVE continuar igual.

---

### RT — Qualidade e testes

- **RT-1 (P1)** O tratamento de comandos no JS DEVE ter testes unitários (vitest), no padrão do
  upstream (`*.test.js` ao lado do código).
- **RT-2 (P1)** A serialização do protocolo (snapshot e comandos) DEVE ter testes Kotlin nos dois lados.
- **RT-3 (P1)** A suíte de testes do frontend do upstream DEVE continuar passando (exceto falhas já
  existentes no upstream antes do fork, registradas no `FORK.md`).
- **RT-4 (P1)** Cada marco DEVE terminar com APKs gerados pelo script de release e um checklist manual
  executado no celular e no relógio reais.
- **RT-5 (P1)** O app do relógio DEVE ser testado em emulador Wear OS e no Galaxy Watch 4 Classic.

## 3. Fora de escopo

- Servidor / self-hosting, sincronização com servidor, PWA. Só o APK Android, em modo local.
- iOS (incluindo Apple Watch).
- Publicação em loja (Play Store, Galaxy Store) e publicação de releases no GitHub.
- Importadores de outros apps além dos que o upstream já tem (ficam para outra spec). Usar o
  importador de CSV do Hevy já existente está dentro do escopo, só como passo do guia.
- Qualquer mudança no modelo de dados que quebre backups existentes ou a importação no app
  oficial.
- Iniciar um treino pelo relógio, montar rotinas ou ver histórico/estatísticas no relógio.
- Tiles e complicações do Wear OS (além da Ongoing Activity).
- Funcionamento do relógio sem celular por perto desde o início do treino (modo standalone),
  exceto o início pelo relógio do RW-8 (P2), que usa as sessões prontas do último snapshot.
- Mudanças em `api/`, `web/`, `helm/`, `kubernetes/`, Docker e CI do upstream.
- Alterar a lógica de treino em `frontend/src/lib/` (só código novo que a usa).
- Tradução da UI do relógio para todos os idiomas do upstream. O relógio terá pt-BR e inglês.

## 4. Decisões (antes "Perguntas em aberto")

Aprovadas pelo dono em 2026-10-13, com as recomendações do assistente.

| # | Tema | Decisão |
|---|---|---|
| D-1 | Versão do Wear OS | App Wear com `minSdk 30` (Wear OS 3+), `targetSdk`/`compileSdk 35`. A versão real do relógio é confirmada via `adb` no M2 e registrada no `FORK.md`. |
| D-2 | Celular / Now Bar (P2) | Modelo e versão de One UI ainda não informados. O design trata isso de forma genérica: Live Update (Android 16 `setRequestPromotedOngoing` + `ProgressStyle`) quando a API existir, notificação ongoing comum caso contrário. Confirmar o aparelho via `adb` no M1. |
| D-3 | Verificador de atualização | Aponta para `Cezarmvm/openGym` (troca de uma constante em `update.js`, marcada `// FORK:`). Como o fork não publica releases, ele responde "sem atualização". |
| D-4 | Frequência cardíaca (P2) | Mostrar no relógio sempre. Salvar no treino só como campo extra opcional (`hr`), e só se o M5 provar que backups com o campo importam sem erro no app oficial. Caso contrário, fica só no relógio. |
| D-5 | Tipos de série no relógio | UI completa para séries de reps+carga, incluindo unilateral L/R e aquecimento. Tempo, cardio, drop set e rest-pause têm UI básica (ver valores, concluir) e "editar no celular" para o resto. Séries de tempo concluídas pelo relógio não disparam o cronômetro de hold: são marcadas como feitas com o tempo planejado. |
| D-6 | Abrir o app do relógio sozinho | Não. Ao iniciar o treino, o relógio mostra a Ongoing Activity e vibra uma vez. O usuário toca para abrir. |
| D-7 | Incremento de carga | O mesmo passo que o celular usa no stepper do exercício (`weightIncrement`), calculado no JS e enviado no snapshot. |
| D-8 | `applicationId` | `com.cezar.opengym` (release) e `com.cezar.opengym.test` (debug), iguais no celular e no relógio. |
| D-9 | Specs públicos | Sim, no fork público `Cezarmvm/openGym`. |
| D-10 | Nome visível do app | "openGym", sem sufixo (decisão do dono em 2026-10-14). O fork se distingue do oficial só pelo `applicationId`. |
| D-11 | Como o JS do relógio do upstream entra no fork (R2) | **Pendente.** (a) `merge` do branch `upstream/watch` no `fork/main` agora (recomendado) ou (b) um branch de integração até o upstream lançar. Ver `design.md` §14.1. |
| D-12 | Formato da sincronização (R2) | O do upstream (Apple Watch): relógio autônomo, eventos `setDone`/`finish`/`start`, fila durável aplicada quando o celular abre. Decisão do dono em 2026-10-14, depois do spike M2.1. |
