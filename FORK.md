# FORK.md — openGym (Cezar)

Fork pessoal de [DuarteSantos8/openGym](https://github.com/DuarteSantos8/openGym) (AGPL-3.0). Ele
gera um APK Android próprio (`com.cezar.opengym`) e um app Wear OS para o Galaxy Watch 4 Classic,
sincronizado com o celular durante o treino. Uso pessoal, não comercial. `LICENSE`, `NOTICE.md` e a
mídia dos exercícios não são modificados.

- Especificação: [`specs/fork-watch/`](specs/fork-watch/) (requirements → design → tasks)
- Instalação: [`docs/FORK_INSTALL.md`](docs/FORK_INSTALL.md)
- Branch do fork: `fork/main`. `main` espelha o upstream.

## Pontos FORK em arquivos do upstream

Toda mudança em arquivo do upstream é marcada com `FORK:` no comentário. Para listar todas:
`git grep -n "FORK:"`.

| Arquivo | O que muda | Como reaplicar após conflito |
|---|---|---|
| `.gitignore` | bloco `# FORK:` com keystore, `*.jks`, `*.keystore` e `dist-apk/` | manter o bloco no fim do arquivo |
| `frontend/android/settings.gradle` | `apply from` de `fork/android/fork-settings.gradle` (módulos do fork) | manter a linha no fim |
| `frontend/android/app/build.gradle` | `apply from` de `fork/android/fork-app.gradle` (applicationId, nome, versão, assinatura) | manter a linha no **fim** (precisa do `defaultConfig` do upstream já lido) |
| `frontend/src/lib/update.js` | `REPO = 'Cezarmvm/openGym'`: o verificador só olha as releases do fork | manter a constante do fork |
| `frontend/src/lib/update.test.js` | a URL esperada passa a ser a do fork (linha marcada) | idem |
| `frontend/src/lib/import-csv.js` | `parseWhen` aceita "de" entre dia, mês e ano ("4 de mar. de 2026"), formato do Hevy em português/espanhol. Teste: `import-csv.pt-br.test.js` | manter o `(?:de\s+)?` na regex dia-mês-ano |
| `frontend/src/lib/health-sync.js` | Health Connect recebe só os treinos iniciados depois de ligar a opção (`since` no `opengym-health.json`; filtro em `src/fork/health-since.js`). 3 linhas marcadas | reaplicar `onlySince(...)` no `planSync` do `syncOnce` e o `since` no `enableHealth` |
| `frontend/src/lib/health-sync.test.js` | relógio dos testes fixado antes dos treinos de exemplo, para manter os casos do upstream | manter as 2 linhas FORK no `beforeEach` |
| `frontend/android/app/src/main/java/ch/duartesantos/opengym/MainActivity.java` | `registerPlugin(HealthReadPlugin.class)` (fork-health) | manter a linha FORK após os `registerPlugin` do upstream |
| `frontend/src/main.jsx` | `initHealthRead()` no MOBILE (fork-health) | manter a linha FORK |
| `frontend/src/views/Settings.jsx` | import + `<HealthReadCard/>` abaixo do `HealthConnectCard` (fork-health) | manter as 2 linhas FORK |

## Build do fork (Gradle)

- `fork/android/fork-app.gradle`: `applicationId com.cezar.opengym` (debug: `.test`) O nome visível continua "openGym", igual ao upstream (D-10).
- `fork/android/fork-version.gradle`: `versionCode = upstream × 1000 + forkBuild`, `versionName = <upstream>-cezar.<forkBuild>`. O `forkBuild` fica em `fork/android/version.properties` e nunca zera. A task `forkVersionCheck` compara com `fork/android/last-version-code`.
- `fork/android/fork-signing.gradle`: lê `fork/keystore.properties` (ou `$OPENGYM_KEYSTORE_PROPERTIES`), assina v1+v2+v3, e o release falha sem ele (`forkCheckSigning`).

## Procedimento de merge com o upstream

1. `git fetch upstream`
2. `git checkout fork/main && git merge upstream/main` (merge, nunca rebase nem force-push)
3. Resolver conflitos só nos pontos da tabela acima (`git grep -n "FORK:"`)
4. `cd frontend && npx vitest run` e os testes Kotlin dos módulos do fork
5. `scripts/fork-release.ps1 --install` e o checklist curto (abrir, Treino A, concluir série
   pelo relógio, finalizar)

O remote `upstream` tem o push desativado (`git remote set-url --push upstream DISABLED`).

## Ambiente de build (Windows)

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | Microsoft OpenJDK 21.0.12 | `JAVA_HOME` do usuário |
| Android SDK | platform 35, build-tools 35.0.0, platform-tools 37 | `%LOCALAPPDATA%\Android\Sdk` |
| cmdline-tools | **15859902** | a 16111833 aborta neste PC ("HW capability … requested 0x20000000") |
| Node | 22.18 | igual ao CI do upstream |
| git | `core.autocrlf=input` (config local) | sem isso, o `cap sync` deixa 2 arquivos Gradle "modificados" (só fim de linha) |

- `npm run build:mobile` não funciona no cmd/PowerShell (sintaxe `VITE_MOBILE=1 …`). O script de
  release roda as mesmas etapas pelo Git Bash.
- A primeira compilação Gradle falhou uma vez com "Could not move temporary workspace" (antivírus
  segurando o cache), e a segunda passou. Se isso se repetir, excluir `%USERPROFILE%\.gradle` e
  a pasta do projeto da verificação do Windows Defender.

## Testes do upstream que já falham neste ambiente

Antes de qualquer mudança do fork, `npx vitest run` no Windows dava 5054/5055 testes passando e
2 arquivos com falha:

- `src/sheets.day-note.test.jsx`: "the calendar marks it, with the note as its tooltip and a
  legend entry" (`expected 'cal-d' to contain 'noted'`)
- `scripts/check-mobile-bundle.test.mjs`: `SyntaxError: Invalid or unexpected token` ao carregar
  no Windows

Essas falhas são a referência: o fork não pode adicionar falhas novas (RT-3).

## Aparelhos

| Aparelho | Modelo | Sistema | Observações |
|---|---|---|---|
| Celular | Samsung Galaxy S25 Ultra (`SM-S938B`, `pa3q`) | Android 17 (SDK 37), One UI 9.0, build `S938BXXUCDZIF` | SDK ≥ 36: Live Updates / Now Bar viáveis (P2, D-2) |
| Relógio | Galaxy Watch 4 Classic (`SM-R890`, `wisebl`) | Wear OS 6 / Android 16 (SDK 36), build `R890XXS2JZG3` | Tela 450×450. Depuração só por Wi-Fi (`adb pair` + `adb connect`); o Wi-Fi do relógio cai com o Bluetooth do celular ligado |

Keystore do fork: `C:\Users\Cezar\keys\opengym-cezar.jks`, alias `opengym-cezar`, certificado SHA-256
`70:F8:BD:74:05:75:EF:43:37:85:3D:B6:3F:40:CE:35:A2:AF:D3:2D:25:94:BE:0E:25:C9:6E:58:2E:47:BF:D2`.
O fingerprint é público: ele só identifica a chave, não dá acesso a ela.

## Health Connect (specs/fork-health), verificado no S25 Ultra em 2026-10-14

- **Gravar:** ligado em 1.4.1-cezar.3, com permissões de gravação de exercício e peso. Só vão
  treinos iniciados depois de ligar (`since`). Os 23 treinos importados do Hevy não foram enviados.
- **Ler:** ligado em 1.4.1-cezar.5, com leitura de peso, gordura corporal e histórico. A
  primeira leitura trouxe **33 registros** (25 pesagens desde maio/2025 e 8 de gordura corporal).
  A pesagem lançada no openGym em 09/10 (66,6 kg) foi mantida.
- O Samsung Health tem `READ_EXERCISE`/`READ_WEIGHT` (lê o que o openGym grava) e grava peso,
  gordura e FC no Health Connect. Não grava massa magra, água nem massa óssea.
- **Pendente (HW-2.1):** confirmar, depois do primeiro treino finalizado no openGym, se ele
  aparece no histórico de exercícios do Samsung Health.

## Spike M2.1: JS da WebView com a tela apagada (risco R-1), S25 Ultra, 2026-10-14

Branch local `spike/bg-js` (commit `e7056ff3`, não enviado ao GitHub; scripts e logs em
`spike-results/`). Build debug `com.cezar.opengym.test`, sem dados reais. Um broadcast do adb faz
o papel da mensagem do relógio: plugin nativo → evento no JS → `setState` do Zustand →
`subscribe` → `ack` nativo, que registra a latência. Bateria simulada como "fora da tomada"
(`dumpsys battery unplug`), tela confirmada apagada (`mWakefulness=Dozing`) antes de cada mensagem.

| Cenário | Tela acesa | 1 min apagada | 2–6 min apagada |
|---|---|---|---|
| A. Sem FGS | 3–5 ms | 4 ms | **JS parado** (nativo recebe, JS não responde) |
| B. FGS `specialUse` | 4 ms | **JS parado** | **JS parado**: tudo entregue de uma vez ao acender a tela (25–330 s de atraso) |
| C. FGS + Doze forçado | 4 ms | **JS parado** | **JS parado** |
| D/E. FGS + `setRendererPriorityPolicy(IMPORTANT, waived=false)` | 3 ms | **JS parado** | **JS parado** (E: entregue ao acender; D: eventos perdidos) |

O processo do app fica vivo e não congelado com o FGS (`procState 4`, `isFrozen=false`) e recebe
cada mensagem na hora. O processo de renderização da WebView fica `cached` (adj 900, grupo de
background) e o JS não roda até a tela voltar. O Capacitor não pausa a WebView (`KeepRunning` =
true). Conclusão: **R-1 confirmado**. Com o celular no bolso, o JS não processa comandos do
relógio, nem com FGS. Em alguns casos o evento nativo → JS se perdeu, o que confirma que a fila
durável (`CommandInbox`, drenada pelo JS) é obrigatória. Spec a revisar antes do M2.2.
