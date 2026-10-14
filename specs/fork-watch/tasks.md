# Tarefas — fork do openGym com APK próprio + app para Galaxy Watch

> Spec `fork-watch` · Fase 3 (Tarefas) · Requisitos e design aprovados em 2026-10-13
> Status: **aprovado em 2026-10-13**

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

## M2 — Plugin Data Layer + integração JS (testável com app Wear mínimo)

Termina com: um app Wear mínimo mostra o treino ativo (texto) e um botão "concluir série" funciona com a tela do celular apagada.

- [ ] **M2.1 — Spike: JS em segundo plano com FGS (risco R-1)**
  - Arquivos: branch descartável `spike/bg-js` (não entra no `fork/main`). Os resultados vão para o `FORK.md`.
  - Aceite: no celular real, com a tela apagada por ≥ 5 min durante um treino, um FGS mínimo + uma mensagem `adb shell am broadcast` → plugin → JS altera o store e escreve log com timestamp. Medir a latência. **Se falhar**, PARE e proponha a atualização da spec (design §13 R-1) antes de seguir.
  - Req: RS-1.1, RS-2.1, design §6

- [ ] **M2.2 — Kotlin e plugins de build**
  - Arquivos: `frontend/android/build.gradle` (+3 linhas FORK de classpath: Kotlin 2.0.21, compose compiler, serialization), `fork/android/fork-settings.gradle`
  - Aceite: `./gradlew help` funciona. `:app:assembleDebug` continua compilando. Versões registradas no `FORK.md`.
  - Req: design §9.1, D-1

- [ ] **M2.3 — Módulo `:fork-protocol` (modelos + testes)**
  - Arquivos: `frontend/android/fork-protocol/**` (Kotlin/JVM, kotlinx-serialization), `fork/android/fork-settings.gradle`
  - Aceite: classes `Snapshot`, `EntryView`, `SetView`, `RestView` e `Command` (sealed, os 9 tipos do design §3.3), `Envelope`, `Result` e `ProtocolVersion = 1`. Testes: ida e volta de cada tipo, `v` ≠ 1 → `Incompatible`, campos desconhecidos ignorados. `./gradlew :fork-protocol:test` verde.
  - Req: RS-1.5, RT-2, design §3

- [ ] **M2.4 — `buildSnapshot` (JS puro) + fixtures**
  - Arquivos: `frontend/src/watch/snapshot.js`, `frontend/src/watch/snapshot.test.js`, `frontend/android/fork-protocol/src/test/resources/fixtures/*.json` (gerados pelo teste JS)
  - Aceite: casos: sem treino, edição ou backfill → `active:false`. Treino simples, superset, unilateral, aquecimento, `focus` = `nextOpenSet`, `wStep` = `weightIncrement`, `restHint`, `rest` rodando ou pausado, `editable` por tipo (D-5) e corte de tamanho > 90 KB. O vitest grava as fixtures, e um teste do `:fork-protocol` as lê sem erro.
  - Req: RW-1.1–1.4, RW-1.2, RT-1, RT-2, design §3.2

- [ ] **M2.5 — `validateCommand` + memória de ids (JS puro)**
  - Arquivos: `frontend/src/watch/commands.js` (parte pura), `frontend/src/watch/applied.js`, `frontend/src/watch/commands.test.js`
  - Aceite: rejeita `bad-version`, `no-workout`, `workout-ended` (wid), `target-moved`, `out-of-range` e `not-editable`. Resolve o alvo por `exId` quando o índice mudou. Ignora id repetido (inclusive após recarregar, porque persiste em `localStorage`). `finish` de replay só se for o último.
  - Req: RS-1.5, RS-3.1, RS-3.2, RO-1.7, design §3.3, §7.3

