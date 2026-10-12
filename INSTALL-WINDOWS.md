# Instalação no Windows — SAM TV OFICIAL

Guia para compilar e instalar o app a partir de um PC com Windows 10/11.

## 1. Instalar o Android Studio

1. Baixe em <https://developer.android.com/studio> e instale com as opções padrão (inclui o Android SDK e um JDK embutido).
2. Na primeira abertura, escolha **Standard** no assistente; ele baixa o SDK, as Build-Tools e o emulador.
3. Em *Settings → Languages & Frameworks → Android SDK*, confirme que **Android API 36** está instalado (aba *SDK Platforms*). Se não estiver, marque e aplique.

> Não é preciso instalar Java separadamente: o Gradle usa o JDK do Android Studio (*Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK* = *jbr-17* ou superior).

## 2. Abrir o projeto

1. Extraia o zip, por exemplo em `C:\Projetos\SamTvOficial` (evite caminhos muito longos ou com acentos).
2. Android Studio → **Open** → selecione a pasta `SamTvOficial`.
3. Aguarde o **Gradle Sync** terminar (barra inferior). Na primeira vez pode levar alguns minutos.
4. Se aparecer aviso de atualização de versão do AGP/Kotlin, você pode aceitar — as versões do projeto ficam centralizadas em `gradle\libs.versions.toml`.

## 3. Rodar no celular

1. No celular: *Configurações → Sobre o telefone* → toque 7 vezes em **Número da versão** para liberar as *Opções do desenvolvedor*.
2. Em *Opções do desenvolvedor*, ative **Depuração USB** (ou **Depuração por Wi-Fi** no Android 11+).
3. Conecte o cabo e aceite a autorização no celular.
4. No Android Studio, escolha o aparelho na lista de dispositivos e clique em ▶ **Run 'app'**.

## 4. Gerar o APK pelo terminal

No terminal do Android Studio (aba *Terminal*, PowerShell), na pasta do projeto:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Arquivos gerados:

- `app\build\outputs\apk\debug\app-debug.apk`
- `app\build\outputs\apk\release\app-release-unsigned.apk`

Instalar via cabo (o `adb` fica em `%LOCALAPPDATA%\Android\Sdk\platform-tools`):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

## 5. Testes instrumentados (opcional)

Com o celular conectado ou um emulador aberto (*Device Manager → Create Device*):

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

O relatório fica em `app\build\reports\androidTests\connected\index.html`. Esses testes usam um servidor Xtream **simulado** dentro do próprio teste — nenhuma conta real é necessária.

## Problemas comuns

| Mensagem | Solução |
|---|---|
| `SDK location not found` | Abra o projeto pelo Android Studio uma vez (ele cria `local.properties`) ou crie o arquivo com `sdk.dir=C\:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk`. |
| `Unsupported Java` / `requires Java 17` | Ajuste *Gradle JDK* para jbr-17 ou 21 (ver passo 1). |
| `Failed to find target android-36` | Instale a *Android API 36* no SDK Manager. |
| Download de dependências falha | Verifique proxy/antivírus corporativo; o Gradle precisa acessar `dl.google.com` e `repo.maven.apache.org`. |
| Celular não aparece | Troque o cabo/porta, reative a Depuração USB e aceite a chave RSA no aparelho. Em alguns fabricantes é preciso o driver USB do fabricante. |
| "App não instalado" | Desinstale uma versão anterior assinada com outra chave, ou use o APK de debug. |
