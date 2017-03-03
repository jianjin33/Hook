package com.pecoo.hook;

import android.app.Application;

import com.pecoo.hook.utils.HookAmsUtil;

/**
 * Created by Administrator on 2017/3/3.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        HookAmsUtil amsUtil = new HookAmsUtil(ProxyActivity.class,this);

        amsUtil.hookAms();
        amsUtil.hookSystemHandler();

    }
}