- [ ] **M2.6 — Módulo `:fork-wearbridge`: plugin, inbox e listener**
  - Arquivos: `frontend/android/fork-wearbridge/**` (`WearBridgePlugin`, `CommandInbox`, `PhoneListenerService`, `SnapshotPublisher`, manifest e `res/values/wear.xml`), `fork/android/fork-app.gradle` (dependência), `MainActivity.java` (+2 linhas FORK)
  - Aceite: testes JVM do `CommandInbox` (ordem, dedupe, persistência e recuperação recriando a instância). `:app:assembleDebug` inclui o módulo. Os métodos `publish`, `ack`, `pending` e `status` e o evento `command` estão disponíveis no JS (conferido por log no WebView com `chrome://inspect`).
  - Req: RS-1.3, RS-1.4, RS-2.1, design §2.3, §4.3

- [ ] **M2.7 — `WorkoutSessionService` (FGS do treino)**
  - Arquivos: `frontend/android/fork-wearbridge/.../WorkoutSessionService.kt`, manifest do módulo
  - Aceite: `startSession` mostra uma notificação silenciosa "Treino em andamento" e o serviço aparece em `adb shell dumpsys activity services com.cezar.opengym`. `stopSession` remove ambos. Fora de treino, nenhum FGS do fork.
  - Req: RS-2.1, RE-1.3, design §4.2, §6

- [ ] **M2.8 — Ponte JS: publicar snapshot e receber comandos**
  - Arquivos: `frontend/src/watch/index.js`, `frontend/src/watch/bridge.js`, `frontend/src/main.jsx` (+1–2 linhas FORK)
  - Aceite: `subscribe` nos stores → snapshot com debounce de 150 ms, publicado só quando muda (teste vitest com o plugin falso conta as publicações). Liga e desliga a sessão quando `S.active` aparece ou some. No boot, drena `pending()`. Fora do Android/MOBILE, não faz nada (teste).
  - Req: RS-1.2, RE-1.3, RE-1.4, design §3.2, §4.2

- [ ] **M2.9 — Gancho no `ActiveWorkout` + `doFinishWorkout` exportado**
  - Arquivos: `frontend/src/views/Workout.jsx` (bloco FORK ~30 linhas), `frontend/src/sheets.jsx` (1 palavra FORK), `frontend/src/watch/commands.js` (despacho), `frontend/src/watch/hook.test.jsx`
  - Aceite (teste de integração, happy-dom, no estilo dos `sheets.*.test.jsx`): `completeSet` deixa o mesmo estado que um clique no botão da série, incluindo descanso iniciado e avanço em superset. Também cobre:
    - repetir não desmarca;
    - `setWeight` cascateia como `setField`;
    - `goTo` e `nav` movem `cur`;
    - replay corrige `at` para o `ts`;
    - série de tempo é marcada com o `sec` planejado (D-5);
    - comando com o app fora de `/workout` navega até lá e é aplicado depois do `opengym:watch-ready`;
    - `restAdjust`, `restSkip`, `restSync` e `finish` funcionam sem a tela.

    A suíte do upstream continua com o mesmo resultado.
  - Req: RW-2.4, RW-3.1, RW-3.4, RW-4.1, RW-5.2, RS-2.3, RO-1.4, design §4.1, §7.3

- [ ] **M2.10 — Módulo `:wear` mínimo (app de teste)**
  - Arquivos: `frontend/android/wear/**` (Activity Compose com texto do snapshot + botão "Concluir série" + `WatchListenerService` + `CommandOutbox` simples), `fork/android/fork-settings.gradle`, assinatura e versão compartilhadas
  - Aceite: `:wear:assembleDebug` e `:wear:assembleRelease` compilam com `applicationId` igual ao do celular, mesma assinatura e mesmo `versionCode`. Em emulador Wear pareado com o emulador do celular, o texto mostra o exercício e a série atuais, e o botão conclui a série no celular.
  - Req: RS-1.3, RS-1.6, RB-3.2, design §9.1

