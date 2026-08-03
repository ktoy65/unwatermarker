package com.unwatermarker;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public class ModuleMain extends XposedModule {

    private static final String PKG_MEITUAN = "com.sankuai.meituan.dispatch.crowdsource";
    private static final String PKG_DADA = "com.dada.mobile.android";

    private static final String CLS_MEITUAN_WATERMARK = "com.meituan.banma.base.common.ui.view.WaterMarkView";
    private static final String CLS_MEITUAN_WAYBILL_LIST_MODEL = "com.meituan.banma.waybill.list.model.j";
    private static final String CLS_MEITUAN_ADDRESS_UTIL = "com.meituan.banma.waybill.util.a";
    private static final String CLS_MEITUAN_ADDRESS_FORMAT = "com.meituan.banma.waybill.utils.k";
    private static final String CLS_DADA_WATERMARK_UTIL = "com.dada.mobile.delivery.utils.WaterMarkPageUtil";

    private static final String KILL_JS = """
            (function(){
            var kill=function(){
            try{var el=document.getElementById('wm_div_id');
            if(el){
            el.style.display='none';
            el.style.visibility='hidden';
            el.style.backgroundImage='none';
            if(el.parentNode){el.parentNode.removeChild(el);}
            }}catch(e){}};
            kill();
            try{new MutationObserver(kill).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}
            setInterval(kill,800);
            })()
            """;

    private static final String REMOVE_JS = """
            (function(){
            var sels='[class*="watermark"],[id*="watermark"],[class*="water_mark"],[id*="water_mark"],[class*="water-mark"],[id*="water-mark"],[class*="wmMask"],[class*="wm_"]';
            var es=document.querySelectorAll(sels);
            for(var i=0;i<es.length;i++){var el=es[i];if(el.parentNode){el.parentNode.removeChild(el);}}
            })()
            """;

    private ClassLoader appClassLoader;

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        param.setSavedInstanceState(appClassLoader);
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        param.getOldHookHandles().forEach(h -> h.unhook());
        ClassLoader cl = (ClassLoader) param.getSavedInstanceState();
        if (cl == null) {
            return;
        }
        if (tryLoad(CLS_MEITUAN_WATERMARK, cl) != null) {
            hookMeituan(cl);
        } else if (tryLoad(CLS_DADA_WATERMARK_UTIL, cl) != null) {
            hookDada(cl);
        } else {
            hookH5Watermark();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        appClassLoader = param.getClassLoader();
        String pkg = param.getPackageName();
        try {
            if (PKG_MEITUAN.equals(pkg)) {
                hookMeituan(appClassLoader);
            } else if (PKG_DADA.equals(pkg)) {
                hookDada(appClassLoader);
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookMeituan(ClassLoader cl) {
        Class<?> wmv = tryLoad(CLS_MEITUAN_WATERMARK, cl);
        if (wmv != null) {
            hookVoidBySignature(wmv, "e", Activity.class);
            hookVoidBySignature(wmv, "a", Activity.class);
        }
        hookOfflineSeeOrders(cl);
        hookH5Watermark();
    }

    private void hookOfflineSeeOrders(ClassLoader cl) {
        Class<?> waybillListModel = tryLoad(CLS_MEITUAN_WAYBILL_LIST_MODEL, cl);
        if (waybillListModel != null) {
            hookByNameAndParamCount(waybillListModel, "b", 0, args -> false);
        }

        Class<?> addressUtil = tryLoad(CLS_MEITUAN_ADDRESS_UTIL, cl);
        if (addressUtil != null) {
            hookByNameAndParamCount(addressUtil, "a", 2, args -> args[1]);
        }

        Class<?> addressFormat = tryLoad(CLS_MEITUAN_ADDRESS_FORMAT, cl);
        if (addressFormat != null) {
            hookByNameAndParamCount(addressFormat, "a", 4, args -> {
                Object poi = args[1];
                Object door = args[2];
                Object address = args[3];

                if (hasText(poi)) {
                    return hasText(door)
                            ? poi + "（" + door + "）"
                            : poi.toString();
                }
                return address;
            });
        }
    }

    private void hookDada(ClassLoader cl) {
        Class<?> util = tryLoad(CLS_DADA_WATERMARK_UTIL, cl);
        if (util != null) {
            hookAllByName(util, "b");
            hookAllByName(util, "d");
            hookAllByName(util, "c");
            for (Class<?> inner : util.getDeclaredClasses()) {
                if (Drawable.class.isAssignableFrom(inner)) {
                    hookAllByName(inner, "draw");
                }
            }
        }
        hookH5Watermark();
    }

    private Class<?> tryLoad(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    private void hookVoidBySignature(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getDeclaredMethod(name, params);
            hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> null);
        } catch (Throwable ignored) {
        }
    }

    private void hookAllByName(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                try {
                    hook(m).setPriority(PRIORITY_HIGHEST).intercept(chain -> null);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void hookByNameAndParamCount(
            Class<?> clazz,
            String name,
            int paramCount,
            HookResultProvider resultProvider
    ) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != paramCount) {
                continue;
            }
            try {
                hook(method).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                    Object[] args = new Object[paramCount];
                    for (int i = 0; i < paramCount; i++) {
                        args[i] = chain.getArg(i);
                    }
                    return resultProvider.get(args);
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().isEmpty();
    }

    @FunctionalInterface
    private interface HookResultProvider {
        Object get(Object[] args);
    }

    private void hookH5Watermark() {
        try {
            Method onPageFinished = WebViewClient.class.getMethod("onPageFinished", WebView.class, String.class);
            hook(onPageFinished).intercept(chain -> {
                try {
                    Object wv = chain.getArg(0);
                    if (wv instanceof WebView) {
                        WebView webView = (WebView) wv;
                        webView.evaluateJavascript(KILL_JS, null);
                        webView.evaluateJavascript(REMOVE_JS, null);
                    }
                } catch (Throwable ignored) {
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {
        }
    }
}
