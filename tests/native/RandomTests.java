import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import com.transferchain.random.Random;

public final class RandomTests {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static int checks;

    private static CallbackContext call(CordovaPlugin plugin, String action, JSONArray args) throws Exception {
        plugin.cordova = () -> POOL;
        CallbackContext callback = new CallbackContext();
        if (!plugin.execute(action, args, callback)) throw new AssertionError("Unexpected action rejection");
        if (!callback.done.await(45, TimeUnit.SECONDS)) throw new AssertionError("Native call timeout");
        // The production guard is released in finally after the callback.
        POOL.submit(() -> {}).get();
        return callback;
    }

    private static Object success(CordovaPlugin plugin, String action, JSONArray args) throws Exception {
        CallbackContext callback = call(plugin, action, args);
        if (callback.failure != null) throw new AssertionError(callback.failure.toString());
        return callback.result instanceof PluginResult ? ((PluginResult) callback.result).value : callback.result;
    }

    private static void rejected(CordovaPlugin plugin, String action, JSONArray args, String code) throws Exception {
        CallbackContext callback = call(plugin, action, args);
        if (callback.result != null) throw new AssertionError("Rejected operation released output");
        if (!(callback.failure instanceof JSONObject) ||
                !((JSONObject) callback.failure).getString("code").equals(code) ||
                ((JSONObject) callback.failure).getString("message").isBlank()) {
            throw new AssertionError("Expected " + code + ", received " + callback.failure);
        }
        checks++;
    }

    private static String binary(byte[] value) { return Base64.getEncoder().encodeToString(value); }
    private static String hex(byte[] value) { return HexFormat.of().formatHex(value); }
    private static byte[] bytes(String value) { return HexFormat.of().parseHex(value); }
    private static void equal(Object actual, Object expected) {
        if (!actual.equals(expected)) throw new AssertionError("Vector mismatch: " + actual + " != " + expected);
        checks++;
    }

    @SuppressWarnings("unchecked")

    private static void random() throws Exception {
        byte[] a = (byte[]) success(new Random(), "randomBytes", new JSONArray().put(new JSONObject().put("length", 32)));
        byte[] b = (byte[]) success(new Random(), "randomBytes", new JSONArray().put(new JSONObject().put("length", 32)));
        equal(a.length, 32);
        equal(Arrays.equals(a, b), false);
        equal(Arrays.equals(a, new byte[32]), false);
        for (Object bad : new Object[] {0, -1, 1048577, 1.5, true, "32", JSONObject.NULL}) {
            rejected(new Random(), "randomBytes", new JSONArray().put(new JSONObject().put("length", bad)), "INVALID_ARGUMENT");
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            random();
            System.out.println("PASS: " + checks + " checks");
        } finally { POOL.shutdownNow(); }
    }
}
