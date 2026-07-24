package com.hyunmin.manna;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * 광고 제거 = 일회성(비소모성) 상품.
 * Play Console > 수익 창출 > 인앱 상품 에서 아래 ID로 상품을 만들어야 한다.
 */
public class BillingManager implements PurchasesUpdatedListener {

    /** Play Console 인앱 상품 ID (콘솔에서 동일하게 등록할 것) */
    public static final String PRODUCT_ID = "remove_ads";

    public interface AdFreeListener { void onAdFreeChanged(boolean adFree); }

    private final Context ctx;
    private final AdFreeListener listener;
    private BillingClient client;
    private ProductDetails details;

    public BillingManager(Context context, AdFreeListener l) {
        this.ctx = context.getApplicationContext();
        this.listener = l;

        client = BillingClient.newBuilder(ctx)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .build();

        connect();
    }

    private void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    loadProduct();
                    queryExisting();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // 다음 사용 시 재연결 시도
            }
        });
    }

    private void loadProduct() {
        QueryProductDetailsParams.Product p = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(p))
                .build();

        client.queryProductDetailsAsync(params, (result, queryResult) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            if (queryResult == null) return;
            List<ProductDetails> list = queryResult.getProductDetailsList();
            if (list != null && !list.isEmpty()) {
                details = list.get(0);
            }
        });
    }

    /** 구매 시작 */
    public void launchPurchase(Activity activity) {
        if (client == null || !client.isReady()) {
            toast("스토어 연결 중이야. 잠시 후 다시 눌러줘.");
            connect();
            return;
        }
        if (details == null) {
            toast("상품 정보를 불러오는 중이야. 잠시 후 다시 시도해줘.");
            loadProduct();
            return;
        }

        BillingFlowParams.ProductDetailsParams pdp =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build();

        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(pdp))
                .build();

        client.launchBillingFlow(activity, flow);
    }

    /** 이미 산 것 확인 (재설치·기기 변경 시 복원) */
    public void queryExisting() {
        if (client == null || !client.isReady()) { connect(); return; }

        client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                (result, purchases) -> {
                    boolean owned = false;
                    if (purchases != null) {
                        for (Purchase pur : purchases) {
                            if (pur.getProducts().contains(PRODUCT_ID)
                                    && pur.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                owned = true;
                                acknowledgeIfNeeded(pur);
                            }
                        }
                    }
                    Prefs.setAdFree(ctx, owned);
                    if (listener != null) listener.onAdFreeChanged(owned);
                });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase pur : purchases) {
                if (pur.getProducts().contains(PRODUCT_ID)
                        && pur.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    acknowledgeIfNeeded(pur);
                    Prefs.setAdFree(ctx, true);
                    if (listener != null) listener.onAdFreeChanged(true);
                    toast("광고가 제거됐어. 고마워!");
                }
            }
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            // 사용자가 취소 — 아무것도 하지 않음
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryExisting();
        } else {
            toast("결제를 처리하지 못했어. 잠시 후 다시 시도해줘.");
        }
    }

    /** 3일 안에 승인하지 않으면 자동 환불되므로 반드시 호출 */
    private void acknowledgeIfNeeded(Purchase pur) {
        if (pur.isAcknowledged()) return;
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(pur.getPurchaseToken())
                .build();
        client.acknowledgePurchase(params, r -> {});
    }

    public void end() {
        if (client != null) {
            client.endConnection();
            client = null;
        }
    }

    private void toast(String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }
}
