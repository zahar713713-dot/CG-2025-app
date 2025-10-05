package app.what.schedule

import android.app.Application
import app.what.foundation.services.AppLogger
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.crash.CrashHandler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class ScheduleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        CrashHandler.initialize(applicationContext)
        AppLogger.initialize(applicationContext)
        Auditor.info("core", "App started")

        startKoin {
            androidContext(this@ScheduleApp)
            modules(generalModule)
        }
    }
}

val generalModule = module {

}