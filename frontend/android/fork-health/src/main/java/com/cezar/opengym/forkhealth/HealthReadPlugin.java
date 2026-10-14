package com.cezar.opengym.forkhealth;

import android.content.Intent;
import android.os.Build;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.BodyFatRecord;
import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.WeightRecord;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlinx.coroutines.BuildersKt;

/**
 * FORK (specs/fork-health): reads weight, body fat and heart rate back from Health Connect, where
 * Samsung Health and the Galaxy Watch put them. Read-only, and off until the owner turns it on.
 * Upstream's HealthConnectPlugin keeps doing the writing; nothing here writes or deletes.
 *
 * What to keep and how to merge it is decided in JS (src/fork/health-read.js): this side returns
 * plain records with their origin package and client id, so the JS can drop what openGym wrote.
 *
 * Usage from JS (src/fork/health-read-sync.js):
 *   const HR = registerPlugin('HealthRead');
 *   await HR.status();                                   // { status, granted: { weight, bodyFat, heartRate, history } }
 *   await HR.requestPermissions({ kinds: ['weight', 'bodyFat', 'history'] });
 *   await HR.readSince({ kinds: ['weight', 'bodyFat'], since: 0 });  // { weights: [...], bodyFat: [...] }
 *   await HR.heartRate({ start, end });                  // { samples: [[t, bpm], ...] }
 */
@CapacitorPlugin(name = "HealthRead")
public class HealthReadPlugin extends Plugin {
    private static final String PROVIDER = "com.google.android.apps.healthdata";
    private static final String READ_WEIGHT = "android.permission.health.READ_WEIGHT";
    private static final String READ_BODY_FAT = "android.permission.health.READ_BODY_FAT";
    private static final String READ_HEART_RATE = "android.permission.health.READ_HEART_RATE";
    private static final String READ_HISTORY = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY;
    private static final int PAGE = 1000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Set<String> asked = Collections.emptySet();

    private static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    private String sdkStatus() {
        if (!supported()) return "unsupported";
        int s = HealthConnectClient.Companion.getSdkStatus(getContext(), PROVIDER);
        if (s == HealthConnectClient.SDK_AVAILABLE) return "available";
        if (s == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) return "update";
        return Build.VERSION.SDK_INT >= 34 ? "unsupported" : "missing";
    }

    private HealthConnectClient client() {
        return HealthConnectClient.Companion.getOrCreate(getContext(), PROVIDER);
    }

    private static String permissionOf(String kind) {
        switch (kind) {
            case "weight": return READ_WEIGHT;
            case "bodyFat": return READ_BODY_FAT;
            case "heartRate": return READ_HEART_RATE;
            case "history": return READ_HISTORY;
            default: return null;
        }
    }

    private static Set<String> permissionsOf(JSArray kinds) {
        Set<String> p = new HashSet<>();
        for (int i = 0; i < kinds.length(); i++) {
            String perm = permissionOf(kinds.optString(i, ""));
            if (perm != null) p.add(perm);
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private Set<String> granted() throws InterruptedException {
        PermissionController pc = client().getPermissionController();
        return (Set<String>) BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope, cont) -> pc.getGrantedPermissions(cont));
    }

    private static JSObject grantedObject(Set<String> g) {
        JSObject o = new JSObject();
        o.put("weight", g.contains(READ_WEIGHT));
        o.put("bodyFat", g.contains(READ_BODY_FAT));
        o.put("heartRate", g.contains(READ_HEART_RATE));
        o.put("history", g.contains(READ_HISTORY));
        return o;
    }

