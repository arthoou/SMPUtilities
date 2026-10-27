// Joins the smoke test server as a vanilla client and checks what it is sent:
// one join line only (SMPutilities' own, not also the vanilla one); chat and
// another player's join and quit lines, once each, in the vanilla format and
// then in the custom one (chat "smoke:custom"); the overworld spawn reads as
// the flat world it is; and in the Nether box
// SmokeTest builds (chat "smoke:nether"), buried ores read as netherrack while
// ores open to the air read as themselves.
// Usage: node bot.js <minecraft version> [port]   exit code 0 = passed, 1 = failed
//        node bot.js --check <minecraft version>  exit code 0 = this mineflayer knows the version, 2 = it does not
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const check = process.argv[2] === '--check'
const version = process.argv[check ? 3 : 2]
const port = Number(process.argv[3] || 25565)
const known = mineflayer.testedVersions.includes(version) || require('minecraft-data').supportedVersions.pc.includes(version)
if (check || !known) {
  if (!known) console.log(`mineflayer does not support ${version}`)
  process.exit(known ? 0 : 2)
}

const failures = []
let finished = false
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'SmokeBot', version, auth: 'offline' })
const lines = []
bot.on('message', (message) => {
  lines.push(message.toString().trim())
})

function finish () {
  if (finished) return
  finished = true
  if (failures.length === 0) {
    console.log('CLIENT SMOKE OK')
  } else {
    console.log('CLIENT SMOKE FAILED:\n - ' + failures.join('\n - '))
  }
  bot.quit()
  setTimeout(() => process.exit(failures.length === 0 ? 0 : 1), 2000)
}

function fail (message) {
  failures.push(message)
  finish()
}

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

/** Waits until the client has the block at pos (its chunk arrived). */
async function blockAt (pos, what) {
  for (let i = 0; i < 300; i++) {
    const block = bot.blockAt(pos)
    if (block) return block
    await sleep(200)
  }
  throw new Error(`no chunk for ${what} at ${pos}`)
}

function expect (block, names, what) {
  const name = block ? block.name : 'nothing'
  const ok = names.includes(name)
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what} at ${block && block.position}: ${name}${ok ? '' : ' (expected ' + names.join(' or ') + ')'}`)
  if (!ok) failures.push(`${what}: got ${name}, expected ${names.join(' or ')}`)
}

/** The lines matching select must be exactly the expected ones, in order. */
function expectLines (select, expected, what) {
  const got = lines.filter(select)
  const ok = JSON.stringify(got) === JSON.stringify(expected)
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}: ${JSON.stringify(got)}`)
  if (!ok) failures.push(`${what}: got ${JSON.stringify(got)}, expected ${JSON.stringify(expected)}`)
}

async function say (text) {
  bot.chat(text)
  await sleep(2000)
}

/** Another vanilla client joins, stays a moment and leaves. */
async function visitor (name) {
  const other = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version, auth: 'offline' })
  other.on('error', (error) => fail(`${name}: ${error && error.message}`))
  other.on('kicked', (reason) => fail(`${name} kicked: ${JSON.stringify(reason)}`))
  await Promise.race([new Promise(resolve => other.once('spawn', resolve)), sleep(60000)])
  await sleep(1500)
  const ended = new Promise(resolve => other.once('end', resolve))
  other.quit()
  await Promise.race([ended, sleep(10000)])
  await sleep(2000)
}

bot.on('kicked', (reason) => fail('kicked: ' + JSON.stringify(reason)))
bot.on('error', (error) => fail('error: ' + (error && error.stack || error)))
bot.on('end', (reason) => { if (!finished) fail('disconnected: ' + reason) })
setTimeout(() => fail('timed out'), 180000)

bot.once('spawn', async () => {
  try {
    const feet = bot.entity.position.floored()
    console.log(`spawned in ${bot.game.dimension} at ${feet} (protocol ${bot.protocolVersion})`)
    expect(await blockAt(feet.offset(0, -1, 0), 'ground'), ['grass_block'], 'flat world surface')
    expect(await blockAt(new Vec3(feet.x, bot.game.minY, feet.z), 'bottom'), ['bedrock'], 'flat world bottom')

    // The join lines arrive around the spawn; give a late one time to show up.
    await sleep(2000)
    expectLines(text => text.includes('SmokeBot joined the game'), ['SmokeBot joined the game'], 'own join line')

    // Vanilla format (the default): chat, and another player's join and quit lines.
    await say('smoke vanilla chat')
    expectLines(text => text.includes('smoke vanilla chat'), ['<SmokeBot> smoke vanilla chat'], 'vanilla chat line')
    await visitor('SmokeBot2')
    expectLines(text => text.includes('SmokeBot2 joined'), ['SmokeBot2 joined the game'], 'vanilla join line of another player')
    expectLines(text => text.includes('SmokeBot2 left'), ['SmokeBot2 left the game'], 'vanilla quit line of another player')

    // Custom (QSMP) format, as set by chat.customFormat.
    bot.chat('smoke:custom')
    await sleep(1000)
    await say('smoke custom chat')
    expectLines(text => text.includes('smoke custom chat'), ['SmokeBot » smoke custom chat'], 'custom chat line')
    await visitor('SmokeBot3')
    expectLines(text => text.includes('SmokeBot3') && /\[[+-]\]|joined the game|left the game/.test(text),
      ['[+] SmokeBot3', '[-] SmokeBot3'], 'custom join and quit lines (and no vanilla ones)')

    const changed = new Promise(resolve => bot.once('respawn', resolve))
    bot.chat('smoke:nether')
    await Promise.race([changed, sleep(30000)])
    for (let i = 0; i < 150 && !String(bot.game.dimension).includes('nether'); i++) await sleep(200)
    if (!String(bot.game.dimension).includes('nether')) throw new Error('not sent to the Nether (dimension ' + bot.game.dimension + ')')
    await blockAt(new Vec3(2, 100, 2), 'the Nether box')
    // Let a late chunk resend or block update arrive before reading.
    await sleep(3000)
    expect(bot.blockAt(new Vec3(2, 100, 2)), ['netherrack'], 'buried ancient debris')
    expect(bot.blockAt(new Vec3(4, 99, 4)), ['netherrack'], 'buried quartz ore')
    expect(bot.blockAt(new Vec3(1, 102, 1)), ['nether_gold_ore'], 'open gold ore')
    expect(bot.blockAt(new Vec3(4, 102, 4)), ['ancient_debris'], 'open ancient debris')
    expect(bot.blockAt(new Vec3(3, 101, 3)), ['netherrack'], 'netherrack')
    expect(bot.blockAt(new Vec3(3, 104, 3)), ['air'], 'room')
    finish()
  } catch (error) {
    fail(error && error.stack || String(error))
  }
})
