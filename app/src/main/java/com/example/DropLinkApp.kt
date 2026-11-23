package com.example

import android.app.Application
import com.example.repository.TransferCoordinator

class DropLinkApp : Application() {
    lateinit var coordinator: TransferCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        coordinator = TransferCoordinator(this)
    }
}