- [ ] **M2.11 — Script de release com o relógio**
  - Arquivos: `scripts/fork-release.sh`
  - Aceite: compila `:wear:assembleRelease`, compara o SHA-256 dos certificados dos dois APKs (falha se diferentes, testado assinando um com a keystore de teste), confere o pacote e o `versionCode` iguais via `aapt2`, copia `openGym-Cezar-watch-*.apk`. `--install` detecta o relógio por `ro.build.characteristics` ou usa `--watch SERIAL`. Também roda os testes Kotlin.
  - Req: RB-6.1 (2–6), RS-1.6

- [x] **M2.12 — 🛑 Parear o relógio por adb + identificar Wear OS**
  - Arquivos: `FORK.md` (Aparelhos), `docs/FORK_INSTALL.md` (seção relógio: opções de desenvolvedor, depuração sem fio, `adb pair`/`adb connect`, instalar o APK)
  - Aceite: o dono ativa a depuração sem fio no relógio e informa IP:porta e o código de pareamento. O assistente roda `adb pair` e `adb connect` e registra a versão de Wear OS/SDK. Se o SDK do relógio for < 30, PARE e revise o D-1.
  - Req: RB-5.2, RB-8.1, D-1

- [ ] **M2.13 — Checklist manual M2**
  - **Checklist (o dono):** (1) instalar celular + relógio pelo script (`--install`); (2) iniciar o Treino A no celular → o app mínimo do relógio mostra exercício e série; (3) apagar a tela do celular, esperar 1 min, tocar "Concluir série" no relógio 3× → ao acender, o celular tem 3 séries feitas e o descanso rodando; (4) matar o app do celular (recentes), tocar "Concluir" no relógio, reabrir o celular → a série aparece (fila durável); (5) upgrade N → N+1 dos dois sem perda.
  - Req: RS-1.1, RS-2.1, RB-7, RT-4

**🛑 PARE — fim do M2. Aguardar confirmação do dono.**

---

## M3 — UI do treino no relógio + comandos

Termina com: telas Série, Lista e Finalizar completas, bezel ajustando carga e reps, navegação e finalizar pelo relógio.

- [ ] **M3.1 — Base do app Wear: tema, pager, estado**
  - Arquivos: `wear/.../ui/App.kt`, `ui/theme/*`, `WorkoutRepository.kt`, `res/values{,-pt-rBR}/strings.xml`
  - Aceite: `HorizontalPager` de 4 páginas, tela "Nenhum treino ativo" quando `active=false`, "Atualize o app" quando o protocolo é incompatível. Strings em inglês e pt-BR. Previews Compose para cada estado.
  - Req: RW-1.5, RS-1.5, design §8.1, §8.4

- [ ] **M3.2 — `OptimisticReducer` + `CommandOutbox` completo**
  - Arquivos: `wear/.../data/OptimisticReducer.kt`, `CommandOutbox.kt` (fila durável em arquivo, ULID, envio em ordem, reenvio a cada 5 s, limpeza por `appliedThrough`), testes JVM
  - Aceite: testes: projeção de cada comando (design §5.2), persistência entre instâncias, limpeza, rejeição via `/result` remove o item e gera um evento de toast.
  - Req: RO-1.1, RO-1.2, RO-1.6, RS-3.1, design §5.2, §3.4

- [ ] **M3.3 — Página Série**
  - Arquivos: `wear/.../ui/SetPage.kt`
  - Aceite: mostra nome, superset, "Série X de Y"/"Aquec. N", carga e reps com +/−, foco alternável por toque, "Concluir", chips anterior/próximo e o indicador offline. Layout unilateral com E/D. `editable=false` mostra "edite no celular" e só deixa concluir. Números ≥ 32 sp. Previews para reps, unilateral, superset e não editável.
  - Req: RW-1.1, RW-1.3, RW-1.4, RW-2.3, RW-3.1, D-5, design §8.1

