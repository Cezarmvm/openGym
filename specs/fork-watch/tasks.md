# Tarefas — fork do openGym com APK próprio + app para Galaxy Watch

> Spec `fork-watch` · Fase 3 (Tarefas) · Requisitos e design aprovados em 2026-10-13
> Status: R1 aprovada em 2026-10-13 · **M2–M5 reescritos na Revisão 2 (2026-10-14), aguardando aprovação**

## Como ler

- Cada tarefa traz: **Arquivos**, **Aceite** (critério verificável) e **Req** (requisitos/seções do design que atende).
- Ordem: de cima para baixo dentro de cada marco. Um marco só começa depois de o anterior ser confirmado no aparelho.
- Ao terminar cada tarefa: testes/lint relevantes → marcar `[x]` → commit (Conventional Commits, em inglês) → linha `✅ [tarefa] — [como verifiquei]`.
- Ao terminar cada marco: APKs pelo script de release → checklist manual → **PARE** até o dono confirmar.
- 🛑 = a tarefa exige uma ação do dono. O assistente para e pede.
- Convenção de commit: `feat(fork-…)`, `fix(fork-…)`, `docs(fork)`, `build(fork)`, `test(fork-…)`, `chore(fork)`.

---

## M1 — APK próprio assinado + script de release + guia de instalação

Termina com: APK `com.cezar.opengym` assinado com a chave do dono, instalado no celular, atualizando por cima (N → N+1) sem perder dados.

- [x] **M1.1 — Base do fork: `FORK.md`, `.gitignore` e config local do git**
  - Arquivos: `FORK.md` (novo), `.gitignore` (bloco `# FORK:`), config local `core.autocrlf=input`
  - Aceite: `.gitignore` ignora `fork/keystore.properties`, `*.jks`, `*.keystore` e `dist-apk/`. `git check-ignore` confirma cada padrão. `FORK.md` tem: objetivo, tabela "pontos FORK" (vazia por enquanto), versões de ferramentas (cmdline-tools 15859902, JDK 21, build-tools 35), as 2 falhas de teste que o upstream já tem no Windows e o procedimento de merge (design §14).
  - Req: RB-2.4, RB-6.3, RB-9.4, RT-3, design §9.6

- [x] **M1.2 — Estrutura `fork/android/` e ganchos Gradle**
  - Arquivos: `fork/android/fork-settings.gradle` (por enquanto vazio, só comentário), `fork/android/fork-app.gradle`, `frontend/android/settings.gradle` (+1 linha FORK), `frontend/android/app/build.gradle` (+1 linha FORK)
  - Aceite: `./gradlew :app:assembleDebug` compila. `git diff upstream/main -- frontend/android` mostra só as 2 linhas marcadas.
  - Req: RB-9.2, RB-9.3, design §9.1

- [x] **M1.3 — `applicationId` e nome visível**
  - Arquivos: `fork/android/fork-app.gradle` (a sobreposição `release-res` foi removida pela D-10)
  - Aceite: `aapt2 dump badging` do release mostra `package: name='com.cezar.opengym'` e `application-label:'openGym'` (D-10). O debug mostra `com.cezar.opengym.test`. O `namespace` continua `ch.duartesantos.opengym`.
  - Req: RB-1.1, RB-1.3, D-8

- [x] **M1.4 — Versionamento automático**
  - Arquivos: `fork/android/fork-version.gradle`, `fork/android/version.properties` (`forkBuild=0`), `fork/android/fork-app.gradle`
  - Aceite: com `forkBuild=1`, `aapt2 dump badging` mostra `versionCode='26001'` e `versionName='1.4.1-cezar.1'`. Um teste Gradle simples (task `forkVersionCheck`) falha se o `versionCode` calculado for ≤ ao último gravado em `fork/android/last-version-code` (arquivo atualizado pelo script de release).
  - Req: RB-3.1, RB-3.3, design §9.3

