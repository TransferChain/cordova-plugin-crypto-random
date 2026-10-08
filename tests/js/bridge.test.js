import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { it as test } from 'mocha'
import vm from 'node:vm'

function bridge(_slug, name, exec) {
  const filename = new URL(`../../www/${name}.js`, import.meta.url),
    module = { exports: {} }

  vm.runInNewContext(readFileSync(filename, 'utf8'), {
    module,
    ArrayBuffer,
    Uint8Array,
    require(id) {
      assert.equal(id, 'cordova/exec')
      return exec
    }
  })
  return module.exports
}

test('randomBytes forwards numeric length and returns a byte array', async () => {
  const native = new Uint8Array([0, 127, 128, 255]),
    random = bridge(
      'random',
      'Random',
      (success, _reject, service, action, args) => {
        assert.equal(service, 'Random')
        assert.equal(action, 'randomBytes')
        assert.equal(args[0].length, 4)
        success(native.buffer)
      }
    ),
    result = await random.randomBytes(4)

  assert.ok(result instanceof Uint8Array)
  assert.deepEqual(result, native)
})