- [ ] **M3.4 — Bezel giratório**
  - Arquivos: `wear/.../ui/Rotary.kt`, `SetPage.kt`
  - Aceite: no emulador (rotary) e no relógio, cada passo do bezel muda carga em ± `wStep` e reps em ± 1 (mínimo 0), com haptic por passo. Ajustes seguidos viram um único comando 600 ms depois do último passo (teste de unidade do debounce com relógio falso).
  - Req: RW-2.1, RW-2.2, RE-1.4, D-7, design §8.2

- [ ] **M3.5 — Página Lista + navegação**
  - Arquivos: `wear/.../ui/ListPage.kt`
  - Aceite: `ScalingLazyColumn` com nome, feitas/total, ✓ e o atual destacado. O toque envia `goTo` e volta para a página Série. Os chips da página Série enviam `nav ±1`. Offline, a navegação usa `units` (teste do reducer).
  - Req: RW-4.1, RW-4.2, RW-4.3

- [ ] **M3.6 — Página Finalizar**
  - Arquivos: `wear/.../ui/FinishPage.kt`
  - Aceite: botão de segurar 1,5 s com anel de progresso, resumo de séries e duração. Soltar antes não envia nada. Envia `finish`. Sem opção de descartar. Ao chegar `active=false`, volta para a espera.
  - Req: RW-5.1, RW-5.3, RW-5.4

- [ ] **M3.7 — Integração ponta a ponta no emulador**
  - Arquivos: `FORK.md` (como montar o par de emuladores)
  - Aceite: no par de emuladores, todos os comandos de M3 refletem no celular e voltam no snapshot em < 2 s. Rejeições (excluir no celular o exercício que o relógio está editando) mostram toast no relógio.
  - Req: RS-1.1, RS-3.2, RT-5

- [ ] **M3.8 — Release + checklist manual M3**
  - **Checklist (o dono):** com o Treino A: (1) carga +2 passos pelo bezel e reps −1 → o celular mostra em ≤ 2 s, com cascata nas séries seguintes; (2) concluir pelo relógio com a tela do celular apagada; (3) próximo/anterior e pular pela lista; (4) num superset (montar um par na rotina), conferir que o relógio segue a ordem do celular; (5) finalizar segurando → treino no histórico com PRs; (6) upgrade N → N+1 dos dois sem perda.
  - Req: RW-1..5, RT-4

**🛑 PARE — fim do M3. Aguardar confirmação do dono.**

---

## M4 — Descanso sincronizado + Ongoing Activity + offline + ambiente

Termina com: descanso igual nos dois lados com vibração, Ongoing Activity, uso completo offline com reconciliação, modo ambiente econômico.

- [ ] **M4.1 — Página Descanso + offset de relógio**
  - Arquivos: `wear/.../ui/RestPage.kt`, `WorkoutRepository.kt`
  - Aceite: arco de progresso, tempo restante, próxima série, botões −15s/Pular/+15s → `restAdjust`/`restSkip`. O pager vai para Descanso sozinho quando começa e volta quando acaba. O countdown usa `endsAt − offset`. Teste do offset com relógios falsos (diferença ≤ 1 s).
  - Req: RW-3.2, RW-3.4, RW-3.5, design §3.2, §8.1

- [ ] **M4.2 — `LocalRestTimer` + alarme exato de vibração**
  - Arquivos: `wear/.../rest/LocalRestTimer.kt`, `RestAlarmReceiver.kt`, manifest (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, `VIBRATE`)
  - Aceite: o alarme é (re)agendado a cada `rest` novo e cancelado com `rest=null`/pular. Vibra 300-150-300 no fim com a tela apagada e em ambiente (teste no relógio, tela apagada). Offline, concluir série com `restHint > 0` inicia o timer local. Testes JVM com relógio falso.
  - Req: RW-3.3, RO-1.3, design §5.4

