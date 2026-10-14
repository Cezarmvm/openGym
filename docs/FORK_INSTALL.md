# Instalação do openGym (Cezar): celular e Galaxy Watch

Guia do fork `Cezarmvm/openGym`. O app do fork aparece no celular como **openGym**, o mesmo nome do oficial, mas tem um id
próprio (`com.cezar.opengym`). Por isso pode conviver com o app oficial (`ch.duartesantos.opengym`):
são dois apps separados, cada um com seus próprios dados. Se instalar os dois, eles aparecem com o
mesmo nome. O do fork é o que você instalou pelo script.

> Os passos com menus da Samsung foram escritos para a One UI em português. Os nomes podem variar
> um pouco entre versões.

## Sumário

1. [Keystore (uma vez só)](#1-keystore-uma-vez-só)
2. [Gerar os APKs](#2-gerar-os-apks)
3. [Preparar o celular](#3-preparar-o-celular)
4. [Instalar e atualizar no celular](#4-instalar-e-atualizar-no-celular)
5. [Trazer seus dados](#5-trazer-seus-dados)
6. [Bateria: deixar o app trabalhar com a tela apagada](#6-bateria-deixar-o-app-trabalhar-com-a-tela-apagada)
7. [Preparar o Galaxy Watch](#7-preparar-o-galaxy-watch)
8. [Instalar no relógio](#8-instalar-no-relógio)
9. [Solução de problemas](#9-solução-de-problemas)

---

## 1. Keystore (uma vez só)

A keystore é a "assinatura" do app. **Toda atualização precisa ser assinada com a mesma chave.**
Sem ela, o Android recusa instalar por cima, e você teria que desinstalar e perder os dados do app.

1. Abra o **PowerShell** e rode:
   ```powershell
   New-Item -ItemType Directory -Force C:\Users\Cezar\keys | Out-Null
   & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore C:\Users\Cezar\keys\opengym-cezar.jks -alias opengym-cezar -keyalg RSA -keysize 4096 -validity 10950
   ```
   Escolha uma senha forte. O nome pode ser só o seu; o resto pode ficar em branco.
2. Crie o arquivo de configuração (ele fica **fora do git**):
   ```powershell
   Copy-Item fork\keystore.properties.example fork\keystore.properties
   notepad fork\keystore.properties
   ```
   Preencha `storePassword` e `keyPassword`.
3. **Backup:** copie `opengym-cezar.jks` **e a senha** para fora do PC (pendrive + gerenciador de
   senhas). Sem os dois, nenhuma atualização futura instala.

Nunca coloque a keystore nem o `keystore.properties` dentro do repositório. O `.gitignore` já os
bloqueia, mas o fork é público.

## 2. Gerar os APKs

Na pasta do repositório:

```powershell
scripts\fork-release.ps1            # gera e confere os APKs
scripts\fork-release.ps1 --install  # gera, confere e instala por adb
```

O script roda os testes do fork, faz o build mobile, compila o release e confere se os APKs estão
assinados com a sua chave (e se celular e relógio usam o mesmo certificado). Depois copia tudo para
`dist-apk\` (`openGym-Cezar-phone-<versão>.apk`, com `.sha256`) e registra a versão num commit.
Cada execução gera um `versionCode` maior que o anterior.

Opções: `--skip-tests`, `--no-bump` (recompila a mesma versão, sem commit), `--phone SERIAL` e
`--watch SERIAL`.

## 3. Preparar o celular

### 3.1 Depuração USB (para instalar pelo PC)
1. **Configurações → Sobre o telefone → Informações do software** → toque **7 vezes** em
   **Número de compilação** e digite o PIN. Aparece "Modo de desenvolvedor ativado".
2. **Configurações → Opções do desenvolvedor** → ligue **Depuração USB**.
3. Conecte o cabo USB. No celular, aceite **"Permitir depuração USB?"** (marque "Sempre permitir
   deste computador").
4. No PC: `adb devices` deve listar o celular como `device`.

### 3.2 Instalar sem PC (opcional): fontes desconhecidas
Se você copiar o APK para o celular e abrir pelo app **Meus Arquivos**:
1. O Android avisa que o app não tem permissão para instalar apps desconhecidos. Toque em
   **Configurações** → ligue **Permitir desta fonte**.
2. Ou antes: **Configurações → Aplicativos → ⋮ → Acesso especial → Instalar apps desconhecidos**
   → **Meus Arquivos** (ou o navegador) → **Permitir**.

### 3.3 Aviso do Play Protect (esperado)
Como o app não vem da Play Store, o Play Protect pode mostrar **"App não seguro bloqueado"** ou
**"Enviar app para verificação?"**. É esperado para um app assinado por você:
- Toque em **Mais detalhes → Instalar mesmo assim**.
- Se ele oferecer enviar o app para análise, pode recusar.

## 4. Instalar e atualizar no celular

- **Pelo PC:** `scripts\fork-release.ps1 --install`, ou só
  `adb install -r dist-apk\openGym-Cezar-phone-<versão>.apk`.
- **Atualizar:** é igual. O `-r` instala por cima e **mantém os dados**. Nunca desinstale para
  atualizar.
- O app oficial pode continuar instalado. Os dois não se misturam.

## 5. Trazer seus dados

### 5.1 Do app oficial do openGym
1. No **app oficial**: **Configurações → Dados e backup → Exportar backup**. O arquivo
   `opengym-backup-AAAA-MM-DD.json` sai pela tela de compartilhamento. Salve em "Meus Arquivos" ou
   no Drive.
2. No **openGym** do fork: **Configurações → Dados e backup → Importar backup** → escolha o arquivo.

> Se o oficial usava fotos e vídeos em exercícios próprios, use **Exportar com fotos e vídeos
> (.zip)** e importe o `.zip`.

### 5.2 Do Hevy (opcional)
O openGym já importa o CSV do Hevy:
1. No **Hevy**: **Perfil → Configurações → Exportar e importar dados → Exportar treinos**. Isso gera
   um `workouts.csv`.
2. No **openGym** do fork: **Configurações → Dados e backup → Importar de outro app** → escolha o
   CSV → revise o resumo → confirme.

Os exercícios são reconhecidos pelo nome em inglês do Hevy. O resumo mostra o que não foi
reconhecido antes de você confirmar.

## 6. Bateria: deixar o app trabalhar com a tela apagada

A sincronização com o relógio precisa que o app continue rodando durante o treino com a tela
apagada. A Samsung costuma "adormecer" apps:

1. **Configurações → Aplicativos → openGym → Bateria → Sem restrições**.
2. **Configurações → Assistência do aparelho → Bateria → Limites de uso em segundo plano**: o
   openGym **não** pode estar em "Apps em suspensão" nem em "Apps em suspensão profunda".
3. Notificações do app ligadas (para o descanso e para o "Treino em andamento").

## 7. Preparar o Galaxy Watch

> O app do relógio chega no marco M2. Esta seção já vale para parear o relógio por adb.

1. No relógio: **Configurações → Sobre o relógio → Software** → toque **5 vezes** em
   **Versão do software**. Aparece "Modo desenvolvedor ativado".
2. **Configurações → Opções do desenvolvedor** → ligue **Depuração ADB** e
   **Depurar via Wi-Fi**. O relógio e o PC precisam estar na **mesma rede Wi-Fi** (não uma rede
   de convidados). Com o celular ligado por Bluetooth, o relógio desliga o Wi-Fi para poupar
   bateria: se ele ficar caindo, desligue o **Bluetooth do celular** durante o pareamento.
3. Em **Depurar via Wi-Fi**, toque em **Parear novo dispositivo**. O relógio mostra um
   **código de 6 dígitos** e **IP:porta** de pareamento.
4. No PC:
   ```powershell
   adb mdns services             # mostra as duas portas: _adb-tls-pairing e _adb-tls-connect
   adb pair <IP>:<porta-de-pareamento> <código>
   adb connect <IP>:<porta>      # a porta da tela principal de "Depurar via Wi-Fi" (não a de pareamento)
   adb devices                   # o relógio aparece como <IP>:<porta>  device
   ```
5. No relógio, aceite **"Permitir depuração?"**.

O pareamento vale até você desligar a depuração. Depois de reiniciar o relógio, basta repetir o
`adb connect`, porque a porta pode mudar.

## 8. Instalar no relógio

> Disponível a partir do M2.

- `scripts\fork-release.ps1 --install` instala no celular **e** no relógio (detecta qual é qual).
- Manual: `adb -s <IP>:<porta> install -r dist-apk\openGym-Cezar-watch-<versão>.apk`.
- O app do relógio precisa ter **o mesmo id e a mesma assinatura** do app do celular, senão um não
  enxerga o outro. O script confere isso antes de instalar.

## 9. Solução de problemas

| Mensagem / sintoma | Causa | O que fazer |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Já existe um app com o mesmo id assinado com **outra chave** (outra keystore, ou um build de teste antigo). | Confira se `fork\keystore.properties` aponta para a sua `opengym-cezar.jks`. Se o app instalado foi assinado com outra chave, só desinstalando: **exporte o backup antes** (§5.1), desinstale, instale e importe. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | O APK tem `versionCode` **menor** que o instalado. | Gere de novo com `scripts\fork-release.ps1` (sem `--no-bump`): cada release sobe o número. Não instale APKs antigos de `dist-apk\`. |
| "App não instalado" (sem detalhe, instalando pelo celular) | Quase sempre uma das duas causas acima, ou o APK copiado pela metade. | Instale por `adb install -r …` para ver o erro exato. Confira o `.sha256` (`Get-FileHash -Algorithm SHA256 arquivo.apk`). |
| Play Protect bloqueia | App fora da Play Store. | §3.3: **Mais detalhes → Instalar mesmo assim**. |
| `adb devices` vazio ou `unauthorized` | Depuração USB desligada, cabo só de carga, ou autorização não aceita. | §3.1. Troque o cabo. Desconecte, reconecte e aceite o aviso no celular. |
| `keystore.properties not found` no script | A keystore ainda não foi configurada. | §1. |
| Relógio não encontra o celular | Disponível a partir do M2 (detalhes no M4). | Celular e relógio pareados no app **Galaxy Wearable**. Os dois apps instalados pela **mesma** execução do script (mesmo id e mesma chave). Reinstale os dois com `--install`. |
