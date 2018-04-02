package com.google.android.cameraview.demo;

import android.app.Application;
import android.support.annotation.Nullable;

public class ARDemoApp extends Application
{
    private static ARDemoApp INSTANCE;

    @Nullable
    private Model currentModel;

    public static ARDemoApp getInstance() {
        return INSTANCE;
    }

    @Nullable
    public Model getCurrentModel() {
        return currentModel;
    }

    public void setCurrentModel(@Nullable Model model) {
        currentModel = model;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        INSTANCE = this;
    }
}
