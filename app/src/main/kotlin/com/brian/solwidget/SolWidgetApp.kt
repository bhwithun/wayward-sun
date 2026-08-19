package com.brian.solwidget

import android.app.Application
import com.brian.solwidget.work.RefreshScheduler

class SolWidgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.ensure(this)
    }
}
