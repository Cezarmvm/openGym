# Tarefas: Health Connect nos dois sentidos

> Spec `fork-health` · Fase 3 · Status: **aprovado em 2026-10-14**
> Mesmas regras do `specs/fork-watch/tasks.md`: teste → `[x]` → commit → `✅ tarefa — verificação`.

- [x] **H1: Gravar só treinos novos no Health Connect**
  - Arquivos: `frontend/src/fork/health-since.{js,test.js}`, `lib/health-sync.js` (3 linhas FORK), `lib/health-sync.test.js` (2 linhas FORK)
  - Aceite: ligar a gravação não envia treinos anteriores; pesagens continuam. Suíte do upstream igual. Instalado (1.4.1-cezar.3) e ligado no S25 em 2026-10-14.
  - Req: HW-1

- [x] **H2: Módulo `:fork-health` + `HealthReadPlugin` (Java)**
  - Arquivos: `frontend/android/fork-health/**`, `fork/android/fork-settings.gradle`, `fork/android/fork-app.gradle`, `MainActivity.java` (+2 linhas FORK)
  - Aceite: compila. `status`, `requestPermissions`, `readSince` e `heartRate` respondem no S25 (log via `chrome://inspect` não existe em release: verificar com um build debug `.test`). Permissões de leitura aparecem na tela do Health Connect.
  - Req: HR-3.2, design §2

- [x] **H3: Mescla pura (`fork/health-read.js`) + testes**
  - Arquivos: `frontend/src/fork/health-read.{js,test.js}`
  - Aceite: todos os casos do design §6 (vitest) verdes.
  - Req: HR-1, HR-2, HQ-1

- [ ] **H4: Sincronização de leitura (`fork/health-read-sync.js`) + inicialização**
  - Arquivos: `frontend/src/fork/health-read-sync.{js,test.js}`, `frontend/src/main.jsx` (+1 linha FORK)
  - Aceite: com o plugin falso, primeira leitura com `since=0`, depois incremental com margem de 1 dia, nada quando desligado, erro de permissão registrado sem quebrar o app.
  - Req: HR-1.1, HR-3.3, HR-3.4

- [ ] **H5: Card "Ler do Conexão Saúde" no Settings**
  - Arquivos: `frontend/src/fork/HealthReadCard.jsx`, `frontend/src/fork/strings.js`, `frontend/src/views/Settings.jsx` (+1 linha FORK)
  - Aceite: liga e desliga, pede as permissões, mostra a última leitura e a contagem. Teste de componente (happy-dom) no estilo dos `sheets.*.test.jsx`.
  - Req: HR-3

- [ ] **H6: Release + checklist no S25**
  - Aceite (o assistente executa pelo cabo e o dono confirma): os 5 itens do design §6, mais HW-2.1 (o treino do openGym aparece no Samsung Health). O resultado vai para o `FORK.md`.
  - Req: HW-2, HQ-3

- [ ] **H7 (P2): FC nos treinos**, só depois do portão M5.1 do fork-watch
  - Arquivos: `fork/health-read.js` (`summarizeHr`), `fork/health-read-sync.js`, componente da linha de FC + 1 linha FORK no detalhe do treino
  - Aceite: um treino finalizado com o relógio no pulso ganha `hr {avg,max}`, e o backup com `hr` importa no app oficial.
  - Req: HR-4

**🛑 PARE depois do H6 e aguarde confirmação do dono.**
