package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.components.DropLinkCard
import com.example.ui.theme.LocalDropLinkColors
import com.example.ui.theme.SpaceGroteskFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    language: AppLanguage,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val isAr = language == AppLanguage.AR

    val workingFeatures = if (isAr) {
        listOf(
            "نقل الملفات عبر الشبكة المحلية (HTTP/1.1 + mDNS NSD) بسرعة عالية دون إنترنت.",
            "بوابة ويب مدمجة لاستقبال وإرسال الملفات عبر أي متصفح دون تثبيت تطبيقات.",
            "إنشاء ومسح رمز QR للربط الفوري عبر CameraX + ZXing.",
            "دعم Wi-Fi Direct (Peer Discovery & Group Host) للربط المباشر عند دعم الجهاز.",
            "دعم النقل عبر البلوتوث RFCOMM للأجهزة المقترنة.",
            "التحقق من سلامة الملفات عبر بصمة SHA-256 ودعم استكمال النقل عبر أجزاء."
        )
    } else {
        listOf(
            "High-speed local LAN file streaming (HTTP/1.1 + mDNS NSD) with zero internet needed.",
            "Embedded responsive Web Portal to send/receive from any browser without installing apps.",
            "Real QR code generation and CameraX + ZXing scanning for instant pairing.",
            "Wi-Fi Direct peer discovery & Group Host support on compatible hardware.",
            "Bluetooth RFCOMM streaming fallback for paired Android devices.",
            "SHA-256 integrity verification, queued batch transfers, and byte-range resume."
        )
    }

    val futureFeatures = if (isAr) {
        listOf(
            "النقل المشفر طرفًا لطرف عبر الإنترنت عند وجود الأجهزة في شبكات مختلفة.",
            "مزامنة المجلدات المجدولة بين الحاسوب والهاتف."
        )
    } else {
        listOf(
            "End-to-end encrypted Internet relay for transfers across different networks.",
            "Scheduled desktop-to-mobile folder synchronization."
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.aboutTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_droplink_hero),
                contentDescription = "DropLink Hero",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            DropLinkCard(elevated = true) {
                Text(
                    text = "DropLink",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SpaceGroteskFamily)
                )
                Text(
                    text = "${s.versionLabel}: 1.0.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = ext.cyan
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = s.aboutHeroDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropLinkCard {
                Text(s.supportedNowTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                workingFeatures.forEach { feat ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ext.success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(feat, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            DropLinkCard {
                Text(s.futureRoadmapTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                futureFeatures.forEach { feat ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ext.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(feat, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            DropLinkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ext.cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(s.securityGuaranteesTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = s.localOnlyDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
