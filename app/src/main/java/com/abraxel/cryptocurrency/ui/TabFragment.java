package com.abraxel.cryptocurrency.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.abraxel.cryptocurrency.MainActivity;
import com.abraxel.cryptocurrency.data.Market;

/**
 * Alt navigasyon sekmesi. Sekmeler gizlenip gösterilir (durum korunur); yalnızca görünen sekme piyasa verisini
 * dinler, canlı akış ve dönem verisi ister. Gizlenince istekleri bırakılır.
 */
public abstract class TabFragment extends Fragment implements Market.Listener {
    protected Market market;
    private boolean active;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        market = Market.get(context);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!isHidden()) activate();
    }

    @Override
    public void onStop() {
        super.onStop();
        deactivate();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) deactivate();
        else if (isResumed() || isStarted()) activate();
    }

    private boolean isStarted() {
        return getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
    }

    private void activate() {
        if (active || getView() == null) return;
        active = true;
        market.addListener(this);
        onActivated();
    }

    private void deactivate() {
        if (!active) return;
        active = false;
        market.removeListener(this);
        market.setStreams(this, null);
        market.setPeriod(this, null);
        onDeactivated();
    }

    protected boolean isActive() {
        return active;
    }

    protected MainActivity host() {
        return (MainActivity) requireActivity();
    }

    /** Sekme görünür oldu: ayarlar (para birimi, favoriler) değişmiş olabilir, tam yenileme yapılmalı. */
    protected abstract void onActivated();

    protected void onDeactivated() {
    }
}
