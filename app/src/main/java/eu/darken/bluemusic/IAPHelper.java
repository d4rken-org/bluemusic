package eu.darken.bluemusic;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import eu.darken.bluemusic.util.dagger.ApplicationScope;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import timber.log.Timber;

@ApplicationScope
public class IAPHelper implements PurchasesUpdatedListener, BillingClientStateListener {
    static final String SKU_UPGRADE = "upgrade.premium";
    private final BehaviorSubject<List<Upgrade>> upgradesPublisher = BehaviorSubject.createDefault(new ArrayList<>());
    private final BillingClient billingClient;

    public static class Upgrade {
        enum Type {
            PRO_VERSION, UNKNOWN
        }

        private final Purchase purchase;
        private final Type type;

        Upgrade(Purchase purchase) {
            this.purchase = purchase;
            if (purchase.getSku().endsWith(SKU_UPGRADE)) type = Type.PRO_VERSION;
            else type = Type.UNKNOWN;
        }

        public Type getType() {
            return type;
        }
    }

    @Inject
    public IAPHelper(Context context) {
        billingClient = BillingClient.newBuilder(context).setListener(this).build();
        billingClient.startConnection(this);
    }

    @Override
    public void onBillingSetupFinished(int responseCode) {
        Timber.d("onBillingSetupFinished(responseCode=%d)", responseCode);
        if (BillingClient.BillingResponse.OK == responseCode) {
            final Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
            Timber.d("queryPurchases(): code=%d, purchases=%s", purchasesResult.getResponseCode(), purchasesResult.getPurchasesList());
            onPurchasesUpdated(purchasesResult.getResponseCode(), purchasesResult.getPurchasesList());
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        Timber.d("onBillingServiceDisconnected()");
    }

    @Override
    public void onPurchasesUpdated(int responseCode, @Nullable List<Purchase> purchases) {
        Timber.d("onPurchasesUpdated(responseCode=%d, purchases=%s)", responseCode, purchases);
        if (purchases != null) {
            List<Upgrade> upgrades = new ArrayList<>();
            for (Purchase p : purchases) {
                upgrades.add(new Upgrade(p));
            }
            upgradesPublisher.onNext(upgrades);
        }
    }

    public Observable<Boolean> isProVersion() {
        return upgradesPublisher.map(upgrades -> {
            boolean proVersion = false;
            for (Upgrade upgrade : upgrades) {
                if (upgrade.getType().equals(Upgrade.Type.PRO_VERSION)) {
                    proVersion = true;
                    break;
                }
            }
            return proVersion;
        });
    }

    private BillingFlowParams buildSKUProUpgrade() {
        return BillingFlowParams.newBuilder()
                .setSku(SKU_UPGRADE)
                .setType(BillingClient.SkuType.INAPP)
                .build();
    }

    public void buyProVersion(Activity activity) {
        billingClient.launchBillingFlow(activity, buildSKUProUpgrade());
    }
}
