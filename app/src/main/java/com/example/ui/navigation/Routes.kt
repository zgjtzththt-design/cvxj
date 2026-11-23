package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object DiscoverRoute
@Serializable object HistoryRoute
@Serializable object FilesRoute
@Serializable object SendFlowRoute
@Serializable object ReceiveRoute
@Serializable object QrScannerRoute
@Serializable data class TransferDetailRoute(val transferId: Long)
@Serializable object SettingsRoute
@Serializable object PrivacyRoute
@Serializable object AboutRoute
