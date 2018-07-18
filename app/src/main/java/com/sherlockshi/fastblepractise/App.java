package com.sherlockshi.fastblepractise;

import android.app.Application;

import com.blankj.utilcode.util.Utils;

/**
 * Author:      SherlockShi
 * Date:        2018-07-13 15:17
 * Description: TODO
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // AndroidUtilCode
        Utils.init(this);
    }
}
