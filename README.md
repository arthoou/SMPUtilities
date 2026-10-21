# SMPutilities

Um único mod de gerenciamento para servidores SMP e de roleplay: painel da staff, chat, moderação, vanish,
inventários (visualização, backups e perfis), nicknames de RP, TTS com balões de fala, limitador de portais,
controle de dimensões, comandos de qualidade de vida, brincadeiras da staff e sincronização de mods.

*English summary at the end.*

## Situação das versões

Um jar por versão do jogo e loader (`SMPutilities-<versão>-<loader>.jar`), todos do mesmo código:

| Minecraft | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.18.2 · 1.19.2 · 1.19.4 · 1.20.1 | ✔ | ✔ | — (não existe; no NeoForge 1.20.1 use o jar Forge 1.20.1) |
| 1.20.4 · 1.20.6 · 1.21.1 · 1.21.3 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 | ✔ | ✔ | ✔ |

Cada jar cobre a sua linha de versão (por exemplo o de 1.21.4 aceita só 1.21.4; o de 1.18.2, a 1.18.x).
O CI compila os 35 alvos e sobe um servidor dedicado de cada um (smoke test: carrega todas as classes
que os mixins alteram, roda comandos e desliga). Isso não substitui testar em jogo — veja
[Limitações conhecidas](#limitações-conhecidas).

## Instalação

* **Servidor:** coloque o jar do seu loader/versão em `mods/`. No Fabric é preciso o Fabric API.
* **Clientes:** opcional. Sem o mod no cliente, o jogador entra normalmente e recebe chat, mensagens,
  menus de baú, vanish, nicknames no chat/TAB, sons de menção, etc. O mod no cliente é necessário para o
  painel da staff, nicknames acima da cabeça, balões de fala, TTS e sincronização de mods. Cliente e
  servidor precisam da mesma versão do protocolo do SMPutilities, não do mesmo loader.
* **Integrações opcionais** (o mod funciona sem elas): Simple Voice Chat (voice mute), Curios e Cosmetic
  Armor (inventários/backups), LuckPerms (Permission API do Forge/NeoForge; no Fabric, pelo
  fabric-permissions-api que o LuckPerms traz), Aether (nomes de dimensão/portal).

## Arquivos

| Caminho | Conteúdo |
| --- | --- |
| `config/smputilities/server.json` | Toda a configuração do servidor (criada com valores padrão; `/su reload` recarrega) |
| `config/smputilities/client.json` | Preferências do cliente (balões, TTS, sincronização de mods, credenciais privadas) |
| `config/smputilities/modsync/` | Jars oferecidos pela sincronização de mods |
| `<mundo>/smputilities/players/<uuid>.json` | Dados por jogador (nickname, homes, notas, configurações) |
| `<mundo>/smputilities/backups/<uuid>.dat` | Backups de inventário |
| `<mundo>/smputilities/profiles/<uuid>.dat` | Perfis de inventário |
| `<mundo>/smputilities/punishments.json` | Histórico de punições |
| `<mundo>/smputilities/logs/audit-AAAA-MM.jsonl` | Registro administrativo (quem, o quê, em quem, quando) |

Tudo é indexado por UUID; trocar de nome ou de nickname nunca perde dados.

## Sistemas

### Painel da staff — `/su staff [jogador]`
Interface própria (fundo escuro translúcido, barra lateral, destaque verde, animações curtas), com oito
categorias: **Dashboard** (jogadores, TPS, MSPT, memória, uptime, bans/mutes ativos, controles rápidos do
chat), **Players** (busca por nome real ou nickname, online e offline; info, ações, notas/estatísticas e
histórico do jogador), **Chat**, **Inventories** (visualizar/editar, backups com restauração, perfis),
**Punishments** (bans, mutes, histórico, log da staff), **World** (dimensões e portais), **Utilities**
(brincadeiras, anúncios, warps, mod sync) e **Settings** (toda a config, editável ao vivo).
Ações críticas (banir, expulsar, restaurar, excluir) pedem confirmação. O cliente só pede; o servidor
confere a permissão de cada pedido. Há também uma tecla configurável ("Abrir painel da staff", sem tecla
padrão).

### Chat
O chat vanilla (`<Nome> mensagem`, assinado) é o padrão. Com `chat.customFormat` ligado vale o formato QSMP:
`Nick » Mensagem`, `[+] Nome`, `[-] Nome`, todos configuráveis (`&`-cores e `&#RRGGBB`, marcadores
`{name}`, `{realname}`, `{message}`, `{prefix}`). Também: menções com som enviado pelo servidor (cada
jogador ajusta com `/su mentions on|off|sound <id>|volume <0-1>`), destaque das menções, prefixos de staff e
admin, bloqueio do chat global (`/su chat lock|unlock`), limpar chat, cooldown, proteção contra spam
(taxa, repetição, CAPS), mute individual, entrada/saída desativáveis globalmente ou por jogador
(`/su silentjoin|silentquit`), `/msg` `/tell` `/w` `/reply` `/r` com MPs permitidas ou não durante o
bloqueio, `/ignore` e social spy para a staff.

### Moderação
`/su ban|mute|voicemute <jogador> [duração] [motivo]`, `/su unban|unmute|voiceunmute`, `/su kick`,
`/su warn`, `/su history`. Durações: `30m`, `2h`, `7d`, `1mo`, `perm`. Banimentos vão também para a ban list
vanilla (com expiração), então o bloqueio no login é o do próprio jogo e `/pardon` continua funcionando.
Staff não pode punir quem tem nível de OP maior.

### Vanish — `/vanish [on|off|<jogador>]`
Feito no servidor: um mixin no rastreamento de entidades impede que o jogador invisível seja enviado a
quem não tem `smputilities.staff.vanish.see` (sem posição, equipamento, sons ou partículas), remoção da
TAB, entrada/saída silenciosas (a staff vê com a etiqueta `[invisível]`), mobs não miram, coleta de itens
opcionalmente bloqueada, indicador na action bar e no HUD, persiste entre logins.

### Inventários
* `/su inventory <jogador>`, `/su enderchest <jogador>` (e `curios`/`cosmetic`): online é editado ao vivo;
  offline edita só as listas de itens do arquivo do jogador (posição, dimensão e o resto ficam intactos).
  Se o jogador entrar com a edição aberta, a cópia editada é aplicada nele e a janela fecha — sem duplicar
  nem perder itens. Antes de gravar uma edição offline é feito um backup.
* Backups automáticos (entrada, saída, morte, troca de dimensão, periódico) e manuais:
  `/su inventory backup <jogador>`, `/su inventory list <jogador>`, `/su inventory backups <jogador>`
  (navegador em GUI do Inventory Rollback Plus), `/su inventory restore <jogador> <backup> [full|inventory|ender]`.
  Toda restauração cria antes um backup do estado atual.
* **Perfis (Inv Profile integrado):** a Troca Rápida original (`/invprofile` ou `/su profile quick`) com o
  mesmo comportamento, mais perfis nomeados para personagens: `/su profile [create|switch|delete|list|nick]`.
  Guardam inventário, armaduras, Baú do Fim, XP, vida, fome, efeitos e slots de Curios/Cosmetic Armor
  (cada parte configurável, localização opcional). A troca grava o estado de saída antes de aplicar o novo e
  o arquivo do jogador lembra qual perfil ele carrega, então uma queda do servidor no meio da troca é
  corrigida no próximo login. Staff: `/su profile view|switchfor`, e pelo painel.

### Nicknames — `/su nick <nome>`
Aparecem no chat, mensagens de morte, TAB, `/msg` (configurável) e acima da cabeça (clientes com o mod).
Cores com `&`, unicidade opcional, nome real oculto para quem não é staff. `/su realnames` mostra os nomes
reais só para você; `/su whois`. Comandos aceitam nome real, nickname ou UUID, e guardam sempre o UUID.

### TTS e balões de fala — `/su tts`
O menu do DuTTS (gênero, variação, idioma). Com uma voz ativa, o que o jogador digita no chat vira fala: o
servidor envia o texto ao cliente dele, cujo *SMPutilities Voice Bridge* (um jar à parte, embutido no mod
e iniciado só quando necessário) fala num microfone virtual que o voice chat/Discord usa. Todos por perto
com o mod veem um balão de fala animado (estilo do Bubbles4All). Sem o bridge, só o TTS fica indisponível.

### Mundo
* `/su portal limit <nether|end|aether|id> <n|unlimited>`, `/su portal count|list|remove|clear`. Portais do
  Nether são contados ao acender; do End quando o último olho completaria a moldura. Portais destruídos são
  detectados (quebra de bloco e verificação periódica) e liberam a vaga. Permissão para ignorar o limite.
* `/su dimension <dimensão> on|off`, `/su dimension list`: bloqueia novas viagens (portais, `/tp`, entidades
  opcionalmente) sem descarregar nada nem tocar em dados/construções. Exceção para quem tem permissão.

### Qualidade de vida (do TPA)
`/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` (com botões clicáveis), `/home`, `/sethome`, `/delhome`, `/homes`,
`/setdefaulthome`, `/spawn`, `/setspawn`, `/back`, `/warp`, `/warps`, `/setwarp`, `/delwarp`, `/rtp`,
`/tpall`, `/tplogout`, `/hat`, `/sit`. Aquecimento com cancelamento ao mover/levar dano, cooldowns por
comando, limite de homes (config ou permissão numérica `smputilities.teleport.home.limit`), comandos
bloqueáveis por dimensão.

### Brincadeiras da staff — `/su troll <ação> <jogador>`
Explosão e raio só visuais, sons atrás do jogador, susto (guardião ancião + escuridão), náusea, escuridão,
flutuar (com queda lenta), lentidão, brilho, super pulo, girar, lançar (com queda lenta), mensagens/entradas/
saídas falsas, e os títulos do DUSMP (`/su announce action|death`). Nada quebra blocos, causa dano ou mexe em
inventário. Cada grupo pode ser desligado na config.

### Sincronização de mods (opcional, desligada por padrão)
Coloque jars em `config/smputilities/modsync/` e/ou liste downloads externos em `modSync.external`. Clientes
com o mod comparam (SHA-512) e veem uma tela com o que falta, de onde vem e o tamanho; só baixam se o
jogador aceitar. Downloads só por HTTPS de hosts permitidos ou pelo host embutido do servidor (que serve
apenas esses arquivos, só a jogadores conectados, com token), verificados antes de entrar em `mods/`,
nunca executados; remoções vão para uma pasta de backup. Credenciais de repositórios privados ficam só no
`client.json` do jogador. Avisa quando é preciso reiniciar e permite cancelar.

### Permissões e registros
Todos os nós (`smputilities.staff.panel`, `smputilities.staff.ban`, `smputilities.staff.inventory.edit`,
`smputilities.staff.vanish`, `smputilities.staff.voice.mute`, `smputilities.staff.realnames`,
`smputilities.chat.manage`, `smputilities.nickname.change`, `smputilities.portal.manage`,
`smputilities.dimension.manage`, … — lista completa em
[`Perm.java`](core/src/main/java/com/arthou/smputilities/core/perm/Perm.java)) são registrados na Permission
API do Forge/NeoForge; no Fabric são consultados pelo fabric-permissions-api (que o LuckPerms instala).
Com um plugin de permissões, ele decide; sem ele valem os níveis de OP padrão, ajustáveis em
`permissions.levelOverrides`, mais concessões por jogador em `permissions.grants`. Toda ação
administrativa vai para o log de auditoria (também visível no painel).

### Dados dos mods antigos
Na primeira inicialização o SMPutilities importa (só lê, nunca altera) os dados de Inv Profile (vira a
Troca Rápida), Inventory Rollback Plus (backups), TPA (homes, warps, spawn), DUSMP (silent join/quit, voz)
e DuTTS (vozes). Os comandos antigos `/invprofile`, `/view`, `/inventoryrollback` e `/dutts` continuam
funcionando.

## O que ficou de fora de propósito

* O `ApiServer`/`MCControlBootstrap` do DuTTS e do DUSMP **não** foi incorporado: ele abre uma API HTTP
  escondida no servidor, consulta um painel externo e pode executar comandos e apagar os arquivos do
  servidor. Não é algo que administradores esperam de um mod de moderação.
* A compatibilidade com o DuCurrency (custo de teleporte) do TPA, que depende de um mod externo que não
  está no repositório de bases.

## Limitações conhecidas

* Nickname acima da cabeça só para clientes com o mod (o nome da entidade é desenhado pelo cliente).
  Sugestões de comandos e a tela de "interações sociais" do jogo ainda mostram nomes de conta.
* Balões de fala: no Forge 1.21.9+ ainda não aparecem (o Forge não tem mais um evento de renderização de
  mundo e o pipeline novo de entidades não foi adaptado); no Forge 1.21.3–1.21.8 usam um mixin. Nos
  outros loaders/versões usam o evento de renderização do mundo.
* Vanish: a contagem de jogadores na lista de servidores não é alterada.
* Limite de portais: portais de destino gerados automaticamente pelo jogo não são contados; portais de
  outros mods só são limitados se o mod disparar o `PortalSpawnEvent` do Forge/NeoForge (no Fabric, só
  portais do Nether). O bloqueio de dimensão funciona para qualquer dimensão.
* Antes da 1.19.4 não existem Text Displays: o `/sit` usa uma nuvem de efeito invisível. Efeitos que não
  existem na versão (escuridão antes da 1.19) são simplesmente pulados nas brincadeiras.
* Backups e perfis guardam o formato da versão em que foram feitos e são atualizados pelos *data fixers*
  do jogo ao subir de versão; voltar para uma versão mais antiga não converte itens.
* Curios/Cosmetic Armor: edição apenas com o jogador online.
* Sincronização de mods: um jar com o mesmo nome e preso pelo Windows é deixado em
  `config/smputilities/modsync-client/pending/` para substituir manualmente.
* **Testado no CI, não em jogo**: os 35 jars compilam e o servidor de cada um sobe, aplica os mixins e roda
  comandos, mas a parte visual (painel, balões, HUD) e o jogo com jogadores reais precisam de teste num
  servidor de testes antes de produção.

## Multi-loader e multi-versão

```
core/                         lógica pura em Java 17 (config, permissões, chat, punições, auditoria,
                              portais, dimensões, teleporte, mod sync), com testes
mod/src/main/java             todo o código do jogo, compartilhado por todos os alvos
  mc/                         as chamadas que mudam entre versões (NBT, itens, textos, ids, efeitos,
                              teleporte, permissões de OP, bans, tab list...) ficam só aqui
  platform/                   Platform (o que cada loader fornece) e CommonEvents (todos os eventos)
  client/gui/                 Gfx e widgets próprios: o painel desenha igual de 1.18 (PoseStack) a
                              1.21.11 (GuiGraphics novo, eventos de input novos)
mod/src/{fabric,forge,neoforge}/   só a ligação com cada loader (eventos, rede, permissões, mixins)
mod/swaps.txt                 renomeações da Mojang aplicadas por versão (ResourceLocation→Identifier...)
buildSrc/                     pré-processador: //#if MC>=1.21.5 && FABRIC ... //#else ... //#endif
gradle/target.gradle          o build de um alvo (Architectury Loom, mapeamentos da Mojang)
versions/<versão>-<loader>/   versões exatas de Minecraft/loader/Fabric API (tools/gen_targets.py)
tts-bridge/                   aplicativo auxiliar de TTS (jar embutido)
tools/                        traduções (tools/lang → genlang.py), CI (erros, smoke test), sondas de API
```

As diferenças entre versões foram levantadas comparando as assinaturas reais das classes do jogo em
cada versão (`tools/probe`, workflow `probe`), não de memória.

## Compilar

```sh
./gradlew -Psu.targets=1.21.1-fabric build             # um alvo; jar em dist/
./gradlew -Psu.targets=1.20.1-forge,1.21.11-neoforge build
./gradlew -Psu.targets=all build                       # os 35 (cada um baixa o seu Minecraft)
./gradlew -Psu.coreOnly=true :core:test                # só a lógica pura, sem baixar o Minecraft
tools/smoke.sh 1.21.1-fabric                           # sobe o servidor do alvo e faz o smoke test
```

Requer JDK 21 (e JDK 17 para rodar servidores de 1.18.2 a 1.20.4). Traduções: edite `tools/lang/*.tsv`
e rode `python3 tools/genlang.py tools/lang mod/src/main/resources/assets/smputilities/lang`.

---

### English summary

SMPutilities is one mod for SMP/roleplay servers: staff panel GUI with eight categories, vanilla-by-default
chat with an optional QSMP format, mentions, mutes, spam protection, bans synced with the vanilla ban list,
server-side vanish, live and offline inventory editing, automatic and manual backups with safe restore,
Inv Profile's Quick Change plus crash-safe named profiles, RP nicknames, TTS with speech bubbles, portal
limits, dimension access control, TPA-style QoL commands, harmless staff pranks, opt-in mod sync with
SHA-512 verification, permission nodes for LuckPerms and an audit log. It imports data from the
standalone mods it replaces.

One shared code base builds 35 jars: Fabric and Forge for 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6,
1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10 and 1.21.11, and NeoForge from 1.20.4. Version differences
live in `mod/src/main/java/.../mc` and a small source preprocessor (`//#if MC>=1.21.5`); loader glue lives
in `mod/src/<loader>`. CI compiles every target and boots a dedicated server for each (smoke test); it has
not been played in game yet.
