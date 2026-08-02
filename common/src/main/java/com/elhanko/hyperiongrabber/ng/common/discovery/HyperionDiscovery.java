package com.elhanko.hyperiongrabber.ng.common.discovery;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.List;

/** Public discovery component shared by the TV and mobile setup flows. */
public final class HyperionDiscovery {
    public interface Listener extends HyperionDiscoveryController.Listener {
    }

    private final HyperionDiscoveryController controller;

    public HyperionDiscovery(@NonNull Context context, @NonNull Listener listener) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        controller = new HyperionDiscoveryController(
                new AndroidNsdDiscoveryBackend(context),
                listener,
                callback -> {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        callback.run();
                    } else {
                        mainHandler.post(callback);
                    }
                });
    }

    public boolean start() {
        return controller.start();
    }

    public boolean stop() {
        return controller.stop();
    }

    public boolean isRunning() {
        return controller.isRunning();
    }

    @NonNull
    public List<DiscoveredHyperionServer> getResults() {
        return controller.getResults();
    }

    public void close() {
        controller.close();
    }
}
