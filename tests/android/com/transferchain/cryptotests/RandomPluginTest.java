package com.transferchain.cryptotests;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.transferchain.random.Random;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class RandomPluginTest {
    private ExecutorService pool;
    private TestCordova cordova;

    @Before public void setUp() {
        pool = Executors.newFixedThreadPool(2);
        cordova = new TestCordova(pool);
    }

    @After public void tearDown() throws Exception {
        pool.shutdown();
        assertTrue("Native work did not finish", pool.awaitTermination(60, TimeUnit.SECONDS));
    }

    private PluginResult call(CordovaPlugin plugin, String action, JSONArray args) throws Exception {
        // A stream reuses one plugin instance across create/update/finalize.
        // Cordova initializes that instance once; repeated bootstrap asserts.
        if (plugin.cordova == null) plugin.privateInitialize("test", cordova, null, new CordovaPreferences());
        Capture callback = new Capture();
        assertTrue(plugin.execute(action, args, callback));
        assertTrue("Native callback timed out", callback.done.await(60, TimeUnit.SECONDS));
        assertEquals(1, callback.count);
        return callback.result;
    }

    private PluginResult ok(CordovaPlugin plugin, String action, JSONArray args) throws Exception {
        PluginResult result = call(plugin, action, args);
        assertEquals("Native operation rejected", PluginResult.Status.OK.ordinal(), result.getStatus());
        return result;
    }

    private void rejects(CordovaPlugin plugin, String action, JSONArray args, String code) throws Exception {
        PluginResult result = call(plugin, action, args);
        assertEquals(PluginResult.Status.ERROR.ordinal(), result.getStatus());
        JSONObject error = new JSONObject(result.getMessage());
        assertEquals(code, error.getString("code"));
        assertFalse(error.getString("message").isEmpty());
    }

    private static String binary(byte[] data) { return Base64.encodeToString(data, Base64.NO_WRAP); }
    private static byte[] data(PluginResult result) {
        assertEquals(PluginResult.MESSAGE_TYPE_ARRAYBUFFER, result.getMessageType());
        return Base64.decode(result.getMessage(), Base64.DEFAULT);
    }
    private static String hex(byte[] data) {
        StringBuilder out = new StringBuilder();
        for (byte value : data) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    private static byte[] unhex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    @Test public void randomBinaryAndBounds() throws Exception {
        JSONArray args = new JSONArray().put(new JSONObject().put("length", 32));
        byte[] first = data(ok(new Random(), "randomBytes", args));
        byte[] second = data(ok(new Random(), "randomBytes", args));
        assertEquals(32, first.length);
        assertFalse(Arrays.equals(first, second));
        for (Object length : new Object[] {0, -1, 1048577, 1.5, true, "32"}) {
            rejects(new Random(), "randomBytes",
                new JSONArray().put(new JSONObject().put("length", length)), "INVALID_ARGUMENT");
        }
    }

    private static class Capture extends CallbackContext {
        final CountDownLatch done = new CountDownLatch(1);
        volatile PluginResult result;
        volatile int count;
        Capture() { super("test", null); }
        @Override public synchronized void sendPluginResult(PluginResult value) {
            result = value;
            count++;
            done.countDown();
        }
    }

    private static class TestCordova implements CordovaInterface {
        final ExecutorService executor;
        TestCordova(ExecutorService executor) { this.executor = executor; }
        @Override public ExecutorService getThreadPool() { return executor; }
        @Override public Context getContext() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
        @Override public AppCompatActivity getActivity() { return null; }
        @Override public void startActivityForResult(CordovaPlugin plugin, Intent intent, int code) { throw new UnsupportedOperationException(); }
        @Override public void setActivityResultCallback(CordovaPlugin plugin) { throw new UnsupportedOperationException(); }
        @Override public Object onMessage(String id, Object data) { return null; }
        @Override public void requestPermission(CordovaPlugin plugin, int code, String permission) { throw new UnsupportedOperationException(); }
        @Override public void requestPermissions(CordovaPlugin plugin, int code, String[] permissions) { throw new UnsupportedOperationException(); }
        @Override public boolean hasPermission(String permission) { return true; }
    }
}
