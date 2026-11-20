package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.RewardSubmission
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingGoldButton
import com.example.ui.components.GlowingPurpleButton
import com.example.ui.strings.LocalAppStrings
import com.example.ui.theme.*

private val MONTHLY_CATEGORIES = listOf(
    "₹200 UPI Reward" to "upi",
    "Normal Watch" to "watch",
    "Smartwatch" to "smartwatch",
    "Study Table" to "study_table",
    "Smartphone" to "smartphone"
)

@Composable
fun MonthlyRewardsScreen(
    announcements: List<Announcement>,
    submissions: List<RewardSubmission>,
    settings: AppSettings,
    onSubmitParticipation: (category: String, igHandle: String, screenshotName: String) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val primaryAnnouncement = announcements.firstOrNull()
    val instagramUrl = primaryAnnouncement?.instagramUrl?.ifBlank { settings.instagramUrl }
        ?: settings.instagramUrl

    var selectedCategory by remember { mutableStateOf("₹200 UPI Reward") }
    var igHandle by remember { mutableStateOf("") }
    var selectedScreenshotName by remember { mutableStateOf("") }

    // Zero-permission Android Photo Picker (Google Play policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                selectedScreenshotName = it.lastPathSegment ?: "screenshot_${System.currentTimeMillis()}.jpg"
            }
        }
    )

    val monthlySubs = remember(submissions) {
        submissions.filter { it.submissionType == "MONTHLY_PARTICIPATION" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.monthlyRewards, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "100% Free Skill & Community Participation — No coin deposits or paid entries.",
                style = MaterialTheme.typography.bodySmall,
                color = EmeraldSuccess
            )
        }

        if (primaryAnnouncement != null) {
            item {
                GlassCard(borderColor = GoldPrimary.copy(alpha = 0.6f)) {
                    Text(primaryAnnouncement.title, style = MaterialTheme.typography.titleLarge, color = GoldBright)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(primaryAnnouncement.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Closing: ${primaryAnnouncement.closingDate}", style = MaterialTheme.typography.labelMedium)
                        Text("Result: ${primaryAnnouncement.resultDate}", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Slots: ${primaryAnnouncement.availableSlots} • Verified Submissions: ${monthlySubs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (primaryAnnouncement.winnerNames.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = PurpleContainerDark, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                text = "Winners & Announcements: ${primaryAnnouncement.winnerNames}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Categories Preview + Instagram Link + Screenshot Upload
        item {
            GlassCard(borderColor = PurpleVibrant.copy(alpha = 0.55f)) {
                Text("Select Monthly Reward Category", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                MONTHLY_CATEGORIES.forEach { (catTitle, imgKey) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = selectedCategory == catTitle,
                            onClick = { selectedCategory = catTitle }
                        )
                        Box(modifier = Modifier.width(64.dp).height(46.dp)) {
                            RewardProductArtwork(imageKey = imgKey, title = catTitle, modifier = Modifier.fillMaxSize())
                        }
                        Text(catTitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Optional Instagram Follow: Follow StudyWin India on Instagram (never share your Instagram password). Note: Following or submitting a screenshot does not guarantee winning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(instagramUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_instagram_btn")
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Official Instagram Page")
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = igHandle,
                    onValueChange = { igHandle = it },
                    label = { Text("Your Instagram Username (@handle)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                GlowingPurpleButton(
                    text = if (selectedScreenshotName.isBlank()) "Pick Screenshot (Photo Picker)" else "Selected: $selectedScreenshotName",
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pick_screenshot_btn"
                )
                Spacer(modifier = Modifier.height(10.dp))
                GlowingGoldButton(
                    text = "Submit Free Participation",
                    onClick = {
                        onSubmitParticipation(selectedCategory, igHandle, selectedScreenshotName)
                        igHandle = ""
                        selectedScreenshotName = ""
                    },
                    enabled = igHandle.isNotBlank() && selectedScreenshotName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "submit_monthly_btn"
                )
            }
        }

        if (monthlySubs.isNotEmpty()) {
            item { Text("Your Monthly Submissions", style = MaterialTheme.typography.titleMedium) }
            items(monthlySubs, key = { it.id }) { sub ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.rewardTitle, style = MaterialTheme.typography.titleSmall)
                            Text("IG: ${sub.instagramHandle} • ${sub.screenshotName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val statusColor = when (sub.status) {
                            "APPROVED", "FULFILLED" -> EmeraldSuccess
                            "REJECTED" -> RubyError
                            else -> AmberWarning
                        }
                        Text(sub.status, style = MaterialTheme.typography.labelLarge, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
