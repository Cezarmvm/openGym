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

## Build do fork (Gradle)

- `fork/android/fork-app.gradle`: `applicationId com.cezar.opengym` (debug: `.test`) e nome "openGym (Cezar)" via `fork/android/release-res`.
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

_(preenchido nas tarefas M1.9 e M2.12)_
