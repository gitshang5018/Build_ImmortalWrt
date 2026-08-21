package org.immortalwrt.manager

import android.app.Application
import org.immortalwrt.manager.data.api.UbusClient
import org.immortalwrt.manager.data.repository.PreferencesRepository
import org.immortalwrt.manager.data.repository.RouterRepository

class ImmortalWrtApp : Application() {

    lateinit var ubusClient: UbusClient private set
    lateinit var routerRepository: RouterRepository private set
    lateinit var preferencesRepository: PreferencesRepository private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        ubusClient = UbusClient()
        routerRepository = RouterRepository(ubusClient)
        preferencesRepository = PreferencesRepository(this)
    }

    companion object {
        lateinit var instance: ImmortalWrtApp private set
    }
}
