package com.example.voxara.phone

import android.app.Application
import android.content.Context
import com.example.voxara.phone.data.PhoneDb
import com.example.voxara.phone.data.PhoneStore
import com.example.voxara.phone.link.DataLayerGateway
import com.example.voxara.phone.sync.SyncCoordinator
import com.example.voxara.phone.sync.SyncWorker
import com.example.voxara.phone.tono.PhoneTono

/** The phone app's single instances (no DI framework: a handful of objects). */
object PhoneGraph {
    @Volatile private var ready = false
    lateinit var db: PhoneDb
        private set
    lateinit var store: PhoneStore
        private set
    lateinit var sync: SyncCoordinator
        private set
    lateinit var tono: PhoneTono
        private set

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            db = PhoneDb(app)
            store = PhoneStore(app)
            sync = SyncCoordinator(DataLayerGateway(app), store, db)
            tono = PhoneTono()
            ready = true
        }
    }
}

class VoxaraPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneGraph.init(this)
        SyncWorker.schedule(this)
    }
}
