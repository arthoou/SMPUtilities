# SMPutilities

Um único mod de gerenciamento para servidores SMP e de roleplay: painel da staff, chat, moderação, vanish,
inventários (visualização, backups e perfis), nicknames de RP, TTS com balões de fala, limitador de portais,
controle de dimensões, comandos de qualidade de vida, brincadeiras da staff e sincronização de mods.

*English summary at the end.*

## Situação das versões

| Minecraft | Loader | Estado |
| --- | --- | --- |
| 1.21.1 | NeoForge | **Implementado** — compila no CI (`SMPutilities-1.21.1-neoforge.jar`) |
| demais (1.18.2 – 1.21.11) | Forge / Fabric / NeoForge | Ainda não — veja [Multi-loader e multi-versão](#multi-loader-e-multi-versão) |

Os projetos de base (TPA, QSMP Chat Format, DUSMP, Inv Profile, InvView, Inventory Rollback Plus, DuTTS e
Bubbles4All) são todos NeoForge 1.21.1 (o IRP também 1.21.11), por isso esse foi o primeiro alvo.

## Instalação

* **Servidor:** coloque o jar em `mods/`. Nada mais é obrigatório.
* **Clientes:** opcional. Sem o mod no cliente, o jogador entra normalmente e recebe chat, mensagens,
  menus de baú, vanish, nicknames no chat/TAB, sons de menção, etc. O mod no cliente é necessário para o
  painel da staff, nicknames acima da cabeça, balões de fala, TTS e sincronização de mods.
* **Integrações opcionais** (o mod funciona sem elas): Simple Voice Chat (voice mute), Curios e Cosmetic
  Armor (inventários/backups), LuckPerms ou qualquer plugin da Permission API do NeoForge, Aether (nomes de
  dimensão/portal).

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
API do NeoForge: o LuckPerms (ou outro) decide quando instalado; sem ele valem os níveis de OP padrão,
ajustáveis em `permissions.levelOverrides`, mais concessões por jogador em `permissions.grants`. Toda ação
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
* Vanish: a contagem de jogadores na lista de servidores não é alterada.
* Limite de portais: portais de destino gerados automaticamente pelo jogo não são contados; portais de
  outros mods só são limitados se o mod disparar o `PortalSpawnEvent` do NeoForge (o bloqueio de
  dimensão funciona para qualquer dimensão).
* Curios/Cosmetic Armor: edição apenas com o jogador online.
* Sincronização de mods: um jar com o mesmo nome e preso pelo Windows é deixado em
  `config/smputilities/modsync-client/pending/` para substituir manualmente.
* **Ainda não testado em jogo** — compila no CI, mas não houve teste de execução neste ambiente
  (sem acesso aos repositórios do Minecraft). Teste num servidor de testes antes de usar em produção.

## Multi-loader e multi-versão

A estrutura já separa o que independe do jogo:

```
core/                    lógica pura em Java 17 (config, permissões, chat, punições, auditoria, portais,
                         dimensões, teleporte, mod sync) — compilada dentro de cada jar, com testes
tts-bridge/              aplicativo auxiliar de TTS (jar embutido)
platforms/neoforge-1.21.1/   código do jogo; eventos do loader isolados em platform/
tools/lang/              traduções (en_us, pt_br) → tools/genlang.py
```

O que **não** está pronto: os jars de Forge, Fabric e das outras versões. Entre 1.18.2 e 1.21.11 mudam
registro, rede (payloads em 1.20.5), componentes de item (1.20.5), renderização de GUI e de mundo (1.21.2,
1.21.6), salvamento de entidades (1.21.6+), e o NeoForge só existe a partir da 1.20.1 (de 1.18.2 a 1.19.4
os alvos possíveis são Forge e Fabric). Cada alvo novo precisa do
seu `platforms/<loader>-<versão>` com a camada do jogo adaptada; o CI (`.github/workflows/build.yml`) já
gera um jar por entrada da matriz. A ordem sugerida: Fabric 1.21.1 (mesmo código de jogo, só eventos
diferentes), depois 1.20.1 Forge/NeoForge, depois 1.21.11.

## Compilar

```sh
./gradlew build                      # todos os alvos; jars em dist/
./gradlew -Psu.coreOnly=true :core:test   # só a lógica pura, sem baixar o Minecraft
```

Requer JDK 21. Ao editar traduções: `python3 tools/genlang.py tools/lang platforms/neoforge-1.21.1/src/main/resources/assets/smputilities/lang`.

---

### English summary

SMPutilities is one mod for SMP/roleplay servers. NeoForge 1.21.1 is implemented (staff panel GUI with
eight categories, vanilla-by-default chat with an optional QSMP format, mentions, mutes, spam protection,
bans synced with the vanilla ban list, server-side vanish, live and offline inventory editing, automatic
and manual backups with safe restore, Inv Profile's Quick Change plus crash-safe named profiles, RP
nicknames, TTS with speech bubbles, portal limits, dimension access control, TPA-style QoL commands,
harmless staff pranks, opt-in mod sync with SHA-512 verification, NeoForge Permission API nodes and an
audit log). It imports data from the standalone mods it replaces. Other loaders and versions are not built
yet; see the section above. It compiles in CI but has not been run in game yet.