- [x] **M1.5 — Assinatura release pelo Gradle**
  - Arquivos: `fork/android/fork-signing.gradle`, `fork/android/fork-app.gradle`, `fork/keystore.properties.example`
  - Aceite: (a) sem `keystore.properties`, `assembleRelease` falha na task `forkCheckSigning` com a mensagem apontando o guia. (b) Com uma keystore **de teste** gerada pelo assistente em `%TEMP%` (descartada depois), `assembleRelease` gera `app-release.apk` e `apksigner verify -v` mostra v1, v2 e v3 = true. Nada de debug key.
  - Req: RB-2.1, RB-2.2, RB-2.3, design §9.2

- [x] **M1.6 — 🛑 Keystore real do dono**
  - Arquivos: nenhum no repo. Fica em `C:\Users\Cezar\keys\opengym-cezar.jks` e `fork/keystore.properties` (ignorado).
  - Aceite: o assistente mostra o comando `keytool` exato (design §9.2) e espera. O dono roda e preenche `fork/keystore.properties` a partir do `.example`. O assistente confere `git status` (nada novo rastreado) e `keytool -list` (alias certo). O assistente lembra do **backup da keystore e das senhas** (cópia fora do PC, ex.: pendrive + gerenciador de senhas).
  - Req: RB-2.4, RB-2.5

- [x] **M1.7 — Verificador de atualização do fork**
  - Arquivos: `frontend/src/lib/update.js` (1 linha FORK), `FORK.md`
  - Aceite: `REPO = 'Cezarmvm/openGym'`. Um teste vitest novo (`frontend/src/watch/fork-update.test.js`) confirma que `LATEST_URL` aponta para o fork. A suíte do upstream continua com o mesmo resultado de antes (5054/5055 e as mesmas 2 falhas conhecidas).
  - Req: RB-4.1, RB-4.2, D-3

- [x] **M1.8 — Script de release (fase celular)**
  - Arquivos: `scripts/fork-release.sh`, `scripts/fork-release.ps1`
  - Aceite: no Windows, `scripts/fork-release.ps1` (e `bash scripts/fork-release.sh`) faz: checagem de ferramentas → incremento do `forkBuild` → build mobile equivalente ao `build:mobile` → `:app:assembleRelease` → `apksigner verify --print-certs` → `aapt2 dump badging` (confere o pacote) → copia para `dist-apk/openGym-Cezar-phone-<vn>-<vc>.apk` + `.sha256` → commit `chore(fork): release <vn>`. Qualquer erro sai com código ≠ 0 e o nome da etapa (testado forçando a falta do `keystore.properties`). `--install` instala com `adb install -r`. O suporte ao relógio fica reservado (flags aceitas, etapa ignorada até o M2).
  - Req: RB-6.1 (1, 3–6), RB-6.2, design §9.5

- [x] **M1.9 — Identificar o celular**
  - Arquivos: `FORK.md` (seção "Aparelhos")
  - Aceite: com o celular em depuração USB, registrar `ro.product.model`, `ro.build.version.release`/`sdk` e a versão do One UI (`ro.build.version.oneui`). Isso decide a viabilidade do P2 da Now Bar (D-2).
  - Req: D-2, RB-5

