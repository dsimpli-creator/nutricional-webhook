# Nutricional Webhook

App Android que envia os dados de saúde do celular para a conta da pessoa no
[Nutricional](https://nutri.virachef.com.br), sozinho, de hora em hora. Ele lê a **Conexão Saúde**
(Health Connect), onde o Samsung Health, o Google Fit, o Fitbit e outros apps guardam os dados.

É uma adaptação do **[HC Webhook](https://github.com/mcnaveen/health-connect-webhook)**, de
MC.Naveen, com tudo já decidido para o Nutricional: não há endereço para digitar, tipo de dado
para escolher nem intervalo para configurar.

## Como funciona

1. No Nutricional, em **⚙ Perfil e metas → Envio automático de saúde**, a pessoa toca em
   **Conectar este celular**. O app abre com o link pessoal dela e pede a confirmação.
2. O Android mostra uma tela só para liberar os dados (**Permitir tudo**).
3. O app envia os últimos 30 dias e, a partir daí, o que mudou a cada hora, mesmo fechado.

A tela inicial mostra se está tudo certo, tem um botão **Enviar agora** e avisa do que falta
(permissão, economia de bateria, versão nova), sempre com o botão que resolve.

## Instalar

Baixe o APK pelo Nutricional (mesma tela do passo 1) ou em
[Releases](https://github.com/dsimpli-creator/nutricional-webhook/releases). O app não está na
Play Store: o Android pede para permitir a instalação pelo navegador.

Requisitos: Android 9 ou mais novo, com a Conexão Saúde (no Android 14 em diante ela já vem no
sistema).

Este app só fala com `nutri.virachef.com.br`. Para usar com outro servidor, troque o endereço em
`app/src/main/java/com/hcwebhook/app/nutricional/NutricionalLink.kt` e compile a sua versão.

## Modificações em relação ao HC Webhook

Este repositório parte do HC Webhook **v1.9.32** (commit `88fa8b0`). Modificado em **16/11/2026**:

- **Identidade**: nome Nutricional Webhook, pacote `br.com.virachef.nutricional.webhook`, ícone e
  cores do Nutricional. O código continua no pacote Kotlin `com.hcwebhook.app`.
- **Uma tela só**, em português (`nutricional/NutricionalApp.kt`), no lugar das telas de início,
  dados, webhooks, agendamento, servidor local, notificações e ajustes.
- **Conexão por um toque**: o site abre o app por `nutricional://conectar?u=…`. O link só é
  aceito se for o endereço pessoal do Nutricional (`nutricional/NutricionalLink.kt`) e depois de
  confirmado na tela. Também dá para colar o link copiado.
- **Configuração automática** (`nutricional/NutricionalSetup.kt`): um destino só, todos os tipos de
  dado que o Nutricional lê, envio a cada 60 minutos, histórico de 30 dias no primeiro envio.
- **Dados de ciclo menstrual e de vida íntima não são pedidos**: saíram do manifesto e da lista
  de tipos.
- **Envio mais tolerante**: tipos sem permissão são pulados em vez de derrubar o envio inteiro;
  um envio por vez; o histórico é reenviado até chegar uma vez.
- **Só fala com o Nutricional**: sem o serviço de opinião (FeedbackJar) e sem destinos em HTTP
  simples. Saíram do manifesto os widgets, o bloco de Configurações rápidas e o receptor de
  automação (Tasker/adb).
- **Aviso de versão nova**: o app pergunta ao Nutricional qual é a versão publicada
  (`nutricional/UpdateChecker.kt`) e oferece o download.
- **Repositório**: saíram os fluxos de publicação, a ficha de loja, os modelos de issue e as
  capturas de tela do projeto original; entrou `.github/workflows/build.yml`.

O histórico do git mostra cada mudança linha a linha. As pastas `docs/` e `server/` são do
projeto original e descrevem o formato dos dados enviados, que não mudou.

## Compilar

```bash
./gradlew :app:testFossDebugUnitTest   # testes
./gradlew :app:assembleFossDebug       # APK de teste (instala ao lado do app normal)
```

O APK publicado é gerado pelo GitHub Actions (`.github/workflows/build.yml`) a cada tag `vX.Y.Z`,
assinado com uma chave que fica só nos segredos do repositório. A versão fica em
`app/build.gradle.kts`.

## Privacidade

Os dados vão somente para a conta da pessoa no Nutricional, por HTTPS. Sem anúncios nem
rastreadores. Detalhes em [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Licença

[AGPL-3.0-only](https://www.gnu.org/licenses/agpl-3.0.html), a mesma do projeto original: veja
[LICENSE](LICENSE) e o adendo do autor original em [LICENSE.ADDENDUM](LICENSE.ADDENDUM), que
continuam valendo para esta adaptação. Por causa do adendo, este app **não é distribuído pela
Play Store nem pela App Store**.

HC Webhook © 2026 MC.Naveen. As modificações do Nutricional Webhook são publicadas sob a mesma
licença. Este projeto não tem vínculo com o autor original nem é endossado por ele. Se o app for
útil para você, considere apoiar o [projeto original](https://github.com/mcnaveen/health-connect-webhook).
