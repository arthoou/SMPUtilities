// Joins the smoke test server as a vanilla client and checks what it is sent:
// one join line only (SMPutilities' own, not also the vanilla one), the
// overworld spawn reads as the flat world it is, and in the Nether box
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
const joinLines = []
bot.on('message', (message) => {
  const text = message.toString()
  if (text.includes('SmokeBot') && text.includes('joined the game')) joinLines.push(text)
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
    const joins = joinLines.length
    console.log(`${joins === 1 ? 'ok  ' : 'FAIL'} join lines: ${joins} ${JSON.stringify(joinLines)}`)
    if (joins !== 1) failures.push(`expected one join line, got ${joins}: ${JSON.stringify(joinLines)}`)

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