    @PluginMethod
    public void status(PluginCall call) {
        String status = sdkStatus();
        if (!"available".equals(status)) {
            JSObject r = new JSObject();
            r.put("status", status);
            r.put("granted", grantedObject(Collections.emptySet()));
            call.resolve(r);
            return;
        }
        worker.execute(() -> {
            try {
                JSObject r = new JSObject();
                r.put("status", status);
                r.put("granted", grantedObject(granted()));
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage(), "failed");
            }
        });
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (!"available".equals(sdkStatus())) {
            call.reject("Health Connect is not available", "unavailable");
            return;
        }
        asked = permissionsOf(call.getArray("kinds", new JSArray()));
        if (asked.isEmpty()) {
            call.reject("nothing to ask for", "failed");
            return;
        }
        ActivityResultContract<Set<String>, Set<String>> contract = PermissionController.createRequestPermissionResultContract(PROVIDER);
        Intent intent = contract.createIntent(getContext(), asked);
        startActivityForResult(call, intent, "permissionsResult");
    }

    @ActivityCallback
    private void permissionsResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        // The dialog answers only for this time; what stands now is asked of Health Connect.
        worker.execute(() -> {
            try {
                JSObject r = new JSObject();
                r.put("granted", grantedObject(granted()));
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage(), "failed");
            }
        });
    }

    @PluginMethod
    public void readSince(PluginCall call) {
        if (!"available".equals(sdkStatus())) {
            call.reject("Health Connect is not available", "unavailable");
            return;
        }
        JSArray kinds = call.getArray("kinds", new JSArray());
        long since = number(call, "since", 0);
        long until = number(call, "until", 0);
        worker.execute(() -> {
            try {
                Set<String> want = permissionsOf(kinds);
                JSObject r = new JSObject();
                JSArray weights = new JSArray();
                JSArray fat = new JSArray();
                TimeRangeFilter range = range(since, until);
                if (want.contains(READ_WEIGHT)) {
                    for (WeightRecord w : readAll(WeightRecord.class, range)) {
                        JSObject o = base(w.getTime(), w.getMetadata());
                        o.put("kg", w.getWeight().getKilograms());
                        weights.put(o);
                    }
                }
                if (want.contains(READ_BODY_FAT)) {
                    for (BodyFatRecord b : readAll(BodyFatRecord.class, range)) {
                        JSObject o = base(b.getTime(), b.getMetadata());
                        o.put("pct", b.getPercentage().getValue());
                        fat.put(o);
                    }
                }
                r.put("weights", weights);
                r.put("bodyFat", fat);
                call.resolve(r);
            } catch (SecurityException e) {
                call.reject(e.getMessage(), "permission");
            } catch (Exception e) {
                call.reject(e.getMessage(), "failed");
            }
        });
    }

    @PluginMethod
    public void heartRate(PluginCall call) {
        if (!"available".equals(sdkStatus())) {
            call.reject("Health Connect is not available", "unavailable");
            return;
        }
        long start = number(call, "start", 0);
        long end = number(call, "end", 0);
        if (start <= 0 || end <= start) {
            call.reject("start and end are required", "failed");
            return;
        }
        worker.execute(() -> {
            try {
                JSArray samples = new JSArray();
                for (HeartRateRecord rec : readAll(HeartRateRecord.class, range(start, end))) {
                    for (HeartRateRecord.Sample s : rec.getSamples()) {
                        long t = s.getTime().toEpochMilli();
                        if (t < start || t > end) continue;
                        JSArray pair = new JSArray();
                        pair.put(t);
                        pair.put(s.getBeatsPerMinute());
                        samples.put(pair);
                    }
                }
                JSObject r = new JSObject();
                r.put("samples", samples);
                call.resolve(r);
            } catch (SecurityException e) {
                call.reject(e.getMessage(), "permission");
            } catch (Exception e) {
                call.reject(e.getMessage(), "failed");
            }
        });
    }

    private static TimeRangeFilter range(long since, long until) {
        Instant from = Instant.ofEpochMilli(Math.max(0, since));
        Instant to = until > 0 ? Instant.ofEpochMilli(until) : Instant.now().plusSeconds(60);
        return TimeRangeFilter.Companion.between(from, to);
    }

    @SuppressWarnings("unchecked")
    private <T extends Record> List<T> readAll(Class<T> type, TimeRangeFilter range) throws InterruptedException {
        List<T> out = new java.util.ArrayList<>();
        String token = null;
        do {
            ReadRecordsRequest<T> req = new ReadRecordsRequest<>(
                    JvmClassMappingKt.getKotlinClass(type), range, Collections.emptySet(), true, PAGE, token);
            ReadRecordsResponse<T> res = (ReadRecordsResponse<T>) BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE, (scope, cont) -> client().readRecords(req, cont));
            out.addAll(res.getRecords());
            token = res.getPageToken();
        } while (token != null && !token.isEmpty());
        return out;
    }

    private static JSObject base(Instant time, Metadata meta) {
        JSObject o = new JSObject();
        o.put("t", time.toEpochMilli());
        o.put("origin", meta.getDataOrigin().getPackageName());
        String cid = meta.getClientRecordId();
        o.put("clientId", cid == null ? "" : cid);
        return o;
    }

    private static long number(PluginCall call, String name, long fallback) {
        Object v = call.getData().opt(name);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }
}