- [ ] **M4.3 — Ongoing Activity + `WatchWorkoutService`**
  - Arquivos: `wear/.../session/OngoingWorkout.kt`, `WatchWorkoutService.kt`, `WatchListenerService.kt`, manifest
  - Aceite: ao iniciar o treino no celular, com o app do relógio fechado, aparece a Ongoing Activity (ícone + status) e o relógio vibra 1× (D-6). Durante o descanso, o status mostra o `TimerPart` contando. O toque abre o app na página Série. Com o app aberto, o FGS assume a notificação. `active=false` remove tudo. Nenhum FGS ou wake lock fora de treino (`dumpsys`).
  - Req: RW-6.1–6.3, RW-7.1, RE-1.2, D-6, design §5.3, R-4

- [ ] **M4.4 — Replay e `restSync` na reconexão**
  - Arquivos: `wear/.../data/CommandOutbox.kt`, `WorkoutRepository.kt` (estado Offline/Sincronizando), `frontend/src/watch/commands.js`
  - Aceite: teste JVM do fluxo do design §7.2 (`hello` → pendentes com `replay` → `restSync`). Teste vitest: replay de 2 `completeSet` + `restSync` deixa no celular o descanso do relógio, não um descanso novo, e `at` = `ts`. `wid` diferente → todos rejeitados com o toast "Treino já finalizado no celular — N ações descartadas".
  - Req: RO-1.4, RO-1.5, RO-1.7, design §7

- [ ] **M4.5 — Modo ambiente**
  - Arquivos: `wear/.../ui/AmbientScreen.kt`, `App.kt`
  - Aceite: em ambiente, fundo preto, texto fino, sem botões nem animação, atualização ≈ 1/min, com deslocamento anti burn-in. Sai do ambiente sem perder o estado. Conferido no relógio com always-on ligado.
  - Req: RE-1.1, design §8.3

- [ ] **M4.6 — `FORK_INSTALL.md` completo**
  - Arquivos: `docs/FORK_INSTALL.md`
  - Aceite: seções do relógio completas, incluindo "relógio não encontra o celular" (mesmo `applicationId`/assinatura, Galaxy Wearable conectado, reinstalar os dois pelo script, `adb logcat -s OpenGymWear`).
  - Req: RB-8.1

- [ ] **M4.7 — Release + checklist manual M4**
  - **Checklist (o dono):** itens 1 a 8 do design §12, em especial: (a) ±15s e Pular nos 3 lugares (relógio, tela e notificação do celular) ficam iguais; (b) modo avião no relógio no meio do treino, concluir 2 séries, ajustar carga, descanso vibra no fim → tirar o modo avião: tudo no celular, sem duplicar, com o descanso alinhado; (c) Ongoing Activity aparece sem abrir o app e leva ao treino; (d) always-on por 10 min sem travar; (e) bateria do relógio antes e depois de um treino de 60 min (meta ≤ 15%); (f) upgrade N → N+1 dos dois sem perda.
  - Req: RW-3, RW-6, RO-1, RE-1, RB-7, RT-4

**🛑 PARE — fim do M4. Aguardar confirmação do dono.**

---

## M5 — P2: frequência cardíaca e Live Update

Termina com: FC ao vivo no relógio (e salva, se o portão de compatibilidade passar) e notificação promovida no celular onde houver suporte.

- [ ] **M5.1 — Portão de compatibilidade do campo `hr`**
  - Arquivos: `frontend/src/watch/hr-compat.test.js`, `FORK.md` (resultado)
  - Aceite: um backup com `workouts[].hr = {avg,max,samples}`: (a) o importador do upstream (código atual) aceita sem erro (teste vitest chamando o import real); (b) teste manual: importar esse backup no app oficial v1.4.1 instalado no celular sem erro. Se (a) ou (b) falhar, registrar e **não** executar o M5.4 (D-4).
  - Req: RP2-1.2, D-4

- [ ] **M5.2 — Health Services no relógio**
  - Arquivos: `wear/.../health/HeartRateSession.kt`, `WatchWorkoutService.kt` (tipo `health`), manifest (`BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE_HEALTH`)
  - Aceite: com o treino ativo e o app aberto, a FC aparece na página Série. Sem permissão, funciona sem FC. Para no `active=false`. Sem sensores fora do treino (`dumpsys`).
  - Req: RP2-1.1, RP2-1.3, RE-1.2, design §11.1

