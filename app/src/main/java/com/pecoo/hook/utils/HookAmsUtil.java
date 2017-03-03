package com.pecoo.hook.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by Administrator.
 */

public class HookAmsUtil {
    private Class<?> proxyActivity;
    private Context context;

    /**
     * @param proxyActivity 代理Activity
     * @param context
     */
    public HookAmsUtil(Class<?> proxyActivity, Context context) {
        this.proxyActivity = proxyActivity;
        this.context = context;
    }

    public void hookAms() {
        try {
            //通过反射得到ActivityManagerNative类 和成员变量gDefault
            Class<?> forName = Class.forName("android.app.ActivityManagerNative");
            Field defaultField = forName.getDeclaredField("gDefault");
            defaultField.setAccessible(true);

            Object defaultValue = defaultField.get(null);
            //反射SingleTon
            Class<?> aClass = Class.forName("android.util.Singleton");
            Field instanceField = aClass.getDeclaredField("mInstance");
            instanceField.setAccessible(true);
            //得到源码中的iActivityManager
            Object iActivityManagerObject = instanceField.get(defaultValue);

            //使用动态代理 创建hook
            Class<?> iActivityManagerIntercept = Class.forName("android.app.IActivityManager");

            AmsInvocationHandler handler = new AmsInvocationHandler(iActivityManagerObject);

            Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{iActivityManagerIntercept}, handler);

            //替换
            instanceField.set(defaultValue, proxy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class AmsInvocationHandler implements InvocationHandler {

        private Object iActivityManagerObject;

        public AmsInvocationHandler(Object iActivityManagerObject) {
            this.iActivityManagerObject = iActivityManagerObject;
        }

        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            Log.i("INFO", "methodName:" + method.getName());
            if ("startActivity".contains(method.getName())) {
                Intent intent = null;
                int index = 0;
                for (int i = 0; i < objects.length; i++) {
                    if (objects[i] instanceof Intent) {
                        intent = (Intent) objects[i]; //原意图
                        index = i;
                        break;
                    }
                }
                //Intent intent = new Intent(context,ProxyActivity.class);
                Intent proxyIntent = new Intent();
                ComponentName componentName = new ComponentName(context, proxyActivity);
                proxyIntent.setComponent(componentName);
                //绑定通过系统的filter
                proxyIntent.putExtra("oldIntent", intent);
                //开始替换
                objects[index] = proxyIntent;
                return method.invoke(iActivityManagerObject, objects);
            }

            return method.invoke(iActivityManagerObject, objects);
        }
    }

    /**
    *当Intent通过时，启动activity通过源码 系统是通过handler进行启动，handler有个callback 当判断callback为空时才进行发消息，启动activity
    *这里可以通过在创建一个hook，自定义一个callback，把intent的activity替换为我们想要启动的
    */
    public void hookSystemHandler() {
        try {
            Class<?> forName = Class.forName("android.app.ActivityThread");
            Field currentActivityThread = forName.getDeclaredField("sCurrentActivityThread");
            currentActivityThread.setAccessible(true);
            Object activityThreadValue = currentActivityThread.get(null);//程序的入口
            Field handlerField = forName.getDeclaredField("mH");
            handlerField.setAccessible(true);
            Handler handlerObject = (Handler) handlerField.get(activityThreadValue);
            Field callbackField = Handler.class.getDeclaredField("mCallback");
            callbackField.setAccessible(true);  //防止私有
            callbackField.set(handlerObject,new ActivityThreadHandlerCallback(handlerObject));

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


    class ActivityThreadHandlerCallback implements  Handler.Callback{
        Handler handler;

        public ActivityThreadHandlerCallback(Handler handler) {
            this.handler = handler;
        }

        @Override
        public boolean handleMessage(Message message) {
            Log.i("INFO","message callback");
            //这里替换回之前的intent
            if (message.what == 100){
                Log.i("INFO","lauchActivity");
                handleLaunchActivity(message);
            }

            handler.handleMessage(message);

            return true;
        }

        private void handleLaunchActivity(Message message) {
            Object obj = message.obj;       //ActivityClientRecord
            try {
                //不能强转 framwork层
                Field intentField = obj.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);
                Intent proxyIntent = (Intent) intentField.get(obj);
                Intent realIntent = proxyIntent.getParcelableExtra("oldIntent");
                if (realIntent != null){
                    //代理意图替换成真实意图
                    proxyIntent.setComponent(realIntent.getComponent());
                }

            }catch (Exception e){
                e.printStackTrace();
            }


        }
    }

}







