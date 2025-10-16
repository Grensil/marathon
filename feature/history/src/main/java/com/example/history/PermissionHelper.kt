package com.example.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord

/**
 * Health Connect 권한 요청을 위한 Contract
 */
class HealthConnectPermissionContract : ActivityResultContract<Set<String>, Set<String>>() {
    override fun createIntent(context: Context, input: Set<String>): Intent {
        return PermissionController.createRequestPermissionResultContract()
            .createIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> {
        return emptySet()
    }
}

/**
 * Health Connect 권한 요청 헬퍼
 */
@Composable
fun rememberHealthConnectPermissionLauncher(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        onPermissionResult(granted.containsAll(permissions))
    }

    return remember {
        {
            launcher.launch(permissions)
        }
    }
}