- [ ] **M5.3 — Comando `hr` no protocolo**
  - Arquivos: `fork-protocol` (tipo `hr`), `frontend/src/watch/commands.js`, testes JS e Kotlin
  - Aceite: amostras a cada 30 s, fora da fila durável. O celular acumula em `S.active.hr`. Testes de serialização e de acumulação.
  - Req: RP2-1.2, design §11.1

- [ ] **M5.4 — Salvar `hr` no treino (só se o M5.1 passou)**
  - Arquivos: `frontend/src/sheets.jsx` (gancho FORK no `doFinishWorkout`), teste
  - Aceite: o treino salvo tem `hr {avg,max,samples}`. O backup exportado importa no oficial (refaz o M5.1-b).
  - Req: RP2-1.2

- [ ] **M5.5 — Live Update / Now Bar no celular**
  - Arquivos: `fork-wearbridge/.../WorkoutSessionService.kt`
  - Aceite: com SDK ≥ 36, a notificação usa `ProgressStyle` + `setRequestPromotedOngoing(true)` com "Exercício · X/Y" ou "Descanso m:ss". Com SDK < 36, segue a notificação comum. Conferido no celular do dono (resultado no `FORK.md`, com a versão do One UI do M1.9).
  - Req: RP2-2.1, RP2-2.2, design §11.2

- [ ] **M5.6 — Release + checklist manual M5**
  - **Checklist (o dono):** (1) aceitar as permissões de sensores → FC aparece no relógio; (2) negar → o app funciona sem FC; (3) finalizar → treino com FC no histórico (se o M5.4 foi feito) e o backup importa no oficial; (4) a Now Bar/Live Update mostra o treino, se o aparelho suportar; (5) bateria de 60 min com FC; (6) upgrade N → N+1.
  - Req: RP2-1, RP2-2, RT-4

**🛑 PARE — fim do M5. Aguardar confirmação do dono.**

---

## Rastreabilidade (requisito → tarefas)

| Req | Tarefas |
|---|---|
| RB-1 | M1.3, M1.11 |
| RB-2 | M1.1, M1.5, M1.6 |
| RB-3 | M1.4, M2.10 |
| RB-4 | M1.7 |
| RB-5 | M1.9, M2.12 |
| RB-6 | M1.1, M1.8, M2.11 |
| RB-7 | M1.11, M2.13, M3.8, M4.7, M5.6 |
| RB-8 | M1.10, M2.12, M4.6 |
| RB-9 | M1.1, M1.2 (e o padrão FORK em todas) |
| RW-1 | M2.4, M3.1, M3.3 |
| RW-2 | M2.9, M3.3, M3.4 |
| RW-3 | M2.9, M3.3, M4.1, M4.2 |
| RW-4 | M2.9, M3.5 |
| RW-5 | M2.9, M3.6 |
| RW-6 / RW-7 | M4.3 |
| RS-1 | M2.1, M2.3, M2.6, M2.8, M2.10, M3.7 |
| RS-2 | M2.1, M2.6, M2.7, M2.9 |
| RS-3 | M2.5, M3.2, M3.7 |
| RO-1 | M2.5, M3.2, M4.2, M4.4 |
| RE-1 | M2.7, M2.8, M3.4, M4.3, M4.5, M4.7 |
| RP2-1 | M5.1–M5.4 |
| RP2-2 | M1.9, M5.5 |
| RT-1 | M2.4, M2.5, M2.9 |
| RT-2 | M2.3, M2.4, M5.3 |
| RT-3 | M1.1, M1.7, M2.9 |
| RT-4 | M1.11, M2.13, M3.8, M4.7, M5.6 |
| RT-5 | M2.10, M3.7 |