- [x] **M1.10 — Guia `docs/FORK_INSTALL.md` (parte celular)**
  - Arquivos: `docs/FORK_INSTALL.md`
  - Aceite: em português. Cobre: keystore (criação, backup, `keystore.properties`), exportar o backup JSON do oficial e importar no fork, importar o CSV do Hevy (opcional), fontes desconhecidas, aviso do Play Protect, depuração USB, bateria "Sem restrições" (Samsung) e os erros `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `INSTALL_FAILED_VERSION_DOWNGRADE` e "app não instalado". As seções do relógio ficam como esqueleto, preenchidas no M2/M3.
  - Req: RB-8.1, design §6 (Samsung)

- [x] **M1.11 — Release N e N+1 + checklist manual M1**
  - Arquivos: nenhum (execução), `FORK.md` (registro)
  - Aceite: `fork-release --install` gera e instala a versão N. O dono importa o backup e cria um dado. Uma segunda execução gera N+1, e `adb install -r` instala por cima sem desinstalar. Os dados continuam lá. O app oficial (se instalado) continua intacto ao lado.
  - Req: RB-1.2, RB-7.1, RB-7.2, RT-4
  - **Checklist manual M1 (o dono):** (1) o app abre como "openGym"; (2) importar o backup do oficial; (3) Settings → Sobre → "procurar atualização" diz que não há atualização, sem erro; (4) instalar N+1 por cima: dados preservados; (5) o app oficial continua abrindo com os próprios dados.

**🛑 PARE — fim do M1. Aguardar confirmação do dono.**

---

## M2 — JS do upstream + ponte Data Layer + app Wear mínimo

> **Revisão 2.** Tarefas reescritas depois do spike M2.1 (ver `design.md` §0). As tarefas da R1
> M2.2–M2.11 e M2.13 foram substituídas pelas de baixo.

Termina com: o app mínimo do relógio mostra o treino ativo e conclui uma série. Com o celular
bloqueado, ela aparece no celular ao abrir o app, sem perda.

- [x] **M2.1 — Spike: JS em segundo plano com FGS (risco R-1)**
  - Resultado: **falhou** (o JS para em < 1 min com a tela apagada, mesmo com FGS e renderer fixo). Registrado no `FORK.md`. Branch local `spike/bg-js`. A spec foi revista (R2).
  - Req: RS-1.1, RS-2.1, RS-2.2, design §6

- [x] **M2.2 — 🛑 Parear o relógio por adb + identificar Wear OS** (antes M2.12)
  - Resultado: Galaxy Watch 4 Classic `SM-R890`, Wear OS 6 / Android 16 (SDK 36). `FORK.md` e `docs/FORK_INSTALL.md` atualizados.
  - Req: RB-5.2, RB-8.1, D-1

- [ ] **M2.3 — Trazer o JS do relógio do upstream (D-11)**
  - Arquivos: merge do `upstream/watch` (D-11 a) ou branch `fork/watch-next` (D-11 b). Conflitos nos 5 pontos FORK (`lib/health-sync.js`, `lib/health-sync.test.js`, `views/Settings.jsx`, `MainActivity.java`, `settings.gradle`). `FORK.md` (tabela FORK, nota do branch seguido) e `fork/android/*.gradle`, se o upstream trouxer *flavors* ou AGP novo.
  - Aceite: `npm test` com só as falhas já conhecidas do Windows, ou com as falhas novas investigadas e registradas. `fork-release.sh` gera o APK `sideload` assinado. Antes de instalar, exportar o backup do app para fora do repo. Upgrade sobre o `1.4.1-cezar.5` sem perder dados (treinos, rotina, medidas, Health Connect ligado). `versionCode` maior que o instalado.
  - Req: A-1, A-7, RB-3, RB-7, RT-3, design §14

- [ ] **M2.4 — Kotlin e plugins de build**
  - Arquivos: `frontend/android/build.gradle` (+3 linhas FORK de classpath), `fork/android/fork-settings.gradle`
  - Aceite: `./gradlew help` funciona. `:app:assembleSideloadDebug` (ou `:app:assembleDebug`) continua compilando. Versões registradas no `FORK.md`.
  - Req: design §9, D-1, R-5

- [ ] **M2.5 — Módulo `:watch-core`: modelos + fixtures**
  - Arquivos: `frontend/android/watch-core/**` (Kotlin/JVM, kotlinx-serialization: `Snapshot`, `ActiveWorkout`, `Entry`, `SetRow`, `Sides`, `Ready`, `ReadySession`, `WatchSettings`, `Event`), `frontend/src/fork/watch-fixtures.test.js` (gera `watch-core/src/test/resources/fixtures/*.json` com o `buildSnapshot`/`fitSnapshot` do upstream)
  - Aceite: fixtures do Treino A real e de superset, por lado, tempo, cardio, aquecimento, `ready` e sem treino. `:watch-core:test` lê todas, ignora um campo desconhecido injetado e trata `v` ≠ 1 como incompatível. O JSON de cada `Event` serializado em Kotlin é aceito pelo `applyEvents` do upstream: o teste vitest lê as fixtures `events/*.json` geradas pelo Kotlin.
  - Req: RS-1.5, RT-2, design §3.2, §3.3, R-3

- [ ] **M2.6 — Módulo `:fork-wearbridge`: `WatchBridgePlugin`, `EventInbox`, listener**
  - Arquivos: `frontend/android/fork-wearbridge/**` (`WatchBridgePlugin`, `EventInbox`, `PhoneListenerService`, `SnapshotPublisher`, manifest e `res/values/wear.xml`), `fork/android/fork-app.gradle` (dependência), `MainActivity.java` (+1 linha FORK)
  - Aceite: testes JVM da `EventInbox` (ordem, dedupe, persistência, recuperação recriando a instância, `ack` remove). No celular: `WatchBridge.status()` responde pelo `chrome://inspect` e `sendSnapshot` grava o item (`adb shell dumpsys activity service WearableService` ou o log do listener do relógio).
  - Req: RS-1.3, RS-1.4, RS-2.1, design §2.3, §3.4

- [ ] **M2.7 — Liberar o `watch-sync.js` no Android**
  - Arquivos: `frontend/src/lib/watch-sync.js` (1 linha FORK), `frontend/src/views/Settings.jsx` (1–2 linhas FORK), `frontend/src/views/WatchSettings.jsx` (bloco FORK de rótulos), `frontend/src/fork/strings.js`, `frontend/src/fork/watch-android.test.js`
  - Aceite: vitest com `nativePlatform()='android'` e um plugin falso: o snapshot é enviado ao iniciar um treino, `drain` → `applyEvents` → `ack` só depois do `saveNow`. Configurações → relógio aparece no Android com textos em pt-BR ("relógio", "Conexão Saúde"). Os testes de relógio do upstream continuam passando.
  - Req: A-1, RS-1.4, design §2.3, §10

- [ ] **M2.8 — Módulo `:wear` mínimo**
  - Arquivos: `frontend/android/wear/**` (`MainActivity` Compose com o texto do exercício e da série atuais + botão "Concluir série", `WatchListenerService`, `SessionStore` mínimo, `EventSender`), `fork/android/fork-settings.gradle`, assinatura e versão compartilhadas
  - Aceite: `:wear:assembleDebug` e `:wear:assembleRelease` compilam com `applicationId`, assinatura e `versionCode` iguais aos do celular. No relógio real: o snapshot do Treino A aparece. "Concluir" grava um `setDone` (`DataItem`), e o celular o recebe na `EventInbox` com o app em segundo plano (`logcat`).
  - Req: RS-1.3, RS-1.6, RB-3.2, design §2.3, §9

- [ ] **M2.9 — Script de release com o relógio**
  - Arquivos: `scripts/fork-release.sh` (+ `.ps1`)
  - Aceite:
    - compila `:wear:assembleRelease`;
    - compara o SHA-256 dos certificados dos dois APKs (falha se diferentes; testado assinando um com uma keystore de teste);
    - confere pacote e `versionCode` iguais via `aapt2`;
    - copia `openGym-Cezar-watch-*.apk`;
    - `--install` detecta o relógio por `ro.build.characteristics` (contém `watch`) ou usa `--watch SERIAL`;
    - roda os testes Kotlin (`:watch-core:test`, `:fork-wearbridge:testDebugUnitTest`, `:wear:testDebugUnitTest`).
  - Req: RB-6.1 (2–6), RS-1.6

- [ ] **M2.10 — Checklist manual M2**
  - **Checklist (o dono):**
    1. Instalar celular + relógio pelo script (`--install`).
    2. Iniciar o Treino A no celular → o app mínimo do relógio mostra exercício e série.
    3. Bloquear o celular, esperar 1 min, tocar "Concluir série" no relógio 3× → ao desbloquear e abrir o openGym, as 3 séries estão feitas.
    4. Matar o app do celular (recentes), tocar "Concluir" no relógio, reabrir o celular → a série aparece.
    5. Upgrade N → N+1 dos dois sem perda.
  - Req: RS-1.1, RS-2.1, RB-7, RT-4

**🛑 PARE — fim do M2. Aguardar confirmação do dono.**

---

## M3 — O treino inteiro no relógio

Termina com: o treino conduzido inteiro pelo relógio (séries, cargas pelo bezel, superset, lados,
descanso com vibração, finalizar com resumo), com o celular no bolso.

- [ ] **M3.1 — `SessionCore` (port do upstream) + testes**
  - Arquivos: `frontend/android/watch-core/src/main/.../SessionCore.kt`, `SessionState.kt`, `src/test/.../SessionCoreTest.kt`, `SetTypesTest.kt`
  - Aceite: `applySnapshot`, `tick`, `markSide`, `skip`, `next(after)`, `setIndex`, `start`, `finish`, `weighIn`, `progress`, `summary` e `beatsBest`, com o mesmo comportamento do Swift. Os casos de `SessionCoreTests.swift` e `SetTypesTests.swift` portados um a um passam. As funções de Apple Health ficam de fora (registrado no cabeçalho do arquivo).
  - Req: RW-1.2, RW-1.3, RW-3.1, RW-4.1, RO-1.4, RO-1.5, RO-1.7, design §5.1

- [ ] **M3.2 — `SessionStore` completo + `EventSender`**
  - Arquivos: `wear/.../data/SessionStore.kt`, `EventSender.kt`, testes JVM
  - Aceite: o estado é salvo em arquivo a cada mudança e restaurado ao abrir. Snapshot novo → `applySnapshot` mantém os pendentes. Cada evento vira um `DataItem` com id único. Um item apagado pelo celular confirma o evento. Teste: "app morto no meio do treino → reabre no mesmo ponto".
  - Req: RO-1.1, RO-1.6, RS-3.1, design §2.3, §7

- [ ] **M3.3 — Base da UI: tema, pager, estados**
  - Arquivos: `wear/.../ui/App.kt`, `ui/theme/*`, `res/values{,-pt-rBR}/strings.xml` (aproveitando o `pt-BR.lproj/Localizable.strings` do upstream)
  - Aceite: pager Série / Descanso / Lista / Finalizar; "Nenhum treino ativo"; "Atualize o app" para `v` ≠ 1; indicador "sem conexão". Idioma pelo `settings.lang`. Previews Compose de cada estado.
  - Req: RW-1.5, RS-1.5, RO-1.2, design §8

- [ ] **M3.4 — Página Série + bezel**
  - Arquivos: `wear/.../ui/SetPage.kt`, `ui/Rotary.kt`
  - Aceite:
    - mostra nome, superset, "Série X de Y"/"Aquec.", carga e reps;
    - o bezel ajusta o campo em foco (carga ± `step`, reps ± 1, mínimo 0), com haptic por passo, e `settings.crown` define o campo inicial;
    - +/− na tela;
    - "Concluir" chama `tick`;
    - "Pular série" chama `skip`;
    - layout por lado E/D, tempo e cardio conforme o D-5;
    - números ≥ 32 sp;
    - conferido no relógio.
  - Req: RW-1.1, RW-1.3, RW-1.4, RW-2.1–2.3, RW-3.1, RW-4.1, D-5, D-7, design §8, §8.1

- [ ] **M3.5 — Descanso: página, timer local e vibração**
  - Arquivos: `wear/.../ui/RestPage.kt`, `wear/.../rest/RestTimer.kt`, `RestAlarmReceiver.kt`, manifest (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, `VIBRATE`)
  - Aceite:
    - arco e tempo restante;
    - próxima série;
    - −15 s / Pular / +15 s locais;
    - o pager vai para o Descanso quando ele começa e volta quando acaba;
    - segue o `rest.endsAt` do snapshot quando o celular tiver descanso rodando;
    - vibra 300-150-300 no fim com a tela apagada, se `restHaptics`;
    - testes JVM com relógio falso.
  - Req: RW-3.2–3.5, RO-1.3, design §5.3

- [ ] **M3.6 — Página Lista e página Finalizar + resumo**
  - Arquivos: `wear/.../ui/ListPage.kt`, `FinishPage.kt`, `SummaryPage.kt`
  - Aceite:
    - **Lista:** nome, feitas/total, ✓, o atual destacado; tocar troca o exercício do relógio.
    - **Finalizar:** segurar 1,5 s; soltar antes não envia nada; envia `finish` e mostra o resumo (duração, séries, PRs); sem "Descartar".
    - Snapshot sem o treino (finalizado no celular) → resumo "finalizado no celular" e depois a espera.
  - Req: RW-4.2, RW-5.1–5.4

- [ ] **M3.7 — Release + checklist manual M3**
  - **Checklist (o dono):** itens 2 a 7 do design §12, com o Treino A, mais um superset montado na rotina para o teste.
  - Req: RW-1..5, RS-1, RO-1, RT-4

**🛑 PARE — fim do M3. Aguardar confirmação do dono.**

---

## M4 — Ongoing Activity, ambiente, robustez e guia

Termina com: o relógio integrado ao sistema (Ongoing Activity, always-on), robusto a desconexão e
reinício, e o guia de instalação completo.

- [ ] **M4.1 — Ongoing Activity + `WatchWorkoutService` + `/open`**
  - Arquivos: `wear/.../session/OngoingWorkout.kt`, `WatchWorkoutService.kt`, `WatchListenerService.kt`, manifest
  - Aceite:
    - treino iniciado no celular com o app do relógio fechado → Ongoing Activity (ícone + status) e 1 vibração (D-6, `openOnWatch`);
    - no descanso, o status conta com `TimerPart`;
    - tocar abre o app na página Série;
    - com o app aberto, o FGS assume a notificação;
    - fim do treino remove tudo;
    - nenhum FGS ou wake lock fora de treino (`dumpsys`).
  - Req: RW-6.1–6.3, RW-7.1, RE-1.2, D-6, design §5.2, R-4

- [ ] **M4.2 — Modo ambiente**
  - Arquivos: `wear/.../ui/AmbientScreen.kt`, `App.kt`
  - Aceite: fundo preto, texto fino, sem botões, atualização ≈ 1/min, anti burn-in. Sai do ambiente sem perder o estado. Conferido com always-on ligado.
  - Req: RE-1.1, design §8.2

- [ ] **M4.3 — Robustez offline**
  - Arquivos: testes JVM do `SessionStore`/`watch-core`, `FORK.md` (resultados)
  - Aceite: no aparelho: (a) modo avião no relógio com 2 séries → tudo chega sem duplicar; (b) reiniciar o relógio no meio do treino → volta no ponto, com as séries pendentes ainda pendentes; (c) finalizar no celular com o relógio longe → as séries do relógio entram no treino salvo; (d) descartar no celular → o relógio volta à espera sem gravar nada.
  - Req: RO-1.1–1.7

- [ ] **M4.4 — `FORK_INSTALL.md` completo**
  - Arquivos: `docs/FORK_INSTALL.md`
  - Aceite: seções do relógio completas, incluindo "relógio não encontra o celular" (mesmo `applicationId` e assinatura, Galaxy Wearable conectado, reinstalar os dois pelo script, `adb logcat -s OpenGymWear`) e "as séries só aparecem no celular quando abro o app" (é o esperado, R2).
  - Req: RB-8.1

- [ ] **M4.5 — P2: iniciar pelo relógio (`ready`, `start`, `weighIn`)**
  - Arquivos: `wear/.../ui/StartPage.kt`, `watch-core` (`start`, `weighIn` já portados)
  - Aceite: sem treino ativo, o relógio lista a sessão de hoje e outras rotinas do snapshot. Iniciar → a sessão roda no relógio e, ao abrir o celular, o treino está ativo com o mesmo id e início. Com a pesagem ligada, pede o peso pelo bezel.
  - Req: RW-8.1, RW-8.2

- [ ] **M4.6 — Release + checklist manual M4**
  - **Checklist (o dono):**
    - (a) Ongoing Activity aparece sem abrir o app e leva ao treino;
    - (b) always-on por 10 min sem travar;
    - (c) itens a–d do M4.3;
    - (d) bateria do relógio antes e depois de 60 min (meta ≤ 15%);
    - (e) iniciar o treino pelo relógio (se o M4.5 foi feito);
    - (f) upgrade N → N+1 dos dois.
  - Req: RW-6, RW-8, RO-1, RE-1, RB-7, RT-4

**🛑 PARE — fim do M4. Aguardar confirmação do dono.**

---

## M5 — P2: frequência cardíaca e Live Update

- [ ] **M5.1 — Portão de compatibilidade do `hr`**
  - Aceite: o `hr`/`restHr` nas séries é do próprio upstream (`lib/heart-rate.js`). Conferir que um backup com esses campos importa no app oficial da versão com relógio. Registrar no `FORK.md`.
  - Req: RP2-1.2, D-4

- [ ] **M5.2 — Health Services no relógio + `setHr`**
  - Arquivos: `wear/.../health/HeartRateSession.kt`, `WatchWorkoutService.kt` (tipo `health`), manifest (`BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE_HEALTH`)
  - Aceite: com o treino ativo e o app aberto, a FC aparece na página Série. O `setDone` leva `hr`, e o `setHr` leva o `restHr` depois do descanso. Sem permissão, funciona sem FC. Sem sensores fora do treino.
  - Req: RP2-1.1, RP2-1.3, RE-1.2, design §11.1

- [ ] **M5.3 — Live Update / Now Bar (a definir)**
  - Aceite: escolher entre as opções do design §11.2 e implementar. Conferir no S25 Ultra (One UI 9).
  - Req: RP2-2.1, RP2-2.2

- [ ] **M5.4 — Release + checklist manual M5**
  - **Checklist (o dono):**
    1. Aceitar as permissões → FC no relógio.
    2. Negar → o app funciona sem FC.
    3. Treino com FC por série no histórico.
    4. Now Bar, se houver.
    5. Bateria de 60 min com FC.
    6. Upgrade N → N+1.
  - Req: RP2-1, RP2-2, RT-4

**🛑 PARE — fim do M5. Aguardar confirmação do dono.**

---

## Rastreabilidade (requisito → tarefas)

| Req | Tarefas |
|---|---|
| RB-1 | M1.3, M1.11 |
| RB-2 | M1.1, M1.5, M1.6 |
| RB-3 | M1.4, M2.3, M2.8 |
| RB-4 | M1.7 |
| RB-5 | M1.9, M2.2 |
| RB-6 | M1.1, M1.8, M2.9 |
| RB-7 | M1.11, M2.3, M2.10, M3.7, M4.6, M5.4 |
| RB-8 | M1.10, M2.2, M4.4 |
| RB-9 | M1.1, M1.2 (e o padrão FORK em todas) |
| RW-1 | M2.8, M3.1, M3.3, M3.4 |
| RW-2 | M3.4 |
| RW-3 | M3.1, M3.4, M3.5 |
| RW-4 | M3.1, M3.4, M3.6 |
| RW-5 | M3.6 |
| RW-6 / RW-7 | M4.1 |
| RW-8 | M4.5 |
| RS-1 | M2.1, M2.5, M2.6, M2.7, M2.8, M2.10 |
| RS-2 | M2.1, M2.6, M2.10 |
| RS-3 | M2.6, M3.2 |
| RO-1 | M3.1, M3.2, M4.3 |
| RE-1 | M4.1, M4.2, M4.6 |
| RP2-1 | M5.1, M5.2 |
| RP2-2 | M1.9, M5.3 |
| RT-1 | M2.5, M2.7 |
| RT-2 | M2.5, M3.1 |
| RT-3 | M1.1, M1.7, M2.3, M2.7 |
| RT-4 | M1.11, M2.10, M3.7, M4.6, M5.4 |
| RT-5 | M3.3 |
